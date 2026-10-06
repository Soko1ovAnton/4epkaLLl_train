package com.chepkall.gymlog.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Delete
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises WHERE archived = 0 ORDER BY isCustom ASC, name COLLATE NOCASE ASC")
    fun observeAllExercises(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun exerciseById(id: Long): Exercise?

    @Query("SELECT * FROM exercises WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<Exercise>

    @Query("SELECT * FROM exercises ORDER BY name COLLATE NOCASE")
    suspend fun allIncludingArchived(): List<Exercise>

    @Query(
        "SELECT * FROM exercises WHERE archived = 0 AND (name LIKE '%' || :q || '%' " +
            "OR primaryMuscle LIKE '%' || :q || '%' OR secondaryMuscles LIKE '%' || :q || '%') " +
            "ORDER BY name COLLATE NOCASE",
    )
    fun search(q: String): Flow<List<Exercise>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(e: Exercise): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<Exercise>)

    @Update
    suspend fun update(e: Exercise)

    @Delete
    suspend fun delete(e: Exercise)

    @Query("UPDATE exercises SET archived = 1 WHERE id = :id")
    suspend fun archive(id: Long)

    @Query("UPDATE exercises SET archived = 0 WHERE id = :id")
    suspend fun restore(id: Long)

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int
}

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workouts ORDER BY date DESC, orderPos ASC")
    fun observeAllWorkouts(): Flow<List<Workout>>

    @Query("SELECT * FROM workouts WHERE date = :epochDay ORDER BY orderPos ASC")
    fun observeByDate(epochDay: Long): Flow<List<Workout>>

    @Query("SELECT * FROM workouts WHERE date = :epochDay ORDER BY orderPos ASC")
    suspend fun workoutsOnDate(epochDay: Long): List<Workout>

    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun workoutById(id: Long): Workout?

    @Query("SELECT * FROM workouts WHERE id = :id")
    fun observeWorkoutById(id: Long): Flow<Workout?>

    @Query("SELECT * FROM workouts WHERE status = 1 LIMIT 1")
    suspend fun inProgressWorkout(): Workout?

    @Query("SELECT * FROM workouts WHERE status = 1 ORDER BY startedAt DESC LIMIT 1")
    fun observeInProgress(): Flow<Workout?>

    @Query("SELECT * FROM workouts WHERE status != 2 ORDER BY date DESC")
    suspend fun unfinished(): List<Workout>

    @Query("SELECT MIN(date) FROM workouts")
    suspend fun firstDate(): Long?

    @Insert
    suspend fun insert(w: Workout): Long

    @Update
    suspend fun update(w: Workout)

    @Delete
    suspend fun delete(w: Workout)

    @Query("SELECT MAX(orderPos) + 1 FROM workouts WHERE date = :epochDay")
    suspend fun nextOrderPos(epochDay: Long): Int
}

@Dao
interface WorkoutExerciseDao {
    @Query("SELECT * FROM workout_exercises WHERE workoutId = :workoutId ORDER BY orderPos ASC")
    fun observeForWorkout(workoutId: Long): Flow<List<WorkoutExercise>>

    @Query("SELECT * FROM workout_exercises WHERE workoutId = :workoutId ORDER BY orderPos ASC")
    suspend fun exercisesForWorkout(workoutId: Long): List<WorkoutExercise>

    @Query("SELECT * FROM workout_exercises WHERE id = :id")
    suspend fun workoutExerciseById(id: Long): WorkoutExercise?

    @Query("SELECT * FROM workout_exercises WHERE exerciseId = :exerciseId")
    suspend fun forExercise(exerciseId: Long): List<WorkoutExercise>

    @Insert
    suspend fun insert(we: WorkoutExercise): Long

    @Update
    suspend fun update(we: WorkoutExercise)

    @Delete
    suspend fun delete(we: WorkoutExercise)

    @Query("DELETE FROM workout_exercises WHERE workoutId = :workoutId")
    suspend fun deleteExercisesForWorkout(workoutId: Long)

    @Query("SELECT MAX(orderPos) + 1 FROM workout_exercises WHERE workoutId = :workoutId")
    suspend fun nextExerciseOrderPos(workoutId: Long): Int
}

@Dao
interface SetDao {
    @Query("SELECT * FROM sets WHERE workoutExerciseId = :weId ORDER BY setIndex ASC, id ASC")
    fun observeForExercise(weId: Long): Flow<List<ExerciseSet>>

    @Query("SELECT * FROM sets ORDER BY date ASC, createdAt ASC")
    fun observeAllSets(): Flow<List<ExerciseSet>>

    @Query("SELECT * FROM sets WHERE workoutExerciseId = :weId ORDER BY setIndex ASC, id ASC")
    suspend fun setsForWorkoutExercise(weId: Long): List<ExerciseSet>

    @Query("SELECT * FROM sets WHERE workoutId = :workoutId ORDER BY workoutExerciseId, setIndex")
    suspend fun setsForWorkout(workoutId: Long): List<ExerciseSet>

    @Query("SELECT * FROM sets WHERE workoutId = :workoutId ORDER BY createdAt ASC")
    fun observeSetsForWorkout(workoutId: Long): Flow<List<ExerciseSet>>

    @Query("SELECT * FROM sets WHERE exerciseId = :exerciseId ORDER BY date ASC, createdAt ASC")
    suspend fun historyForExercise(exerciseId: Long): List<ExerciseSet>

    @Query("SELECT * FROM sets WHERE exerciseId = :exerciseId AND date <= :beforeEpochDay ORDER BY date DESC, createdAt DESC LIMIT :limit")
    suspend fun recentForExerciseBefore(exerciseId: Long, beforeEpochDay: Long, limit: Int = 40): List<ExerciseSet>

    @Query("SELECT * FROM sets WHERE date BETWEEN :from AND :to ORDER BY date ASC, createdAt ASC")
    suspend fun inRange(from: Long, to: Long): List<ExerciseSet>

    @Query("SELECT * FROM sets ORDER BY date ASC, createdAt ASC")
    suspend fun allSets(): List<ExerciseSet>

    @Query("SELECT COUNT(DISTINCT date) FROM sets")
    suspend fun trainedDaysCount(): Int

    @Insert
    suspend fun insert(s: ExerciseSet): Long

    @Update
    suspend fun update(s: ExerciseSet)

    @Delete
    suspend fun deleteSet(s: ExerciseSet)

    @Query("DELETE FROM sets WHERE workoutExerciseId = :weId")
    suspend fun deleteForWorkoutExercise(weId: Long)

    @Query("DELETE FROM sets WHERE workoutId = :workoutId")
    suspend fun deleteForWorkout(workoutId: Long)
}

/** Счётчик упражнений по шаблонам (для списка). */
data class TemplateCount(val templateId: Long, val cnt: Int)

@Dao
interface TemplateDao {
    @Query("SELECT * FROM templates ORDER BY createdAt DESC")
    fun observeAllTemplates(): Flow<List<WorkoutTemplate>>

    @Query("SELECT * FROM templates WHERE id = :id")
    suspend fun templateById(id: Long): WorkoutTemplate?

    @Query("SELECT templateId AS templateId, COUNT(*) AS cnt FROM template_exercises GROUP BY templateId")
    fun observeCounts(): Flow<List<TemplateCount>>

    @Query("SELECT * FROM template_exercises WHERE templateId = :templateId ORDER BY orderPos ASC")
    suspend fun exercisesForTemplate(templateId: Long): List<TemplateExercise>

    @Insert
    suspend fun insert(t: WorkoutTemplate): Long

    @Update
    suspend fun update(t: WorkoutTemplate)

    @Delete
    suspend fun delete(t: WorkoutTemplate)

    @Insert
    suspend fun insertTemplateExercise(e: TemplateExercise): Long

    @Update
    suspend fun updateTemplateExercise(e: TemplateExercise)

    @Delete
    suspend fun deleteTemplateExercise(e: TemplateExercise)

    @Query("DELETE FROM template_exercises WHERE templateId = :templateId")
    suspend fun deleteExercisesForTemplate(templateId: Long)
}

/**
 * Атомарные операции над тренировкой (удаление каскада вручную — без FK,
 * чтобы не терять записи статистики при удалении истории).
 */
@Dao
abstract class GymDao :
    ExerciseDao, WorkoutDao, WorkoutExerciseDao, SetDao, TemplateDao {
    @Transaction
    open suspend fun deleteWorkoutCascade(w: Workout) {
        deleteForWorkout(w.id)
        deleteExercisesForWorkout(w.id)
        delete(w)
    }

    @Transaction
    open suspend fun deleteWorkoutExerciseCascade(we: WorkoutExercise) {
        deleteForWorkoutExercise(we.id)
        delete(we)
    }
}