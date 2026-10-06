package com.chepkall.gymlog.util

/** Подписи плана «подходы × повторы» (точно/диапазон). */
object Plans {

    fun repsLabel(min: Int, max: Int): String = when {
        min <= 0 -> "—"
        max <= min -> "$min"
        else -> "$min–$max"
    }

    fun planLabel(sets: Int, min: Int, max: Int): String = "$sets × ${repsLabel(min, max)}"
}
