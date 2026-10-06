package com.chepkall.gymlog.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Упражнение из каталога пользователя. */
@Entity(tableName = "exercises", indices = [Index(value = ["name"], unique = false)])
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val primaryMuscle: String,      // целевая группа мышц
    val secondaryMuscles: String,   // побочные, через запятую
    val isCustom: Boolean = false,  // создано пользователем
    val archived: Boolean = false,  // скрыто из каталога
    val createdAt: Long = System.currentTimeMillis(),
)