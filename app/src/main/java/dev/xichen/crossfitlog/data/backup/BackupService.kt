package dev.xichen.crossfitlog.data.backup

import android.content.ContentResolver
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import dev.xichen.crossfitlog.data.local.CrossFitDatabase
import dev.xichen.crossfitlog.data.local.DatabaseController
import dev.xichen.crossfitlog.data.local.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.time.LocalDate
import java.util.UUID

data class RestoreReport(val restoredSessions: Int) {
    fun message() = "Restored $restoredSessions session${if (restoredSessions == 1) "" else "s"}."
}

class PreparedBackup internal constructor(
    internal val archive: File,
    val suggestedFilename: String,
) : Closeable {
    override fun close() { archive.delete() }
}

class BackupService(
    private val resolver: ContentResolver,
    private val databaseController: DatabaseController,
    private val photoStore: PhotoStore,
    private val cacheDir: File,
) {
    init {
        cacheDir.listFiles { file -> file.name.startsWith("prepared-backup-") || file.name.startsWith("backup-") || file.name.startsWith("restore-") }
            ?.forEach(File::deleteRecursively)
    }

    suspend fun prepare(appVersion: String): PreparedBackup = withContext(Dispatchers.IO) {
        val work = File(cacheDir, "backup-${UUID.randomUUID()}").apply { mkdirs() }
        val snapshot = File(work, "snapshot").apply { mkdirs() }
        val archive = File(cacheDir, "prepared-backup-${UUID.randomUUID()}.zip")
        try {
            val databaseSnapshot = databaseController.createSnapshot(File(snapshot, BACKUP_DATABASE_PATH)) { references ->
                photoStore.copySnapshot(references, File(snapshot, "photos"))
            }
            val paths = snapshot.walkTopDown().filter(File::isFile)
                .map { it.relativeTo(snapshot).invariantSeparatorsPath }
                .sorted().toList()
            val files = paths.map { BackupCodec.describeFile(snapshot, it) }
            val manifest = BackupManifest(
                format = BACKUP_FORMAT,
                formatVersion = BACKUP_VERSION,
                exportedAt = System.currentTimeMillis(),
                applicationVersion = appVersion,
                sessionCount = databaseSnapshot.sessionCount,
                files = files,
            )
            archive.outputStream().use { BackupCodec.writeArchive(it, snapshot, manifest) }
            PreparedBackup(archive, "crossfit-logger-backup-${LocalDate.now()}.zip")
        } catch (error: Throwable) {
            archive.delete()
            throw error
        } finally {
            work.deleteRecursively()
        }
    }

    suspend fun save(prepared: PreparedBackup, uri: Uri) = withContext(Dispatchers.IO) {
        try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                prepared.archive.inputStream().use { it.copyTo(output) }
            } ?: error("The selected destination could not be opened.")
        } finally {
            prepared.close()
        }
    }

    fun discard(prepared: PreparedBackup?) { prepared?.close() }

    suspend fun restore(uri: Uri): RestoreReport = withContext(Dispatchers.IO) {
        val staging = File(cacheDir, "restore-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val manifest = resolver.openInputStream(uri)?.use { BackupCodec.extractAndValidate(it, staging) }
                ?: error("The selected backup could not be opened.")
            val database = File(staging, BACKUP_DATABASE_PATH)
            val sessionCount = validateDatabase(database, staging, manifest)
            val stagedPhotos = File(staging, "photos").apply { mkdirs() }
            File(stagedPhotos, "thumbnails").mkdirs()
            databaseController.replaceDatabaseAndPhotos(database, stagedPhotos, photoStore.rootDirectory)
            RestoreReport(sessionCount)
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun validateDatabase(databaseFile: File, root: File, manifest: BackupManifest): Int {
        val referencedFiles = linkedSetOf(BACKUP_DATABASE_PATH)
        var count: Int
        SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            database.rawQuery("PRAGMA quick_check", null).use { cursor ->
                require(cursor.moveToFirst() && cursor.getString(0) == "ok") { "The backup database is corrupted." }
            }
            database.rawQuery("PRAGMA user_version", null).use { cursor ->
                require(cursor.moveToFirst() && cursor.getInt(0) == CrossFitDatabase.SCHEMA_VERSION) { "The backup database version is not supported." }
            }
            database.rawQuery("PRAGMA foreign_key_check", null).use { cursor ->
                require(!cursor.moveToFirst()) { "The backup database has broken relationships." }
            }
            database.rawQuery("SELECT COUNT(*) FROM workout_sessions", null).use { cursor ->
                require(cursor.moveToFirst()); count = cursor.getInt(0)
            }
            require(count == manifest.sessionCount) { "The backup session count does not match its manifest." }
            database.rawQuery("SELECT id FROM workout_sessions", null).use { cursor ->
                while (cursor.moveToNext()) UUID.fromString(cursor.getString(0))
            }
            database.rawQuery("SELECT id, name FROM movement_records", null).use { cursor ->
                while (cursor.moveToNext()) {
                    UUID.fromString(cursor.getString(0))
                    require(cursor.getString(1).isNotBlank()) { "A restored movement has no name." }
                }
            }
            database.rawQuery(
                "SELECT id, photo_filename, thumbnail_filename FROM workout_sessions WHERE photo_filename IS NOT NULL OR thumbnail_filename IS NOT NULL",
                null,
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    UUID.fromString(cursor.getString(0))
                    val photo = cursor.getString(1)
                    val thumbnail = cursor.getString(2)
                    require(!photo.isNullOrBlank() && !thumbnail.isNullOrBlank()) { "A restored session has incomplete photo data." }
                    require(File(photo).name == photo && File(thumbnail).name == thumbnail) { "A restored photo filename is unsafe." }
                    val photoPath = "photos/$photo"
                    val thumbnailPath = "photos/thumbnails/$thumbnail"
                    require(referencedFiles.add(photoPath) && referencedFiles.add(thumbnailPath)) { "The backup contains shared or duplicate photo references." }
                    photoStore.validateRestoredImage(File(root, photoPath))
                    photoStore.validateRestoredImage(File(root, thumbnailPath))
                }
            }
            database.rawQuery(
                "SELECT s.id FROM workout_sessions s LEFT JOIN movement_records m ON m.session_id = s.id GROUP BY s.id HAVING COUNT(m.id) = 0 LIMIT 1",
                null,
            ).use { cursor -> require(!cursor.moveToFirst()) { "A restored session has no movements." } }
        }
        require(referencedFiles == manifest.files.mapTo(linkedSetOf()) { it.path }) { "The backup contains missing or orphaned photo files." }
        return count
    }
}
