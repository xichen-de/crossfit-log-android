package dev.xichen.crossfitlog.data.backup

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object BackupCodec {
    val json = Json { prettyPrint = true; ignoreUnknownKeys = true; explicitNulls = false }
    fun encodeManifest(value: BackupManifest): String = json.encodeToString(value)
    fun encodeSessions(value: BackupSessions): String = json.encodeToString(value)
    fun decodeManifest(value: String): BackupManifest = json.decodeFromString(value)
    fun decodeSessions(value: String): BackupSessions = json.decodeFromString(value)

    fun validateManifest(manifest: BackupManifest) {
        require(manifest.format == BACKUP_FORMAT) { "This is not a CrossFit Log backup." }
        require(manifest.formatVersion == BACKUP_VERSION) { "This backup version is not supported." }
        require(manifest.sessionCount >= 0) { "The backup manifest is invalid." }
    }

    fun validateSessions(value: BackupSessions, expectedCount: Int) {
        require(value.sessions.size == expectedCount) { "The backup is incomplete." }
        require(value.sessions.map { it.id }.distinct().size == value.sessions.size) { "The backup contains duplicate sessions." }
        value.sessions.forEach { session ->
            java.util.UUID.fromString(session.id)
            require(session.movements.isNotEmpty() && session.movements.all { it.name.trim().isNotEmpty() }) { "The backup contains an invalid movement." }
        }
    }

    fun safeZipPath(name: String): Boolean {
        if (name.isBlank() || name.startsWith('/') || name.startsWith('\\')) return false
        val parts = name.replace('\\', '/').split('/')
        return parts.none { it == ".." || it.isBlank() } && !name.contains(':')
    }

    fun friendlyFailure(error: Throwable): String = when (error) {
        is SerializationException -> "The backup contains malformed data."
        is IllegalArgumentException -> error.message ?: "The backup is invalid."
        else -> "The backup could not be read."
    }
}
