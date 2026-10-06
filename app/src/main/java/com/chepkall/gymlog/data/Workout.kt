package com.chepkall.gymlog.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Тренировка: запланированная (в календаре) или начатая. */
@Entity(tableName = "workouts", indices = [Index(value = ["date"]), Index(value = ["status"])])
data class Workout(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Локальная дата epoch-day (LocalDate.toEpochDay()). */
    val date: Long,
    val name: String,
    val status: Int = STATUS_PLANNED,   // STATUS_PLANNED | STATUS_IN_PROGRESS | STATUS_DONE
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    val note: String = "",
    val orderPos: Int = 0,
    /** Из какого шаблона собрана (0 — собрана вручную). */
    val sourceTemplateId: Long = 0,
) {
    companion object {
        const val STATUS_PLANNED = 0
        const val STATUS_IN_PROGRESS = 1
        const val STATUS_DONE = 2
    }
}