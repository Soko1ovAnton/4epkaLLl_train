package com.chepkall.gymlog.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Сохранённая тренировка-шаблон. */
@Entity(tableName = "templates")
data class WorkoutTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Упражнение внутри шаблона: число подходов + повторы (точно или диапазоном). */
@Entity(
    tableName = "template_exercises",
    indices = [Index(value = ["templateId"]), Index(value = ["exerciseId"])],
)
data class TemplateExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val exerciseId: Long,
    val nameSnapshot: String,
    val primaryMuscle: String,
    val secondaryMuscles: String,
    val orderPos: Int = 0,
    /** Сколько подходов planned. */
    val planSets: Int = 3,
    /** Повторы: точно (min == max), диапазон (min < max) или не задано (min == 0). */
    val planRepsMin: Int = 0,
    val planRepsMax: Int = 0,
)
