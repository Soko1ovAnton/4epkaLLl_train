package com.chepkall.gymlog.ui.calendar

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.chepkall.gymlog.R
import com.chepkall.gymlog.data.Workout
import com.chepkall.gymlog.databinding.ItemDayCellBinding
import com.chepkall.gymlog.util.Dates

class MonthGridAdapter(
    private val onDayClick: (Long) -> Unit,
) : RecyclerView.Adapter<MonthGridAdapter.VH>() {

    private var cells: List<DayCell> = emptyList()
    private var selected: Long = Dates.todayEpochDay()

    fun submit(list: List<DayCell>) {
        cells = list
        notifyDataSetChanged()
    }

    fun setSelected(day: Long) {
        selected = day
        notifyDataSetChanged()
    }

    class VH(val binding: ItemDayCellBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemDayCellBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun getItemCount(): Int = cells.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val cell = cells[position]
        val b = holder.binding
        val ctx = b.root.context
        b.dayNum.text = if (cell.epochDay == 0L) "" else cell.epochDay.toString().let {
            Dates.epochDayToLocal(cell.epochDay).dayOfMonth.toString()
        }
        b.weekday.text = if (cell.epochDay == 0L) "" else Dates.weekdayShort(cell.epochDay)

        val selectable = cell.epochDay != 0L
        b.root.isEnabled = selectable
        b.root.isClickable = selectable
        if (!selectable) {
            b.root.background = null
            return
        }

        b.root.setOnClickListener { onDayClick(cell.epochDay) }

        // Стилизация
        b.dayNum.setTypeface(null, if (cell.isToday) Typeface.BOLD else Typeface.NORMAL)
        b.root.alpha = if (cell.inMonth) 1f else 0.35f
        when {
            cell.epochDay == selected -> {
                b.root.setBackgroundResource(R.drawable.bg_day_selected)
                b.dayNum.setTextColor(ContextCompat.getColor(ctx, R.color.md_theme_onPrimary))
                b.weekday.setTextColor(ContextCompat.getColor(ctx, R.color.md_theme_onPrimary))
            }
            else -> {
                b.root.setBackgroundResource(R.drawable.bg_day_normal)
                b.dayNum.setTextColor(ContextCompat.getColor(ctx, R.color.md_theme_onBackground))
                b.weekday.setTextColor(ContextCompat.getColor(ctx, R.color.md_theme_onSurfaceVariant))
            }
        }

        // Маркер состояния единственной тренировки дня: ✓ зелёная / запланирована — контурная
        if (cell.hasWorkout && cell.status == Workout.STATUS_DONE) {
            b.dots1.setBackgroundResource(R.drawable.bg_dot)
            b.dots1.backgroundTintList = ContextCompat.getColorStateList(ctx, R.color.md_theme_primary)
            b.dots2.setBackgroundResource(android.R.color.transparent)
            b.dots2.backgroundTintList = null
        } else if (cell.hasWorkout) {
            // запланирована/идёт — контурное кольцо
            b.dots1.setBackgroundResource(R.drawable.bg_dot_outline)
            b.dots1.backgroundTintList = ContextCompat.getColorStateList(ctx, R.color.md_theme_tertiary)
            b.dots2.setBackgroundResource(android.R.color.transparent)
            b.dots2.backgroundTintList = null
        } else {
            b.dots1.setBackgroundResource(android.R.color.transparent)
            b.dots1.backgroundTintList = null
            b.dots2.setBackgroundResource(android.R.color.transparent)
            b.dots2.backgroundTintList = null
        }
    }
}