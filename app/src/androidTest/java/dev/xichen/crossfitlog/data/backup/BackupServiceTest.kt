package dev.xichen.crossfitlog.data.backup

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.xichen.crossfitlog.data.local.DatabaseController
import dev.xichen.crossfitlog.data.local.MovementRecordEntity
import dev.xichen.crossfitlog.data.local.PhotoStore
import dev.xichen.crossfitlog.data.local.WorkoutSessionEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class BackupServiceTest {
    @Test fun databaseAndPhotosSurviveBackupAndRestore() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(DatabaseController.DATABASE_NAME)
        File(context.filesDir, "photos").deleteRecursively()
        val controller = DatabaseController(context)
        val photoStore = PhotoStore(context)
        val service = BackupService(context.contentResolver, controller, photoStore, context.cacheDir)
        val sessionId = UUID.randomUUID().toString()
        val movementId = UUID.randomUUID().toString()
        val photoName = "$sessionId.jpg"
        writeJpeg(File(photoStore.rootDirectory, photoName))
        writeJpeg(File(photoStore.rootDirectory, "thumbnails/$photoName"))
        controller.database().workoutDao().insertComplete(
            WorkoutSessionEntity(sessionId, 1, "note", photoName, photoName, 1, 1),
            listOf(MovementRecordEntity(movementId, sessionId, "Back Squat", "back squat", "60 kg", "5", "", 0)),
        )

        val archive = File(context.cacheDir, "backup-service-test.zip").apply { delete() }
        service.save(service.prepare("test"), Uri.fromFile(archive))
        val report = service.restore(Uri.fromFile(archive))

        assertEquals(1, report.restoredSessions)
        assertEquals(sessionId, controller.database().workoutDao().getAllSessions().single().session.id)
        assertTrue(photoStore.photoFile(photoName)?.isFile == true)
        assertTrue(photoStore.thumbnailFile(photoName)?.isFile == true)
        archive.delete()
    }

    private fun writeJpeg(file: File) {
        file.parentFile?.mkdirs()
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        try { file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) } }
        finally { bitmap.recycle() }
    }
}
