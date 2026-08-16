package dev.xichen.crossfitlog.data.local

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

data class DatabaseSnapshot(
    val sessionCount: Int,
    val photos: List<Pair<String, String?>>,
)

/** Owns the replaceable Room instance and serializes backup/restore maintenance. */
class DatabaseController(private val context: Context) {
    private val maintenance = Mutex()
    private val databaseFile get() = context.getDatabasePath(DATABASE_NAME)
    private val livePhotos get() = File(context.filesDir, "photos")
    private val rollbackDir get() = File(context.filesDir, ROLLBACK_DIRECTORY)
    @Volatile private var instance: CrossFitDatabase? = null

    init { recoverInterruptedRestore() }

    fun database(): CrossFitDatabase = instance ?: synchronized(this) {
        instance ?: CrossFitDatabase.create(context).also { instance = it }
    }

    suspend fun createSnapshot(targetDatabase: File, copyPhotos: (List<Pair<String, String?>>) -> Unit): DatabaseSnapshot =
        maintenance.withLock {
            withContext(Dispatchers.IO) {
                val room = database()
                val sqlite = room.openHelper.writableDatabase
                sqlite.query("PRAGMA wal_checkpoint(FULL)").use { cursor ->
                    check(cursor.moveToFirst() && cursor.getInt(0) == 0 && cursor.getInt(1) == cursor.getInt(2)) {
                        "The database was busy and could not be checkpointed."
                    }
                }
                var result: DatabaseSnapshot? = null
                room.withTransaction {
                    val photos = sqlite.query(
                        "SELECT photo_filename, thumbnail_filename FROM workout_sessions WHERE photo_filename IS NOT NULL OR thumbnail_filename IS NOT NULL",
                    ).use { cursor ->
                        buildList {
                            while (cursor.moveToNext()) {
                                check(!cursor.isNull(0) && !cursor.isNull(1)) { "A workout has incomplete photo data." }
                                add(cursor.getString(0) to cursor.getString(1))
                            }
                        }
                    }
                    targetDatabase.parentFile?.mkdirs()
                    databaseFile.copyTo(targetDatabase, overwrite = true)
                    copyPhotos(photos)
                    val count = sqlite.query("SELECT COUNT(*) FROM workout_sessions").use { cursor ->
                        check(cursor.moveToFirst()); cursor.getInt(0)
                    }
                    result = DatabaseSnapshot(count, photos)
                }
                checkNotNull(result)
            }
        }

    suspend fun replaceDatabaseAndPhotos(stagedDatabase: File, stagedPhotos: File, livePhotos: File) = maintenance.withLock {
        withContext(Dispatchers.IO) {
            require(livePhotos.canonicalFile == this@DatabaseController.livePhotos.canonicalFile)
            val rollback = rollbackDir.apply { deleteRecursively(); mkdirs() }
            val oldDb = File(rollback, DATABASE_NAME)
            val oldPhotos = File(rollback, "photos")
            val phase = File(rollback, "phase").apply { writeText(PHASE_PREPARED) }
            closeCurrent()
            try {
                moveIfExists(databaseFile, oldDb)
                databaseAuxiliaryFiles().forEach { it.delete() }
                moveIfExists(livePhotos, oldPhotos)
                phase.writeText(PHASE_MOVED)
                stagedDatabase.copyTo(databaseFile, overwrite = false)
                check(stagedPhotos.renameTo(livePhotos)) { "Could not install restored photos." }
                phase.writeText(PHASE_INSTALLED)
                database().openHelper.writableDatabase
                rollback.deleteRecursively()
            } catch (error: Throwable) {
                rollbackInterruptedRestore(rollback)
                database().openHelper.writableDatabase
                throw error
            } finally {
                rollback.deleteRecursively()
            }
        }
    }

    private fun closeCurrent() = synchronized(this) {
        instance?.close()
        instance = null
    }

    private fun databaseAuxiliaryFiles() = listOf(File(databaseFile.path + "-wal"), File(databaseFile.path + "-shm"), File(databaseFile.path + "-journal"))

    private fun recoverInterruptedRestore() {
        if (rollbackDir.exists()) rollbackInterruptedRestore(rollbackDir)
    }

    private fun rollbackInterruptedRestore(rollback: File) {
        closeCurrent()
        val phase = File(rollback, "phase").takeIf(File::isFile)?.readText()
        val oldDb = File(rollback, DATABASE_NAME)
        val oldPhotos = File(rollback, "photos")
        if (phase == PHASE_MOVED || phase == PHASE_INSTALLED) {
            databaseFile.delete()
            databaseAuxiliaryFiles().forEach { it.delete() }
            livePhotos.deleteRecursively()
        }
        if (oldDb.exists()) {
            databaseFile.delete()
            databaseAuxiliaryFiles().forEach { it.delete() }
            moveIfExists(oldDb, databaseFile)
        }
        if (oldPhotos.exists()) {
            livePhotos.deleteRecursively()
            moveIfExists(oldPhotos, livePhotos)
        }
        livePhotos.mkdirs()
        File(livePhotos, "thumbnails").mkdirs()
        rollback.deleteRecursively()
    }

    private fun moveIfExists(source: File, target: File) {
        if (!source.exists()) return
        target.parentFile?.mkdirs()
        check(source.renameTo(target)) { "Could not move ${source.name}." }
    }

    companion object {
        const val DATABASE_NAME = "crossfit-log.db"
        private const val ROLLBACK_DIRECTORY = ".restore-rollback"
        private const val PHASE_PREPARED = "prepared"
        private const val PHASE_MOVED = "moved"
        private const val PHASE_INSTALLED = "installed"
    }
}
