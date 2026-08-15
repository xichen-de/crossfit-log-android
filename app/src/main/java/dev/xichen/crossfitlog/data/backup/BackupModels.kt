package dev.xichen.crossfitlog.data.backup

import dev.xichen.crossfitlog.domain.MovementRecord
import dev.xichen.crossfitlog.domain.WorkoutSession
import dev.xichen.crossfitlog.domain.normalizeMovementName
import kotlinx.serialization.Serializable

const val BACKUP_FORMAT = "crossfit-log"
const val BACKUP_VERSION = 1

@Serializable
data class BackupManifest(
    val format: String,
    val formatVersion: Int,
    val exportedAt: Long,
    val applicationVersion: String,
    val sessionCount: Int,
)

@Serializable
data class BackupMovement(
    val id: String, val name: String, val load: String, val result: String, val note: String, val displayOrder: Int,
)

@Serializable
data class BackupSession(
    val id: String,
    val sessionTime: Long,
    val sessionNote: String,
    val photoFilename: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val movements: List<BackupMovement>,
)

@Serializable data class BackupSessions(val sessions: List<BackupSession>)

fun WorkoutSession.toBackup() = BackupSession(
    id, sessionTime, sessionNote, photoFilename, createdAt, updatedAt,
    movements.map { BackupMovement(it.id, it.name, it.load, it.result, it.note, it.displayOrder) }
)

fun BackupSession.toDomain(photo: String?, thumbnail: String?) = WorkoutSession(
    id, sessionTime, sessionNote.trim(), photo, thumbnail, createdAt, updatedAt,
    movements.sortedBy { it.displayOrder }.map {
        MovementRecord(it.id, id, it.name.trim(), normalizeMovementName(it.name), it.load.trim(), it.result.trim(), it.note.trim(), it.displayOrder)
    }
)
