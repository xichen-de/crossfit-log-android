package dev.xichen.crossfitlog.data.backup

import android.content.ContentResolver
import android.net.Uri
import dev.xichen.crossfitlog.data.local.PhotoStore
import dev.xichen.crossfitlog.data.repository.WorkoutRepository
import dev.xichen.crossfitlog.domain.isMovementNameValid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class RestoreReport(val imported: Int, val skipped: Int, val failed: Int) {
    fun message(): String = "Imported $imported, skipped $skipped, failed $failed ${if (failed == 1) "session" else "sessions"}."
}

class BackupService(
    private val resolver: ContentResolver,
    private val repository: WorkoutRepository,
    private val photoStore: PhotoStore,
    private val cacheDir: File,
) {
    suspend fun export(uri: Uri, appVersion: String) = withContext(Dispatchers.IO) {
        val sessions = repository.getAllSessions()
        resolver.openOutputStream(uri, "w")?.use { output ->
            ZipOutputStream(output.buffered()).use { zip ->
                val manifest = BackupManifest(BACKUP_FORMAT, BACKUP_VERSION, System.currentTimeMillis(), appVersion, sessions.size)
                zip.textEntry("manifest.json", BackupCodec.encodeManifest(manifest))
                zip.textEntry("sessions.json", BackupCodec.encodeSessions(BackupSessions(sessions.map { it.toBackup() })))
                sessions.forEach { session ->
                    photoStore.photoFile(session.photoFilename)?.let { photo ->
                        zip.putNextEntry(ZipEntry("photos/${session.id}.jpg"))
                        photo.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
        } ?: error("The selected destination could not be opened.")
    }

    suspend fun restore(uri: Uri): RestoreReport = withContext(Dispatchers.IO) {
        val staging = File(cacheDir, "restore-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            var manifestText: String? = null
            var sessionsText: String? = null
            val stagedPhotos = mutableMapOf<String, File>()
            var totalBytes = 0L
            resolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input.buffered()).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        require(BackupCodec.safeZipPath(entry.name)) { "The backup contains an unsafe file path." }
                        require(!entry.isDirectory) { "The backup contains an unexpected directory entry." }
                        when {
                            entry.name == "manifest.json" -> manifestText = zip.readLimited(1_000_000).decodeToString()
                            entry.name == "sessions.json" -> sessionsText = zip.readLimited(10_000_000).decodeToString()
                            entry.name.matches(Regex("photos/[0-9a-fA-F-]+\\.jpg")) -> {
                                val id = entry.name.removePrefix("photos/").removeSuffix(".jpg")
                                UUID.fromString(id)
                                require(id !in stagedPhotos) { "The backup contains duplicate photos." }
                                val target = File(staging, "$id.jpg")
                                target.outputStream().use { output -> totalBytes += zip.copyLimited(output, 50_000_000) }
                                require(totalBytes <= 1_000_000_000) { "The backup is too large." }
                                stagedPhotos[id] = target
                            }
                            else -> error("The backup contains an unexpected file: ${entry.name}")
                        }
                        zip.closeEntry()
                    }
                }
            } ?: error("The selected backup could not be opened.")

            val manifest = BackupCodec.decodeManifest(requireNotNull(manifestText) { "The backup manifest is missing." })
            BackupCodec.validateManifest(manifest)
            val backupSessions = BackupCodec.decodeSessions(requireNotNull(sessionsText) { "The session data is missing." })
            BackupCodec.validateSessions(backupSessions, manifest.sessionCount)
            val sessions = backupSessions.sessions

            var imported = 0
            var skipped = 0
            var failed = 0
            sessions.forEach { backup ->
                try {
                    UUID.fromString(backup.id)
                    require(backup.movements.isNotEmpty() && backup.movements.all { isMovementNameValid(it.name) }) { "Invalid movement." }
                    if (repository.exists(backup.id)) { skipped++; return@forEach }
                    val staged = backup.photoFilename?.let { stagedPhotos[backup.id] ?: error("A session photo is missing.") }
                    val stored = staged?.let { photoStore.installRestoredPhoto(it, backup.id) }
                    try {
                        repository.create(backup.toDomain(stored?.photoFilename, stored?.thumbnailFilename))
                        imported++
                    } catch (error: Throwable) {
                        photoStore.delete(stored?.photoFilename, stored?.thumbnailFilename)
                        throw error
                    }
                } catch (_: Throwable) { failed++ }
            }
            RestoreReport(imported, skipped, failed)
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun ZipOutputStream.textEntry(name: String, text: String) {
        putNextEntry(ZipEntry(name)); write(text.encodeToByteArray()); closeEntry()
    }

    private fun ZipInputStream.readLimited(max: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        copyLimited(output, max.toLong())
        return output.toByteArray()
    }

    private fun ZipInputStream.copyLimited(output: java.io.OutputStream, max: Long): Long {
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            require(total <= max) { "A backup entry is too large." }
            output.write(buffer, 0, count)
        }
        return total
    }
}
