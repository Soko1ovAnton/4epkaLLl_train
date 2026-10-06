package com.chepkall.gymlog.ui.calendar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chepkall.gymlog.R
import com.chepkall.gymlog.data.ExerciseSet
import com.chepkall.gymlog.data.Workout
import com.chepkall.gymlog.databinding.FragmentCalendarBinding
import com.chepkall.gymlog.ui.library.ExerciseEditorFragment
import com.chepkall.gymlog.ui.library.ExercisePickerSheet
import com.chepkall.gymlog.ui.workout.WorkoutSessionFragment
import com.chepkall.gymlog.util.Dates
import com.chepkall.gymlog.util.TextInputDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

class CalendarFragment : Fragment() {

    companion object { const val TAG = "CalendarFragment" }

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = requireNotNull(_binding)
    private val vm: CalendarViewModel by viewModels()

    private lateinit var daysAdapter: MonthGridAdapter
    private var selectedDay: Long = Dates.todayEpochDay()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        daysAdapter = MonthGridAdapter { day -> onDayClicked(day) }
        binding.monthGrid.layoutManager = androidx.recyclerview.widget.GridLayoutManager(requireContext(), 7)
        binding.monthGrid.adapter = daysAdapter
        attachMonthSwipe()

        binding.monthPrev.setOnClickListener { shiftMonthAnimated(-1) }
        binding.monthNext.setOnClickListener { shiftMonthAnimated(1) }
        binding.dayAction.setOnClickListener { onActionClicked() }
        binding.btnAddExercise.setOnClickListener { showPicker() }
        binding.btnApplyTemplate.setOnClickListener { showTemplatePicker() }

        // Выбор упражнения из каталога → сразу в тренировку этого дня
        setFragmentResultListener("pick_for_day") { _, bundle ->
            val exId = bundle.getLong("id", -1L)
            if (exId > 0) vm.addExerciseToDay(selectedDay, exId)
        }
        // «Создать новое» из пикера → полный экран редактирования; после сохранения
        // упражнение вернётся по "pick_for_day" и сразу добавится в день
        setFragmentResultListener("pick_for_day_create") { _, _ ->
            parentFragmentManager.commit {
                replace(R.id.container, ExerciseEditorFragment.newInstance(0L, "pick_for_day"), ExerciseEditorFragment.TAG + "new")
                addToBackStack(ExerciseEditorFragment.TAG + "new")
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vm.month.collect { grid ->
                        binding.monthTitle.text = Dates.monthYear(LocalDate.ofEpochDay(vm.currentMonthAnchor))
                        daysAdapter.submit(grid)
                    }
                }
                launch {
                    vm.dayUi.collect { renderDay(it) }
                }
            }
        }

        onDayClicked(selectedDay)
    }

    private fun onDayClicked(day: Long) {
        selectedDay = day
        daysAdapter.setSelected(day)
        vm.selectDay(day)
        binding.dayHeader.text = Dates.relativeLabel(day)
    }

    /** Список упражнений дня с подходами прямо под календарём. */
    private fun renderDay(ui: CalendarViewModel.DayUi) {
        binding.dayList.removeAllViews()
        val w = ui.workout

        // Строка под заголовком: «нет тренировки» либо время начала
        if (w == null) {
            binding.dayEmpty.visibility = View.VISIBLE
            binding.dayEmpty.text = "В этот день тренировки нет"
        } else {
            val firstSetAt = ui.sets.minOfOrNull { it.createdAt }
            val start = firstSetAt ?: w.startedAt
            if (start != null) {
                binding.dayEmpty.visibility = View.VISIBLE
                binding.dayEmpty.text = "Начало ${Dates.timeLabel(start)}"
            } else binding.dayEmpty.visibility = View.GONE
        }

        if (w == null) {
            binding.dayAction.visibility = View.VISIBLE
            binding.dayAction.text = "Начать"
            binding.btnAddExercise.visibility = View.GONE
            binding.btnApplyTemplate.visibility = View.VISIBLE
            return
        }

        // Строки упражнений с подходами
        if (ui.exercises.isEmpty()) {
            val hint = layoutInflater.inflate(R.layout.item_day_workout, binding.dayList, false)
            hint.findViewById<TextView>(R.id.w_name).text = "Упражнений пока нет"
            hint.findViewById<TextView>(R.id.w_sets).text = "нажмите, чтобы открыть тренировку"
            hint.findViewById<View>(R.id.w_more).setOnClickListener { showDayActions(w) }
            hint.setOnClickListener { openWorkout(w.id) }
            hint.setOnLongClickListener { showDayActions(w); true }
            binding.dayList.addView(hint)
        } else {
            for (we in ui.exercises) {
                val item = layoutInflater.inflate(R.layout.item_day_workout, binding.dayList, false)
                item.findViewById<TextView>(R.id.w_name).text = we.nameSnapshot
                val sets = ui.sets.filter { it.workoutExerciseId == we.id }.sortedBy { it.setIndex }
                item.findViewById<TextView>(R.id.w_sets).text = setsLabel(sets)
                item.findViewById<View>(R.id.w_more).setOnClickListener { showDayActions(w) }
                item.setOnClickListener { openWorkout(w.id) }
                item.setOnLongClickListener { showDayActions(w); true }
                binding.dayList.addView(item)
            }
        }

        // Кнопки: «Начать» → «Завершить»; «＋ Добавить упражнение» после старта; «Из шаблона» всегда
        binding.btnApplyTemplate.visibility = View.VISIBLE
        when (w.status) {
            Workout.STATUS_IN_PROGRESS -> {
                binding.dayAction.visibility = View.VISIBLE
                binding.dayAction.text = "Завершить"
                binding.btnAddExercise.visibility = View.VISIBLE
            }
            Workout.STATUS_PLANNED -> {
                binding.dayAction.visibility = View.VISIBLE
                binding.dayAction.text = "Начать"
                binding.btnAddExercise.visibility = View.VISIBLE
            }
            else -> { // DONE — «Завершить» скрыта, добавление остаётся
                binding.dayAction.visibility = View.GONE
                binding.btnAddExercise.visibility = View.VISIBLE
            }
        }
    }

    /** «N подходов: 60×8 · 60×8 · 65×6» */
    private fun setsLabel(sets: List<ExerciseSet>): String {
        if (sets.isEmpty()) return "подходов нет"
        val details = sets.joinToString(" · ") { s ->
            val w = if (s.weightKg % 1.0 == 0.0) s.weightKg.toInt().toString() else
                String.format(Locale.ROOT, "%.1f", s.weightKg)
            var label = "$w×${s.reps}"
            if (s.isDropset) label += " дс"
            if (s.isFailure) label += " ⚠"
            label
        }
        return "${plural(sets.size)}: $details"
    }

    private fun plural(n: Int): String = when {
        n % 10 == 1 && n % 100 != 11 -> "$n подход"
        n % 10 in 2..4 && n % 100 !in 12..14 -> "$n подхода"
        else -> "$n подходов"
    }

    /** Одна кнопка под списком: «Начать» → «Завершить». */
    private fun onActionClicked() {
        val w = vm.dayUi.value.workout
        if (w != null && w.status == Workout.STATUS_IN_PROGRESS) {
            showFinishTimePopup(w)
            return
        }
        startDayWithChain()
    }

    /** Стартуем день. Если где-то уже идёт тренировка — сначала завершаем её с выбором времени. */
    private fun startDayWithChain() {
        val w = vm.dayUi.value.workout
        val running = vm.running.value
        if (running != null && running.id != w?.id) {
            showFinishTimePopup(running, then = { vm.startWorkoutForDay(selectedDay) })
            return
        }
        vm.startWorkoutForDay(selectedDay)
    }

    /** Свайп по сетке месяцев: влево — следующий, вправо — предыдущий. */
    private fun attachMonthSwipe() {
        val density = resources.displayMetrics.density
        val minDistance = 80 * density
        val minVelocity = 1500 * density
        val detector = android.view.GestureDetector(
            requireContext(),
            object : android.view.GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: android.view.MotionEvent) = true
                override fun onFling(
                    e1: android.view.MotionEvent?,
                    e2: android.view.MotionEvent,
                    vx: Float,
                    vy: Float,
                ): Boolean {
                    e1 ?: return false
                    val dx = e2.x - e1.x
                    val dy = e2.y - e1.y
                    if (Math.abs(dx) > Math.abs(dy) &&
                        Math.abs(dx) > minDistance &&
                        Math.abs(vx) > minVelocity
                    ) {
                        shiftMonthAnimated(if (dx < 0) 1 else -1)
                        return true
                    }
                    return false
                }
            },
        )
        binding.monthGrid.addOnItemTouchListener(
            object : androidx.recyclerview.widget.RecyclerView.SimpleOnItemTouchListener() {
                override fun onInterceptTouchEvent(rv: androidx.recyclerview.widget.RecyclerView, e: android.view.MotionEvent): Boolean {
                    detector.onTouchEvent(e)
                    return false // не перехватываем: тапы по дням работают как раньше
                }
            },
        )
    }

    /** Перелистывание месяца с анимацией: сетка уезжает, новая въезжает. */
    private fun shiftMonthAnimated(dir: Int) {
        val grid = binding.monthGrid
        val w = grid.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        grid.animate().cancel()
        grid.animate()
            .translationX(-dir * w * 0.85f)
            .alpha(0f)
            .setDuration(130)
            .withEndAction {
                vm.shiftMonth(dir)
                grid.translationX = dir * w * 0.85f
                grid.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(150)
                    .start()
            }
            .start()
    }

    private fun showPicker() {
        ExercisePickerSheet.newInstance("pick_for_day")
            .show(parentFragmentManager, ExercisePickerSheet.TAG)
    }

    /** Выбор сохранённого шаблона → подстановка упражнений с планом в день. */
    private fun showTemplatePicker() {
        val list = vm.templates.value
        if (list.isEmpty()) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Нет шаблонов")
                .setMessage("Создай шаблон на вкладке «Шаблоны» или сохрани текущую тренировку через меню упражнения (⋯).")
                .setPositiveButton("ОК", null)
                .show()
            return
        }
        val names = list.map { it.name }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Из шаблона")
            .setItems(names) { _, which ->
                list.getOrNull(which)?.let { vm.applyTemplate(selectedDay, it.id) }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    /** Современный Material-пикер «время завершения» — по умолчанию текущее время, можно поменять. */
    private fun showFinishTimePopup(w: Workout, then: (() -> Unit)? = null) {
        val now = java.util.Calendar.getInstance()
        val picker = com.google.android.material.timepicker.MaterialTimePicker.Builder()
            .setTimeFormat(com.google.android.material.timepicker.TimeFormat.CLOCK_24H)
            .setHour(now.get(java.util.Calendar.HOUR_OF_DAY))
            .setMinute(now.get(java.util.Calendar.MINUTE))
            .setTitleText("Время завершения")
            .build()
        picker.addOnPositiveButtonClickListener {
            val cal = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, picker.hour)
                set(java.util.Calendar.MINUTE, picker.minute)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            var finished = cal.timeInMillis
            if (finished > System.currentTimeMillis()) finished = System.currentTimeMillis()
            vm.finishWorkout(w.id, finished) {
                // Всплывающие итоги: 1ПМ по упражнениям vs прошлый раз;
                // после закрытия — запуск новой тренировки, если был chain
                com.chepkall.gymlog.ui.workout.WorkoutSummary.show(
                    this@CalendarFragment, w.id,
                ) { then?.invoke() }
            }
        }
        picker.show(parentFragmentManager, "finish_time")
    }

    private fun showDayActions(w: Workout) {
        val hasExercises = vm.dayUi.value.exercises.isNotEmpty()
        val items = when {
            hasExercises && w.status == Workout.STATUS_PLANNED ->
                arrayOf("Начать тренировку", "Сохранить как шаблон", "Удалить тренировку")
            hasExercises ->
                arrayOf("Сохранить как шаблон", "Удалить тренировку")
            w.status == Workout.STATUS_PLANNED ->
                arrayOf("Начать тренировку", "Удалить тренировку")
            else -> arrayOf("Удалить тренировку")
        }
        MaterialAlertDialogBuilder(requireContext())
            .setItems(items) { _, which ->
                when (items[which]) {
                    "Начать тренировку" -> startDayWithChain()
                    "Сохранить как шаблон" -> promptSaveTemplate(w)
                    else -> confirmDelete(w)
                }
            }
            .show()
    }

    /** Сохранить тренировку как шаблон; если день собран из шаблона — предложить обновить. */
    private fun promptSaveTemplate(w: Workout) {
        val srcId = w.sourceTemplateId
        if (srcId > 0L) {
            lifecycleScope.launch {
                val srcName = vm.templateName(srcId)
                val options = if (srcName != null)
                    arrayOf("Обновить шаблон «$srcName»", "Сохранить как новый шаблон")
                else arrayOf("Сохранить как новый шаблон")
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Куда сохранить?")
                    .setItems(options) { _, which ->
                        when {
                            srcName != null && which == 0 -> askTemplateName(w, srcId, srcName ?: "")
                            else -> askTemplateName(w, 0L, "")
                        }
                    }
                    .show()
            }
        } else {
            askTemplateName(w, 0L, "")
        }
    }

    private fun askTemplateName(w: Workout, updateTemplateId: Long, preset: String) {
        TextInputDialog.prompt(this, "Название шаблона", "Например: Спина + бицепс", preset = preset) { name ->
            if (name.isNotBlank()) vm.saveWorkoutAsTemplate(w, name.trim(), updateTemplateId)
        }
    }

    private fun confirmDelete(w: Workout) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Удалить тренировку?")
            .setMessage("${Dates.dayMonth(w.date)}: все записи подходов за этот день будут удалены.")
            .setPositiveButton("Удалить") { _, _ -> vm.deleteWorkout(w) }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun openWorkout(id: Long?) {
        id ?: return
        val f = WorkoutSessionFragment.newInstance(id)
        parentFragmentManager.commit {
            replace(R.id.container, f, WorkoutSessionFragment.TAG + id)
            addToBackStack(WorkoutSessionFragment.TAG + id)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
