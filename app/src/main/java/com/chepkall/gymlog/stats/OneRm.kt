package com.chepkall.gymlog.stats

import com.chepkall.gymlog.data.ExerciseSet
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/** Расчёты 1ПМ и объёмов. */
object OneRm {

    /** Формула Эпли: 1ПМ = вес × (1 + повторы / 30). */
    fun epley(weight: Double, reps: Int): Double = weight * (1.0 + reps / 30.0)

    /** Формула Бжицки: 1ПМ = вес × 36 / (37 - повторы), повторы < 37. */
    fun brzycki(weight: Double, reps: Int): Double =
        if (reps < 37) weight * 36.0 / (37.0 - reps) else weight

    /** Лучший (максимальный) 1ПМ из сегментов подхода по формуле Эпли. */
    fun bestOfSet(s: ExerciseSet): Double =
        s.segments().maxOfOrNull { epley(it.weight, it.reps) } ?: 0.0

    /** Оценка 1ПМ по лучшему подходу: если повторов <= 10 — точнее. */
    fun estimate(s: ExerciseSet): Double {
        var best = 0.0
        for (seg in s.segments()) {
            // Приоритет низкоповторным подходам: вес * (1 + reps/30), но при reps<=5 почти чистый вес
            val v = epley(seg.weight, seg.reps)
            if (v > best) best = v
        }
        return best
    }

    /** Округление рабочего веса до шага 2.5 кг (или 0.5 для малых весов). */
    fun roundToPlate(x: Double): Double {
        if (x <= 0) return 0.0
        return if (x < 20) ceil(x * 2) / 2 else ceil(x / 2.5) * 2.5
    }

    /** Проценты для расчёта рабочих весов от 1ПМ. */
    val percentTable = listOf(95, 90, 85, 80, 75, 70, 65, 60)

    fun weightForPercent(oneRm: Double, percent: Int): Double =
        roundToPlate(oneRm * percent / 100.0)

    /** Сглаживание прогрессии (экспоненциальное скользящее среднее) для графика. */
    fun smooth(values: List<Double>, alpha: Double = 0.35): List<Double> {
        if (values.isEmpty()) return values
        val out = ArrayList<Double>(values.size)
        var acc = values.first()
        for (v in values) {
            acc = alpha * v + (1 - alpha) * acc
            out += acc
        }
        return out
    }
}