package com.chepkall.gymlog.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarQueries {
    @Query("SELECT * FROM workouts WHERE date BETWEEN :from AND :to")
    suspend fun workoutsInRange(from: Long, to: Long): List<Workout>

    @Query("SELECT * FROM workouts ORDER BY date DESC")
    fun observeAllWorkouts(): Flow<List<Workout>>

    @Query("SELECT DISTINCT date FROM sets ORDER BY date")
    fun observeSetDates(): Flow<List<Long>>

    @Query("SELECT * FROM workouts WHERE status = 1 ORDER BY startedAt DESC LIMIT 1")
    fun observeInProgress(): Flow<Workout?>

    @Query("SELECT COUNT(*) FROM sets WHERE workoutId = :workoutId")
    suspend fun setsCountForWorkout(workoutId: Long): Int
}