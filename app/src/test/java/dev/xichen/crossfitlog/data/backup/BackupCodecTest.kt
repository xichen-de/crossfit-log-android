package dev.xichen.crossfitlog.data.backup

import kotlinx.serialization.SerializationException
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class BackupCodecTest {
    private val id = UUID.randomUUID().toString()
    private val session = BackupSession(id, 1, "note", "$id.jpg", 1, 2, listOf(BackupMovement(UUID.randomUUID().toString(), "Back Squat", "60 kg", "5", "", 0)))

    @Test fun backupRoundTrips() {
        val original = BackupSessions(listOf(session))
        assertEquals(original, BackupCodec.decodeSessions(BackupCodec.encodeSessions(original)))
    }

    @Test fun unsupportedVersionIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.validateManifest(BackupManifest(BACKUP_FORMAT, 99, 0, "x", 0)) }
    }

    @Test fun malformedJsonIsRejected() {
        assertThrows(SerializationException::class.java) { BackupCodec.decodeSessions("{not-json") }
    }

    @Test fun duplicateRestoreIdsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.validateSessions(BackupSessions(listOf(session, session)), 2) }
    }

    @Test fun incompleteBackupIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.validateSessions(BackupSessions(listOf(session)), 2) }
    }

    @Test fun zipTraversalIsRejected() {
        assertFalse(BackupCodec.safeZipPath("../sessions.json"))
        assertFalse(BackupCodec.safeZipPath("photos/../../evil"))
        assertFalse(BackupCodec.safeZipPath("/absolute"))
        assertFalse(BackupCodec.safeZipPath("C:\\evil"))
        assertTrue(BackupCodec.safeZipPath("photos/$id.jpg"))
    }
}
