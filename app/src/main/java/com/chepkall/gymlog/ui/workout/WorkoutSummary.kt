package com.chepkall.gymlog.ui.workout

import android.graphics.Typeface
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.chepkall.gymlog.R
import com.chepkall.gymlog.data.Repo
import com.chepkall.gymlog.stats.OneRm
import com.chepkall.gymlog.util.Dates
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Всплывающее окно «Итоги»: компактная таблица прогресса — 1ПМ лучшего
 * подхода сейчас vs прошлый раз по каждому упражнению.
 */
object WorkoutSummary {

    fun show(fragment: Fragment, workoutId: Long, onClose: (() -> Unit)? = null) {
        val ctx = fragment.requireContext()
        fragment.lifecycleScope.launch {
            val repo = Repo.get(ctx)
            val w = repo.dao.workoutById(workoutId)
            if (w == null) {
                // Пустая тренировка была удалена — просто закрываем цепочку
                onClose?.invoke()
                return@launch
            }
            val exs = repo.dao.exercisesForWorkout(workoutId).sortedBy { it.orderPos }

            data class Row(
                val name: String,
                val cur1Rm: Double,
                val prev1Rm: Double,
                val prevDay: Long?,
            )

            val rows = exs.map { we ->
                val sets = repo.dao.setsForWorkoutExercise(we.id)
                val cur = sets.maxOfOrNull { OneRm.bestOfSet(it) } ?: 0.0
                val prevSets = repo.dao.recentForExerciseBefore(we.exerciseId, w.date)
                    .filter { it.workoutId != workoutId }
                val prevDay = prevSets.maxOfOrNull { it.date }
                val prev = prevSets.filter { it.date == prevDay }
                    .maxOfOrNull { OneRm.bestOfSet(it) } ?: 0.0
                Row(we.nameSnapshot, cur, prev, prevDay)
            }.filter { it.cur1Rm > 0.0 }

            val density = ctx.resources.displayMetrics.density
            fun dp(v: Int) = (v * density).toInt()

            val scroll = ScrollView(ctx)
            val box = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(8), dp(20), dp(4))
            }

            fun cell(text: String, weight: Float, sizeSp: Float, colorRes: Int, bold: Boolean = false, right: Boolean = false) =
                TextView(ctx).apply {
                    this.text = text
                    textSize = sizeSp
                    setTextColor(ContextCompat.getColor(ctx, colorRes))
                    setTypeface(typeface, if (bold) Typeface.BOLD else Typeface.NORMAL)
                    gravity = if (right) android.view.Gravity.END else android.view.Gravity.START
                }

            fun addTableRow(cells: List<Triple<String, Float, Int>>, bold: Boolean = false, rightCols: Boolean) {
                val rowLl = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
                cells.forEachIndexed { i, (text, weight, color) ->
                    rowLl.addView(
                        cell(text, weight, if (bold) 13f else 14f, color, bold = bold, right = rightCols && i > 0),
                        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight).apply {
                            marginEnd = dp(8)
                        },
                    )
                }
                box.addView(
                    rowLl,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = dp(6); bottomMargin = dp(6) },
                )
            }

            fun addDivider() {
                val div = android.view.View(ctx)
                div.setBackgroundColor(ContextCompat.getColor(ctx, R.color.md_theme_outline_variant))
                box.addView(
                    div,
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
                        topMargin = dp(2); bottomMargin = dp(2)
                    },
                )
            }

            // Заголовок таблицы
            addTableRow(
                listOf(
                    Triple("Упражнение", 1f, R.color.md_theme_onSurfaceVariant),
                    Triple("1ПМ", 0.5f, R.color.md_theme_onSurfaceVariant),
                    Triple("Прошлый раз", 0.7f, R.color.md_theme_onSurfaceVariant),
                ),
                bold = true,
                rightCols = true,
            )

            rows.forEach { r ->
                val diff = r.cur1Rm - r.prev1Rm
                val delta = when {
                    r.prevDay == null || r.prev1Rm <= 0.0 -> "впервые"
                    diff > 0.05 -> "▲ +${fmt(diff)}"
                    diff < -0.05 -> "▼ −${fmt(abs(diff))}"
                    else -> "= без изм."
                }
                val deltaColor = when {
                    r.prevDay == null || r.prev1Rm <= 0.0 -> R.color.md_theme_onSurfaceVariant
                    diff > 0.05 -> R.color.md_theme_primary
                    diff < -0.05 -> R.color.md_theme_error
                    else -> R.color.md_theme_onSurfaceVariant
                }
                addTableRow(
                    listOf(
                        Triple(r.name, 1f, R.color.md_theme_onSurface),
                        Triple(if (r.cur1Rm > 0) "${fmt(r.cur1Rm)} кг" else "—", 0.5f, R.color.md_theme_onSurface),
                        Triple(delta, 0.7f, deltaColor),
                    ),
                    rightCols = true,
                )
                addDivider()
            }

            if (rows.isEmpty()) {
                addTableRow(
                    listOf(Triple("Подходов не записано", 1f, R.color.md_theme_onSurfaceVariant)),
                    rightCols = false,
                )
            }

            scroll.addView(box)

            MaterialAlertDialogBuilder(ctx)
                .setTitle("Итоги · ${Dates.dayMonth(w.date)}")
                .setView(scroll)
                .setPositiveButton("ОК", null)
                .setOnDismissListener { onClose?.invoke() }
                .show()
        }
    }

    private fun fmt(v: Double): String =
        if (v % 1.0 == 0.0) v.toInt().toString() else String.format(java.util.Locale.ROOT, "%.1f", v)
}