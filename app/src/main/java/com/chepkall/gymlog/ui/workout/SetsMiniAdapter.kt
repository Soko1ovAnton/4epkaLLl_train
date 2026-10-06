package com.chepkall.gymlog.ui.workout

import android.graphics.Color
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.chepkall.gymlog.R
import com.chepkall.gymlog.data.ExerciseSet
import com.chepkall.gymlog.util.Plans

/** Чипы подходов в строке упражнения: сделанные + пунктирные слоты плана из шаблона. */
class SetsMiniAdapter(
    private val sets: List<ExerciseSet>,
    private val planSets: Int,
    private val planRepsMin: Int,
    private val planRepsMax: Int,
    private val onEdit: (ExerciseSet) -> Unit,
) : RecyclerView.Adapter<SetsMiniAdapter.VH>() {

    class VH(val v: TextView) : RecyclerView.ViewHolder(v)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val tv = TextView(parent.context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = (8 * parent.context.resources.displayMetrics.density).toInt() }
            setPadding(20, 8, 20, 8)
            textSize = 14f
        }
        return VH(tv)
    }

    override fun getItemCount(): Int = sets.size + planSlots

    /** Сколько слотов плана ещё не заполнено реальными подходами. */
    private val planSlots: Int
        get() = (planSets - sets.size).coerceAtLeast(0)

    override fun onBindViewHolder(h: VH, position: Int) {
        if (position < sets.size) {
            val s = sets[position]
            val w = if (s.weightKg % 1.0 == 0.0) s.weightKg.toInt().toString() else
                String.format(java.util.Locale.ROOT, "%.1f", s.weightKg)
            var text = "${s.setIndex + 1} · $w×${s.reps}"
            if (s.isDropset) text += " дс"
            if (s.isFailure) text += " ⚠"
            h.v.text = text
            h.v.setTextColor(Color.WHITE)
            h.v.setBackgroundResource(R.drawable.bg_set_chip)
            h.v.setOnClickListener { onEdit(s) }
        } else {
            // Пустой слот плана — останется ввести только вес
            val slot = position + 1
            h.v.text = "$slot · ${Plans.repsLabel(planRepsMin, planRepsMax)}"
            h.v.setTextColor(ContextCompat.getColor(h.v.context, R.color.md_theme_onSurfaceVariant))
            h.v.setBackgroundResource(R.drawable.bg_set_chip_plan)
            h.v.setOnClickListener(null)
            h.v.isClickable = false
        }
    }
}
