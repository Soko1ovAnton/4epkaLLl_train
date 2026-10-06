package com.chepkall.gymlog.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Запись об упражнении внутри тренировки (снимок названия на момент тренировки).
 */
@Entity(
    tableName = "workout_exercises",
    indices = [Index(value = ["workoutId"]), Index(value = ["exerciseId"])],
)
data class WorkoutExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exerciseId: Long,
    val nameSnapshot: String,
    val primaryMuscle: String,
    val secondaryMuscles: String,
    val orderPos: Int = 0,
    val note: String = "",
    /** План из шаблона: число подходов и повторы (0 — плана нет). */
    val planSets: Int = 0,
    val planRepsMin: Int = 0,
    val planRepsMax: Int = 0,
)