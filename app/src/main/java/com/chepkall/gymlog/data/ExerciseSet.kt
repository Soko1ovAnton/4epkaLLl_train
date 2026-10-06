package com.chepkall.gymlog.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Один подход. Для дропсета [weightKg]/[reps] — первый (рабочий) сегмент,
 * детали дропсета — в [dropset] (JSON), выполняется сразу без отдыха.
 */
@Entity(
    tableName = "sets",
    indices = [Index(value = ["workoutExerciseId"]), Index(value = ["date"])],
)
data class ExerciseSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutExerciseId: Long,
    val workoutId: Long,
    val exerciseId: Long,
    /** Локальная дата epoch-day — для быстрых выборок статистики. */
    val date: Long,
    val setIndex: Int,          // номер подхода внутри упражнения
    val weightKg: Double,
    val reps: Int,
    val rpe: Double? = null,    // субъективная сложность 1..10, опционально
    val isDropset: Boolean = false,
    /** JSON вида [{"weight":20,"reps":8},{"weight":15,"reps":10},...] */
    val dropset: String? = null,
    val isFailure: Boolean = false,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    /** Все сегменты подхода: основной + сегменты дропсета. */
    fun segments(): List<Segment> {
        val list = mutableListOf(Segment(weightKg, reps))
        dropset?.let { ds ->
            parseDropset(ds).forEach { list += it }
        }
        return list
    }

    /** Суммарный тоннаж подхода (вес × повторы по всем сегментам). */
    fun volume(): Double = segments().sumOf { it.weight * it.reps }

    companion object {
        fun parseDropset(json: String?): List<Segment> {
            if (json.isNullOrBlank()) return emptyList()
            return try {
                val arr = com.google.gson.JsonParser.parseString(json).asJsonArray
                arr.map { el ->
                    val o = el.asJsonObject
                    Segment(
                        o.get("weight")?.asDouble ?: 0.0,
                        o.get("reps")?.asInt ?: 0,
                    )
                }.filter { it.reps > 0 }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}

data class Segment(val weight: Double, val reps: Int)