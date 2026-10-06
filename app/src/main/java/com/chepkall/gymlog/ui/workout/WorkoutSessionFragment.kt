package com.chepkall.gymlog.ui.workout

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chepkall.gymlog.R
import com.chepkall.gymlog.data.ExerciseSet
import com.chepkall.gymlog.data.Repo
import com.chepkall.gymlog.data.Workout
import com.chepkall.gymlog.databinding.FragmentWorkoutSessionBinding
import com.chepkall.gymlog.ui.library.ExerciseEditorFragment
import com.chepkall.gymlog.ui.library.ExercisePickerSheet
import com.chepkall.gymlog.util.Dates
import com.chepkall.gymlog.util.TextInputDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WorkoutSessionFragment : androidx.fragment.app.Fragment() {

    companion object {
        const val TAG = "WorkoutSession"
        private const val ARG_ID = "workout_id"

        fun newInstance(id: Long) = WorkoutSessionFragment().apply {
            arguments = Bundle().apply {
                putLong(ARG_ID, id)
            }
        }
    }

    private var _binding: FragmentWorkoutSessionBinding? = null
    private val binding get() = requireNotNull(_binding)
    val vm: WorkoutSessionViewModel by viewModels()
    private lateinit var adapter: ExerciseRowsAdapter
    private lateinit var touchHelper: androidx.recyclerview.widget.ItemTouchHelper
    private var latestWorkout: Workout? = null
    private var firstSetAt: Long? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWorkoutSessionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vm.bind(requireArguments().getLong(ARG_ID))

        adapter = ExerciseRowsAdapter(
            onAddSet = { row -> showAddSetDialog(row) },
            onMoveUp = { row -> vm.move(row.we, -1) },
            onMoveDown = { row -> vm.move(row.we, +1) },
            onEditSet = { s, row -> showEditSetDialog(s, row) },
            onNote = { row ->
                TextInputDialog.prompt(
                    this, "Заметка: ${row.we.nameSnapshot}", "Заметка к упражнению",
                    preset = row.we.note, multiline = true,
                ) { vm.setNote(row.we, it) }
            },
            onLongPress = { row -> showPrevWorkoutSummary(row) },
            dragStart = { vh -> touchHelper.startDrag(vh) },
        )
        binding.setsList.layoutManager = LinearLayoutManager(requireContext())
        binding.setsList.adapter = adapter
        binding.setsList.isNestedScrollingEnabled = false

        attachSwipeToDelete()

        binding.backBtn.setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        binding.btnAddExercise.setOnClickListener { showPicker() }
        binding.btnFinish.setOnClickListener { confirmFinish() }

        setFragmentResultListener(ExercisePickerSheet.TAG) { _, bundle ->
            val exId = bundle.getLong("id", -1L)
            if (exId > 0) {
                lifecycleScope.launch {
                    val e = Repo.get(requireContext()).dao.exerciseById(exId)
                    if (e != null) vm.addExercise(e)
                }
            }
        }
        // «Создать новое» из пикера → полный экран редактирования; после сохранения
        // упражнение вернётся по TAG и сразу добавится в тренировку
        setFragmentResultListener(ExercisePickerSheet.TAG + "_create") { _, _ ->
            parentFragmentManager.commit {
                replace(R.id.container, ExerciseEditorFragment.newInstance(0L, ExercisePickerSheet.TAG), ExerciseEditorFragment.TAG + "new")
                addToBackStack(ExerciseEditorFragment.TAG + "new")
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vm.workout.collect { w ->
                        if (w == null) return@collect
                        latestWorkout = w
                        renderWorkoutHeader(w)
                    }
                }
                launch {
                    // Время первого подхода: следим и перерисовываем шапку
                    vm.firstSetTime.collect { millis ->
                        firstSetAt = millis
                        latestWorkout?.let { renderWorkoutHeader(it) }
                    }
                }
                launch {
                    vm.rows.collect { list ->
                        adapter.submit(list)
                        val totalSets = list.sumOf { it.sets.size }
                        val totalVol = list.sumOf { row -> row.sets.sumOf { s: com.chepkall.gymlog.data.ExerciseSet -> s.volume() } }
                        binding.wsSummary.text = "Подходов: $totalSets · Тоннаж: ${fmt(totalVol)} кг"
                    }
                }
            }
        }
    }

    private fun renderWorkoutHeader(w: Workout) {
        binding.wsTitle.text = w.name
        binding.btnFinish.text = when (w.status) {
            Workout.STATUS_DONE -> "Показать итоги"
            Workout.STATUS_IN_PROGRESS -> "Завершить"
            else -> "Начать"
        }
        // Сверху — только дата и время начала (когда добавлен первый подход)
        binding.wsDate.text = Dates.dayMonthYear(w.date)
        binding.wsTimer.text = if (firstSetAt != null) "Начало ${Dates.timeLabel(firstSetAt!!)}" else ""
    }

    private fun fmt(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(java.util.Locale.ROOT, "%.1f", v)

    private fun showPicker() {
        ExercisePickerSheet.newInstance(ExercisePickerSheet.TAG)
            .show(parentFragmentManager, ExercisePickerSheet.TAG)
    }

    private fun confirmFinish() {
        lifecycleScope.launch {
            val w = Repo.get(requireContext()).dao.workoutById(vm.workoutId.value) ?: return@launch
            when (w.status) {
                Workout.STATUS_PLANNED -> vm.startPlanned()
                Workout.STATUS_IN_PROGRESS -> {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Завершить тренировку?")
                        .setMessage("Тренировка будет помечена завершённой и появится в статистике.")
                        .setPositiveButton("Завершить") { _, _ ->
                            vm.finishWorkout {
                                // Сразу показываем итоги: 1ПМ по упражнениям vs прошлый раз
                                WorkoutSummary.show(
                                    this@WorkoutSessionFragment,
                                    vm.workoutId.value,
                                ) { requireActivity().onBackPressedDispatcher.onBackPressed() }
                            }
                        }
                        .setNegativeButton("Продолжить", null)
                        .show()
                }
                else -> showFinishSummary()
            }
        }
    }

    private fun showFinishSummary() {
        WorkoutSummary.show(this, vm.workoutId.value) {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    /**
     * Жесты списка упражнений: свайп — удаление (красный фон с мусоркой,
     * подтверждение, отмена возвращает строку), ручка ≡ — drag&drop порядка.
     */
    private fun attachSwipeToDelete() {
        val trash = androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.ic_delete_white)
        val redBg = android.graphics.Paint().apply { color = android.graphics.Color.parseColor("#8C1D18") }

        val cb = object : androidx.recyclerview.widget.ItemTouchHelper.Callback() {
            override fun getMovementFlags(
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
            ): Int {
                val drag = androidx.recyclerview.widget.ItemTouchHelper.UP or
                    androidx.recyclerview.widget.ItemTouchHelper.DOWN
                val swipe = androidx.recyclerview.widget.ItemTouchHelper.LEFT or
                    androidx.recyclerview.widget.ItemTouchHelper.RIGHT
                return androidx.recyclerview.widget.ItemTouchHelper.Callback.makeMovementFlags(drag, swipe)
            }

            override fun isLongPressDragEnabled() = false // drag только за ручку ≡

            override fun onMove(
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean {
                val from = vh.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                adapter.move(from, to)
                return true
            }

            override fun onSelectedChanged(vh: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(vh, actionState)
                if (actionState == androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_IDLE) {
                    // Перетаскивание закончено — сохраняем порядок в БД
                    vm.persistOrder(adapter.snapshot().map { it.we })
                }
            }

            override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {
                val pos = vh.bindingAdapterPosition
                val row = adapter.get(pos)
                if (row == null) {
                    adapter.notifyItemChanged(pos)
                    return
                }
                var removed = false
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Убрать упражнение?")
                    .setMessage("${row.we.nameSnapshot} и все его подходы будут удалены.")
                    .setPositiveButton("Убрать") { _, _ ->
                        removed = true
                        vm.removeExercise(row.we)
                    }
                    .setNegativeButton("Отмена", null)
                    .setOnDismissListener {
                        // Отмена/тап мимо — вернуть строку на место
                        if (!removed) adapter.notifyItemChanged(pos)
                    }
                    .show()
            }

            override fun onChildDraw(
                c: android.graphics.Canvas,
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean,
            ) {
                if (actionState == androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_SWIPE) {
                    val v = vh.itemView
                    val frac = (Math.abs(dX) / v.width).coerceIn(0f, 1f)
                    val mid = v.top + v.height / 2f
                    val iconSize = (28 * resources.displayMetrics.density).toInt()
                    val half = iconSize / 2f
                    if (dX > 0) {
                        // фон слева, строка уезжает вправо
                        c.drawRect(0f, v.top.toFloat(), dX, v.bottom.toFloat(), redBg)
                        trash?.let {
                            it.setBounds(
                                ((dX - iconSize) / 2f).toInt(), (mid - half).toInt(),
                                ((dX + iconSize) / 2f).toInt(), (mid + half).toInt(),
                            )
                            it.alpha = (255 * frac).toInt()
                            it.draw(c)
                        }
                    } else if (dX < 0) {
                        // фон справа, строка уезжает влево
                        c.drawRect(v.right + dX, v.top.toFloat(), v.right.toFloat(), v.bottom.toFloat(), redBg)
                        trash?.let {
                            val cx = v.right + dX / 2f
                            it.setBounds((cx - half).toInt(), (mid - half).toInt(), (cx + half).toInt(), (mid + half).toInt())
                            it.alpha = (255 * frac).toInt()
                            it.draw(c)
                        }
                    }
                }
                super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive)
            }
        }
        touchHelper = androidx.recyclerview.widget.ItemTouchHelper(cb)
        touchHelper.attachToRecyclerView(binding.setsList)
    }

    /** Долгое нажатие на упражнение: сводка по прошлому разу выполнения. */
    private fun showPrevWorkoutSummary(row: ExRow) {
        lifecycleScope.launch {
            val wid = vm.workoutId.value
            val w = Repo.get(requireContext()).dao.workoutById(wid) ?: return@launch
            val prev = Repo.get(requireContext()).dao
                .recentForExerciseBefore(row.we.exerciseId, w.date)
                .filter { it.workoutId != wid }
            val prevDay = prev.maxOfOrNull { it.date }
            if (prevDay == null) {
                android.widget.Toast.makeText(
                    requireContext(),
                    "«${row.we.nameSnapshot}» выполняется впервые",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
                return@launch
            }
            val prevSets = prev.filter { it.date == prevDay }.sortedBy { it.setIndex }
            val best1Rm = prevSets.maxOf { com.chepkall.gymlog.stats.OneRm.bestOfSet(it) }
            val lines = prevSets.mapIndexed { i, s ->
                val wTxt = if (s.weightKg % 1.0 == 0.0) s.weightKg.toInt().toString()
                else String.format(java.util.Locale.ROOT, "%.1f", s.weightKg)
                buildString {
                    append("${i + 1} · $wTxt×${s.reps}")
                    if (s.isDropset) append(" дс")
                    if (s.isFailure) append(" ⚠")
                }
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("${row.we.nameSnapshot} · ${Dates.dayMonth(prevDay)}")
                .setMessage(lines.joinToString("\n") + "\n\nЛучший 1ПМ: ${fmt(best1Rm)} кг")
                .setPositiveButton("ОК", null)
                .show()
        }
    }

    private fun showAddSetDialog(row: ExRow) {
        val dlg = AddSetDialog.newInstance(
            row.we.exerciseId, row.we.nameSnapshot,
            planRepsMin = row.we.planRepsMin,
            planRepsMax = row.we.planRepsMax,
        )
        dlg.setAddSetListener { weight, reps, dropJson, rpe, fail ->
            vm.addSet(row.we, weight, reps, dropJson, rpe, fail)
        }
        dlg.show(parentFragmentManager, AddSetDialog.TAG)
    }

    private fun showEditSetDialog(s: ExerciseSet, row: ExRow) {
        val dlg = AddSetDialog.newInstance(s.exerciseId, row.we.nameSnapshot, s)
        dlg.setAddSetListener { weight, reps, dropJson, rpe, fail ->
            vm.updateSet(
                s.copy(
                    weightKg = weight,
                    reps = reps,
                    dropset = dropJson?.takeIf { it.isNotBlank() },
                    isDropset = !dropJson.isNullOrBlank(),
                    rpe = rpe,
                    isFailure = fail,
                ),
            )
        }
        dlg.setOnDeleteListener { vm.deleteSet(s) }
        dlg.show(parentFragmentManager, AddSetDialog.TAG)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
