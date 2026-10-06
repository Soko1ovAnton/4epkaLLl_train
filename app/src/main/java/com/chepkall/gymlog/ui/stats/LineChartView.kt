package com.chepkall.gymlog.ui.stats

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.chepkall.gymlog.R
import com.chepkall.gymlog.util.Dates
import kotlin.math.max
import kotlin.math.min

/** Простой линейный график прогресса без внешних библиотек. */
class LineChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var points: List<ProgressPoint> = emptyList()

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.md_theme_primary)
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x266C93DE.toInt()
        style = Paint.Style.FILL
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.md_theme_primary)
        style = Paint.Style.FILL
    }
    private val dotStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF000000.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x22FFFFFF
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.md_theme_onSurfaceVariant)
        textSize = 26f
    }

    fun setPoints(list: List<ProgressPoint>) {
        points = list
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.isEmpty()) {
            val cx = width / 2f
            val cy = height / 2f
            labelPaint.textAlign = Paint.Align.CENTER
            labelPaint.textSize = 36f
            canvas.drawText("Нет данных", cx, cy, labelPaint)
            return
        }
        val padL = 16f; val padR = 16f; val padT = 24f; val padB = 40f
        val w = width - padL - padR
        val h = height - padT - padB

        val maxV = max(points.maxOf { it.oneRm }, points.maxOf { it.topWeight }) * 1.1
        val minV = 0.0

        fun x(i: Int): Float = padL + if (points.size == 1) w / 2 else w * i / (points.size - 1)
        fun y(v: Double): Float = (padT + h * (1 - (v - minV) / (if (maxV > 0) maxV else 1.0))).toFloat()

        // Сетка: 4 горизонтальные линии
        for (g in 0..3) {
            val gy = padT + h * g / 3f
            canvas.drawLine(padL, gy, padL + w, gy, gridPaint)
            val value = maxV * (1 - g / 3.0)
            labelPaint.textAlign = Paint.Align.LEFT
            labelPaint.textSize = 22f
            canvas.drawText(fmtShort(value), padL + 4f, gy - 6f, labelPaint)
        }

        // Линия 1ПМ
        val path = Path()
        points.forEachIndexed { i, p ->
            val px = x(i); val py = y(p.oneRm)
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        // Заливка
        val fill = Path(path)
        fill.lineTo(x(points.size - 1), padT + h)
        fill.lineTo(x(0), padT + h)
        fill.close()
        canvas.drawPath(fill, fillPaint)
        canvas.drawPath(path, linePaint)

        // Точки (верхний вес по дню)
        val dark = 0xFF000000.toInt()
        points.forEachIndexed { i, p ->
            val px = x(i); val py = y(p.topWeight)
            canvas.drawCircle(px, py, 5f, dotPaint)
            dotStroke.color = dark
            canvas.drawCircle(px, py, 5f, dotStroke)
        }
        // Точки 1ПМ сверху
        points.forEachIndexed { i, p ->
            canvas.drawCircle(x(i), y(p.oneRm), 7f, dotPaint)
            dotStroke.color = dark
            canvas.drawCircle(x(i), y(p.oneRm), 7f, dotStroke)
        }

        // Подписи дат: первая, средняя, последняя
        labelPaint.textAlign = Paint.Align.CENTER
        labelPaint.textSize = 24f
        val idxs = when {
            points.size == 1 -> listOf(0)
            points.size == 2 -> listOf(0, points.size - 1)
            else -> listOf(0, points.size / 2, points.size - 1)
        }
        for (i in idxs) {
            canvas.drawText(Dates.dayMonth(points[i].epochDay), x(i), height - 8f, labelPaint)
        }
    }

    private fun fmtShort(v: Double): String = if (v >= 100) v.toInt().toString() else String.format(java.util.Locale.ROOT, "%.1f", v)
}