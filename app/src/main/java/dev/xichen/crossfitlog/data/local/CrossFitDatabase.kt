package dev.xichen.crossfitlog.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [WorkoutSessionEntity::class, MovementRecordEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class CrossFitDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao

    companion object {
        const val SCHEMA_VERSION = 1
        fun create(context: Context): CrossFitDatabase =
            Room.databaseBuilder(context, CrossFitDatabase::class.java, DatabaseController.DATABASE_NAME).build()
    }
}
