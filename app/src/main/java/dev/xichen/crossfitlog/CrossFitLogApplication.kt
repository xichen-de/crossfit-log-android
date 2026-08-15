package dev.xichen.crossfitlog

import android.app.Application
import dev.xichen.crossfitlog.data.backup.BackupService
import dev.xichen.crossfitlog.data.export.DataExportService
import dev.xichen.crossfitlog.data.local.CrossFitDatabase
import dev.xichen.crossfitlog.data.local.PhotoStore
import dev.xichen.crossfitlog.data.repository.WorkoutRepository

class CrossFitLogApplication : Application() {
    val database by lazy { CrossFitDatabase.create(this) }
    val repository by lazy { WorkoutRepository(database.workoutDao()) }
    val photoStore by lazy { PhotoStore(this) }
    val backupService by lazy { BackupService(contentResolver, repository, photoStore, cacheDir) }
    val dataExportService by lazy { DataExportService(contentResolver, repository) }
}
