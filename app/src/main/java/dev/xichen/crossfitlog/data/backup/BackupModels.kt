package dev.xichen.crossfitlog.data.backup

import kotlinx.serialization.Serializable

const val BACKUP_FORMAT = "crossfit-log"
const val BACKUP_VERSION = 2
const val BACKUP_DATABASE_PATH = "database.sqlite"

@Serializable
data class BackupFile(
    val path: String,
    val size: Long,
    val sha256: String,
)

@Serializable
data class BackupManifest(
    val format: String,
    val formatVersion: Int,
    val exportedAt: Long,
    val applicationVersion: String,
    val sessionCount: Int,
    val files: List<BackupFile>,
)
