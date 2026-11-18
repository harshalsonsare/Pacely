package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        WorkoutEntity::class,
        TrackPointEntity::class,
        LapSplitEntity::class,
        ShoeEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class PacelyDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao

    companion object {
        @Volatile
        private var INSTANCE: PacelyDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workouts ADD COLUMN shoeId INTEGER DEFAULT NULL")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `shoes` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `brand` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `initialMileageKm` REAL NOT NULL DEFAULT 0.0,
                        `targetMaxKm` REAL NOT NULL DEFAULT 800.0,
                        `isDefault` INTEGER NOT NULL DEFAULT 0,
                        `isRetired` INTEGER NOT NULL DEFAULT 0,
                        `createdAtMillis` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): PacelyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PacelyDatabase::class.java,
                    "pacely_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
