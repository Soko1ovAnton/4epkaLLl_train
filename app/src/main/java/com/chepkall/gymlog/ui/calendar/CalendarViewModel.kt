package com.chepkall.gymlog.ui.calendar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chepkall.gymlog.data.ExerciseSet
import com.chepkall.gymlog.data.Repo
import com.chepkall.gymlog.data.TemplateExercise
import com.chepkall.gymlog.data.Workout
import com.chepkall.gymlog.data.WorkoutExercise
import com.chepkall.gymlog.data.WorkoutTemplate
import com.chepkall.gymlog.util.Dates
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Ячейка месячной сетки — в дне максимум одна тренировка. */
data class DayCell(
    val epochDay: Long,
    val inMonth: Boolean,
    val isToday: Boolean,
    val hasWorkout: Boolean,
    val status: Int,   // статус той единственной тренировки (или STATUS_PLANNED, если её нет)
)

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repo.get(app)
    private val dao = repo.dao

    private val _monthAnchor = MutableStateFlow(LocalDate.now().withDayOfMonth(1))
    val currentMonthAnchor: Long get() = _monthAnchor.value.toEpochDay()

    private val _month = MutableStateFlow<List<DayCell>>(emptyList())
    val month: StateFlow<List<DayCell>> = _month

    private val _selectedDay = MutableStateFlow(Dates.todayEpochDay())
    val selectedDay: StateFlow<Long> = _selectedDay

    /** Состояние выбранного дня: единственная тренировка + её упражнения и подходы. */
    data class DayUi(
        val day: Long,
        val workout: Workout?,
        val exercises: List<WorkoutExercise>,
        val sets: List<ExerciseSet>,
    )

    private val _dayUi = MutableStateFlow(DayUi(Dates.todayEpochDay(), null, emptyList(), emptyList()))
    val dayUi: StateFlow<DayUi> = _dayUi

    /** Идущая тренировка (в любом дне) — для цепочки «сначала заверши предыдущую». */
    private val _runningWorkout = MutableStateFlow<Workout?>(null)
    val running: StateFlow<Workout?> = _runningWorkout

    /** Список сохранённых шаблонов (для выбора в дне). */
    private val _templates = MutableStateFlow<List<WorkoutTemplate>>(emptyList())
    val templates: StateFlow<List<WorkoutTemplate>> = _templates

    init {
        viewModelScope.launch {
            _monthAnchor.collect { recomputeMonth(it) }
        }
        viewModelScope.launch {
            dao.observeAllWorkouts().collect { refreshMarks() }
        }
        viewModelScope.launch {
            repo.cal.observeSetDates().collect { refreshMarks() }
        }
        viewModelScope.launch {
            dao.observeInProgress().collect { _runningWorkout.value = it }
        }
        viewModelScope.launch {
            dao.observeAllTemplates().collect { _templates.value = it }
        }
        viewModelScope.launch {
            _selectedDay.flatMapLatest { day ->
                dao.observeByDate(day).map { list -> list.firstOrNull() }.flatMapLatest { w ->
                    if (w == null) flowOf(DayUi(day, null, emptyList(), emptyList()))
                    else combine(
                        dao.observeForWorkout(w.id),
                        dao.observeSetsForWorkout(w.id),
                    ) { exs, sets -> DayUi(day, w, exs.sortedBy { it.orderPos }, sets) }
                }
            }.collect { _dayUi.value = it }
        }
    }

    private suspend fun refreshMarks() {
        recomputeMonth(_monthAnchor.value)
    }

    private suspend fun recomputeMonth(anchor: LocalDate) {
        val first = anchor.withDayOfMonth(1)
        val cells = Dates.monthGrid(first).map { d ->
            if (d == null) null else DayCell(
                epochDay = d.toEpochDay(),
                inMonth = d.monthValue == anchor.monthValue && d.year == anchor.year,
                isToday = d.toEpochDay() == Dates.todayEpochDay(),
                hasWorkout = false,
                status = Workout.STATUS_PLANNED,
            )
        }
        val dayCells = cells.filterNotNull()
        if (dayCells.isEmpty()) return
        val from = dayCells.first().epochDay
        val to = dayCells.last().epochDay
        val workouts = repo.cal.workoutsInRange(from, to)
        val byDay = workouts.groupBy { it.date }.mapValues { (_, ws) -> ws.sortedBy { it.orderPos }.first() }
        _month.value = cells.map { cell ->
            cell ?: return@map DayCell(0, false, false, false, Workout.STATUS_PLANNED).copy(inMonth = false)
            val w = byDay[cell.epochDay]
            if (w == null) cell else cell.copy(hasWorkout = true, status = w.status)
        }
    }

    fun shiftMonth(delta: Int) {
        _monthAnchor.value = _monthAnchor.value.plusMonths(delta.toLong())
    }

    fun selectDay(epochDay: Long) {
        _selectedDay.value = epochDay
    }

    /** «Начать»: создаёт и сразу запускает тренировку дня (плановую — активирует). */
    fun startWorkoutForDay(day: Long) {
        viewModelScope.launch {
            val existing = dao.workoutsOnDate(day).firstOrNull()
            when {
                existing == null -> dao.insert(
                    Workout(
                        date = day,
                        name = "Тренировка",
                        status = Workout.STATUS_IN_PROGRESS,
                        startedAt = System.currentTimeMillis(),
                        orderPos = dao.nextOrderPos(day) ?: 0,
                    ),
                )
                existing.status == Workout.STATUS_PLANNED -> dao.update(
                    existing.copy(
                        status = Workout.STATUS_IN_PROGRESS,
                        startedAt = System.currentTimeMillis(),
                    ),
                )
            }
            _selectedDay.value = day
        }
    }

    /** Завершить тренировку с указанным временем завершения.
     *  Пустая (без упражнений) завершённая тренировка нигде не сохраняется — просто исчезает. */
    fun finishWorkout(id: Long, finishedAt: Long, onDone: (() -> Unit)? = null) {
        viewModelScope.launch {
            val w = dao.workoutById(id) ?: run { onDone?.invoke(); return@launch }
            if (dao.exercisesForWorkout(id).isEmpty()) {
                dao.deleteWorkoutCascade(w)
            } else {
                dao.update(w.copy(status = Workout.STATUS_DONE, finishedAt = finishedAt))
            }
            onDone?.invoke()
        }
    }

    /** Добавить упражнение в тренировку дня (выбор из каталога прямо из календаря). */
    fun addExerciseToDay(day: Long, exerciseId: Long) {
        viewModelScope.launch {
            val w = dao.workoutsOnDate(day).firstOrNull() ?: return@launch
            val e = dao.exerciseById(exerciseId) ?: return@launch
            val pos = dao.nextExerciseOrderPos(w.id) ?: 0
            dao.insert(
                WorkoutExercise(
                    workoutId = w.id,
                    exerciseId = e.id,
                    nameSnapshot = e.name,
                    primaryMuscle = e.primaryMuscle,
                    secondaryMuscles = e.secondaryMuscles,
                    orderPos = pos,
                ),
            )
        }
    }

    fun deleteWorkout(w: Workout) {
        viewModelScope.launch { dao.deleteWorkoutCascade(w) }
    }

    /** Подтянуть шаблон в день: упражнения + план подходов (без слотов в БД). */
    fun applyTemplate(day: Long, templateId: Long) {
        viewModelScope.launch {
            val t = dao.templateById(templateId) ?: return@launch
            val te = dao.exercisesForTemplate(templateId)
            if (te.isEmpty()) return@launch
            var w = dao.workoutsOnDate(day).firstOrNull()
            if (w == null) {
                val id = dao.insert(
                    Workout(
                        date = day,
                        name = t.name,
                        status = Workout.STATUS_PLANNED,
                        orderPos = dao.nextOrderPos(day) ?: 0,
                        sourceTemplateId = t.id,
                    ),
                )
                w = dao.workoutById(id)
            } else if (w.sourceTemplateId == 0L) {
                val updated = w.copy(sourceTemplateId = t.id)
                dao.update(updated)
                w = updated
            }
            w ?: return@launch
            for (e in te) {
                val pos = dao.nextExerciseOrderPos(w.id) ?: 0
                dao.insert(
                    WorkoutExercise(
                        workoutId = w.id,
                        exerciseId = e.exerciseId,
                        nameSnapshot = e.nameSnapshot,
                        primaryMuscle = e.primaryMuscle,
                        secondaryMuscles = e.secondaryMuscles,
                        orderPos = pos,
                        planSets = e.planSets,
                        planRepsMin = e.planRepsMin,
                        planRepsMax = e.planRepsMax,
                    ),
                )
            }
            _selectedDay.value = day
        }
    }

    /**
     * Сохранить тренировку дня как шаблон.
     * [updateTemplateId] > 0 — обновить существующий шаблон, иначе создать новый.
     * План: число подходов = фактическим подходам (минимум 1), повторы = мин–макс по фактам.
     */
    fun saveWorkoutAsTemplate(w: Workout, name: String, updateTemplateId: Long) {
        viewModelScope.launch {
            val exs = dao.exercisesForWorkout(w.id)
            val templateId: Long
            if (updateTemplateId > 0L) {
                val t = dao.templateById(updateTemplateId) ?: return@launch
                dao.update(t.copy(name = name))
                dao.deleteExercisesForTemplate(t.id)
                templateId = t.id
            } else {
                templateId = dao.insert(WorkoutTemplate(name = name))
            }
            exs.sortedBy { it.orderPos }.forEachIndexed { idx, we ->
                val sets = dao.setsForWorkoutExercise(we.id)
                val min = sets.minOfOrNull { it.reps } ?: 0
                val max = sets.maxOfOrNull { it.reps } ?: 0
                dao.insertTemplateExercise(
                    TemplateExercise(
                        templateId = templateId,
                        exerciseId = we.exerciseId,
                        nameSnapshot = we.nameSnapshot,
                        primaryMuscle = we.primaryMuscle,
                        secondaryMuscles = we.secondaryMuscles,
                        orderPos = idx,
                        planSets = maxOf(sets.size, 1),
                        planRepsMin = min,
                        planRepsMax = max,
                    ),
                )
            }
            // Запомним источник, чтобы в следующий раз предлагать обновление
            if (w.sourceTemplateId == 0L) dao.update(w.copy(sourceTemplateId = templateId))
        }
    }

    suspend fun templateName(id: Long): String? = dao.templateById(id)?.name
}
