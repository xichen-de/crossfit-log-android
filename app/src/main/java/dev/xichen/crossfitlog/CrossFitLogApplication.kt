package dev.xichen.crossfitlog

import android.app.Application
import dev.xichen.crossfitlog.data.backup.BackupService
import dev.xichen.crossfitlog.data.export.DataExportService
import dev.xichen.crossfitlog.data.local.DatabaseController
import dev.xichen.crossfitlog.data.local.PhotoStore
import dev.xichen.crossfitlog.data.repository.WorkoutRepository
import dev.xichen.crossfitlog.ocr.MlKitWhiteboardTextRecognizer

class CrossFitLogApplication : Application() {
    val databaseController by lazy { DatabaseController(this) }
    val database get() = databaseController.database()
    val repository get() = WorkoutRepository(database.workoutDao())
    val photoStore by lazy { PhotoStore(this) }
    val whiteboardTextRecognizer by lazy { MlKitWhiteboardTextRecognizer(this) }
    val backupService by lazy { BackupService(contentResolver, databaseController, photoStore, cacheDir) }
    val dataExportService get() = DataExportService(contentResolver, repository)
}
