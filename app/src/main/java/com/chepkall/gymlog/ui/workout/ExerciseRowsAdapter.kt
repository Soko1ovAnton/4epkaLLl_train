package com.chepkall.gymlog.ui.workout

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chepkall.gymlog.data.ExerciseSet
import com.chepkall.gymlog.databinding.ItemExerciseRowBinding

/** Список упражнений на экране тренировки: строка = упражнение + его подходы. */
class ExerciseRowsAdapter(
    private val onAddSet: (ExRow) -> Unit,
    private val onMoveUp: (ExRow) -> Unit,
    private val onMoveDown: (ExRow) -> Unit,
    private val onEditSet: (ExerciseSet, ExRow) -> Unit,
    private val onNote: (ExRow) -> Unit,
    private val onLongPress: (ExRow) -> Unit = {},
    private val dragStart: (RecyclerView.ViewHolder) -> Unit = {},
) : RecyclerView.Adapter<ExerciseRowsAdapter.RowVH>() {

    private val items = mutableListOf<ExRow>()

    fun submit(list: List<ExRow>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    /** Перестановка для drag&drop (только UI; в БД пишется после завершения перетаскивания). */
    fun move(from: Int, to: Int) {
        if (from !in items.indices || to !in items.indices || from == to) return
        val r = items.removeAt(from)
        items.add(to, r)
        notifyItemMoved(from, to)
    }

    /** Текущий порядок строк (для сохранения в БД после перетаскивания). */
    fun snapshot(): List<ExRow> = items.toList()

    class RowVH(val b: ItemExerciseRowBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowVH {
        val b = ItemExerciseRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RowVH(b)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(h: RowVH, position: Int) {
        val row = items[position]
        val ctx = h.b.root.context

        h.b.rowName.text = row.we.nameSnapshot
        val secondary = row.we.secondaryMuscles
            ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.joinToString(", ")
        h.b.rowMuscles.text = listOfNotNull(row.we.primaryMuscle, secondary).joinToString(" · ")
        h.b.rowNoteMark.visibility = if (row.we.note.isBlank()) View.GONE else View.VISIBLE

        h.b.setsMini.layoutManager = LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false)
        h.b.setsMini.adapter = SetsMiniAdapter(
            row.sets, row.we.planSets, row.we.planRepsMin, row.we.planRepsMax,
        ) { s -> onEditSet(s, row) }

        h.b.btnAddSet.setOnClickListener { onAddSet(row) }
        h.b.rowUp.setOnClickListener { onMoveUp(row) }
        h.b.rowDown.setOnClickListener { onMoveDown(row) }
        h.b.rowNote.setOnClickListener { onNote(row) }
        // Долгое нажатие — сводка по прошлому разу этого упражнения
        h.b.root.setOnLongClickListener { onLongPress(row); true }
        // Ручка перетаскивания: drag&drop порядок упражнений
        h.b.rowDrag.setOnTouchListener { _, e ->
            if (e.actionMasked == android.view.MotionEvent.ACTION_DOWN) dragStart(h)
            false
        }
    }

    /** Строка по позиции (для свайп-удаления). */
    fun get(position: Int): ExRow? = items.getOrNull(position)
}
