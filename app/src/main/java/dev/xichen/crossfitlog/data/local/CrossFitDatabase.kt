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
        fun create(context: Context): CrossFitDatabase =
            Room.databaseBuilder(context, CrossFitDatabase::class.java, "crossfit-log.db").build()
    }
}
