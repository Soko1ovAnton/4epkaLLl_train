package com.chepkall.gymlog.ui.workout

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chepkall.gymlog.data.Exercise
import com.chepkall.gymlog.data.ExerciseSet
import com.chepkall.gymlog.data.Repo
import com.chepkall.gymlog.data.Workout
import com.chepkall.gymlog.data.WorkoutExercise
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Строка упражнения на экране тренировки. */
data class ExRow(
    val we: WorkoutExercise,
    val sets: List<ExerciseSet>,
)

/** Данные последнего раза для упражнения. */
data class LastTime(
    val date: Long,
    val sets: List<ExerciseSet>,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WorkoutSessionViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repo.get(app)
    val gson = Gson()

    val workoutId = MutableStateFlow(0L)

    val workout: Flow<Workout?> = workoutId.flatMapLatest { id ->
        if (id == 0L) flowOf(null) else repo.dao.observeWorkoutById(id)
    }

    /** Время первого подхода тренировки (для шапки «Старт HH:MM»). */
    val firstSetTime: Flow<Long?> = workoutId.flatMapLatest { id ->
        if (id == 0L) kotlinx.coroutines.flow.flowOf(null)
        else repo.dao.observeSetsForWorkout(id).map { sets -> sets.minOfOrNull { it.createdAt } }
    }

    val rows: Flow<List<ExRow>> = workoutId.flatMapLatest { id ->
        if (id == 0L) flowOf(emptyList()) else {
            repo.dao.observeForWorkout(id).flatMapLatest { wes ->
                if (wes.isEmpty()) flowOf(emptyList()) else {
                    val flows = wes.map { we ->
                        repo.dao.observeForExercise(we.id).map { sets ->
                            we to sets
                        }
                    }
                    combine(flows) { arr -> arr.toList() }.map { list ->
                        list.map { (we, sets) -> ExRow(we, sets) }
                    }
                }
            }
        }
    }

    private val _finished = MutableStateFlow(false)
    val finished: StateFlow<Boolean> = _finished

    fun bind(id: Long) {
        workoutId.value = id
    }

    fun addExercise(e: Exercise) {
        val wid = workoutId.value
        if (wid == 0L) return
        viewModelScope.launch {
            val pos = repo.dao.nextExerciseOrderPos(wid) ?: 0
            repo.dao.insert(
                WorkoutExercise(
                    workoutId = wid,
                    exerciseId = e.id,
                    nameSnapshot = e.name,
                    primaryMuscle = e.primaryMuscle,
                    secondaryMuscles = e.secondaryMuscles,
                    orderPos = pos,
                ),
            )
        }
    }

    fun removeExercise(we: WorkoutExercise) {
        viewModelScope.launch { repo.dao.deleteWorkoutExerciseCascade(we) }
    }

    fun move(we: WorkoutExercise, delta: Int) {
        viewModelScope.launch {
            val list = repo.dao.exercisesForWorkout(we.workoutId).sortedBy { it.orderPos }
            val idx = list.indexOfFirst { it.id == we.id }
            val target = idx + delta
            if (target < 0 || target >= list.size) return@launch
            val other = list[target]
            repo.dao.update(we.copy(orderPos = other.orderPos))
            repo.dao.update(other.copy(orderPos = we.orderPos))
        }
    }

    /** Сохранить порядок упражнений после drag&drop. */
    fun persistOrder(list: List<WorkoutExercise>) {
        viewModelScope.launch {
            list.forEachIndexed { i, we ->
                if (we.orderPos != i) repo.dao.update(we.copy(orderPos = i))
            }
        }
    }

    fun addSet(
        we: WorkoutExercise,
        weight: Double,
        reps: Int,
        dropsetJson: String? = null,
        rpe: Double? = null,
        isFailure: Boolean = false,
        note: String = "",
    ) {
        viewModelScope.launch {
            val existing = repo.dao.setsForWorkoutExercise(we.id)
            val s = ExerciseSet(
                workoutExerciseId = we.id,
                workoutId = we.workoutId,
                exerciseId = we.exerciseId,
                date = workoutDate(we.workoutId),
                setIndex = existing.size,
                weightKg = weight,
                reps = reps,
                rpe = rpe,
                isDropset = !dropsetJson.isNullOrBlank(),
                dropset = dropsetJson?.takeIf { it.isNotBlank() },
                isFailure = isFailure,
                note = note,
            )
            repo.dao.insert(s)
        }
    }

    fun updateSet(s: ExerciseSet) {
        viewModelScope.launch { repo.dao.update(s) }
    }

    fun deleteSet(s: ExerciseSet) {
        viewModelScope.launch { repo.dao.deleteSet(s) }
    }

    private suspend fun workoutDate(workoutId: Long): Long =
        repo.dao.workoutById(workoutId)?.date ?: 0L

    fun setNote(we: WorkoutExercise, note: String) {
        viewModelScope.launch { repo.dao.update(we.copy(note = note)) }
    }

    /** Начать запланированную тренировку прямо с экрана сессии. */
    fun startPlanned() {
        viewModelScope.launch {
            val w = repo.dao.workoutById(workoutId.value) ?: return@launch
            repo.dao.inProgressWorkout()?.let { running ->
                if (running.id != w.id) {
                    daoAutoFinish(running)
                }
            }
            repo.dao.update(
                w.copy(status = Workout.STATUS_IN_PROGRESS, startedAt = System.currentTimeMillis()),
            )
        }
    }

    private suspend fun daoAutoFinish(running: Workout) {
        repo.dao.update(
            running.copy(status = Workout.STATUS_DONE, finishedAt = System.currentTimeMillis()),
        )
    }

    fun finishWorkout(onDone: (() -> Unit)? = null) {
        viewModelScope.launch {
            val w = repo.dao.workoutById(workoutId.value) ?: run { onDone?.invoke(); return@launch }
            repo.dao.update(
                w.copy(status = Workout.STATUS_DONE, finishedAt = System.currentTimeMillis()),
            )
            _finished.value = true
            onDone?.invoke()
        }
    }

    fun cancel() {
        _finished.value = true
    }

    suspend fun lastTimeFor(exerciseId: Long, beforeWorkoutId: Long): Pair<Long, List<ExerciseSet>>? {
        val current = repo.dao.workoutById(beforeWorkoutId) ?: return null
        val sets = repo.dao.recentForExerciseBefore(exerciseId, current.date, 40)
            .filter { it.workoutId != beforeWorkoutId }
        if (sets.isEmpty()) return null
        // Группируем по последней дате, где было это упражнение
        val lastDate = sets.maxOf { it.date }
        val lastSets = sets.filter { it.date == lastDate }
        return lastDate to lastSets
    }
}
