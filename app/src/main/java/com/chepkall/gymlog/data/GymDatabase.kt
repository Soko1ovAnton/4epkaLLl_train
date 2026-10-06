package com.chepkall.gymlog.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Exercise::class, Workout::class, WorkoutExercise::class, ExerciseSet::class, WorkoutTemplate::class, TemplateExercise::class],
    version = 3,
    exportSchema = false,
)
abstract class GymDatabase : RoomDatabase() {
    abstract fun gymDao(): GymDao
    abstract fun calendarQueries(): CalendarQueries

    companion object {
        @Volatile private var instance: GymDatabase? = null

        fun get(context: Context): GymDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    GymDatabase::class.java,
                    "gymlog.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build().also { instance = it }
            }

        /** v1 → v2: «Задняя поверхность бедра» переименована в «Бицепс бедра». */
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "UPDATE exercises SET primaryMuscle = 'Бицепс бедра' WHERE primaryMuscle = 'Задняя поверхность бедра'",
                )
                db.execSQL(
                    "UPDATE exercises SET secondaryMuscles = REPLACE(secondaryMuscles, 'Задняя поверхность бедра', 'Бицепс бедра')",
                )
            }
        }

        /** v2 → v3: шаблоны тренировок + план подходов/повторов на упражнениях. */
        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS templates (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS template_exercises (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "templateId INTEGER NOT NULL, " +
                        "exerciseId INTEGER NOT NULL, " +
                        "nameSnapshot TEXT NOT NULL, " +
                        "primaryMuscle TEXT NOT NULL, " +
                        "secondaryMuscles TEXT NOT NULL, " +
                        "orderPos INTEGER NOT NULL, " +
                        "planSets INTEGER NOT NULL, " +
                        "planRepsMin INTEGER NOT NULL, " +
                        "planRepsMax INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_template_exercises_templateId ON template_exercises(templateId)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_template_exercises_exerciseId ON template_exercises(exerciseId)",
                )
                db.execSQL("ALTER TABLE workouts ADD COLUMN sourceTemplateId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE workout_exercises ADD COLUMN planSets INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE workout_exercises ADD COLUMN planRepsMin INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE workout_exercises ADD COLUMN planRepsMax INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}