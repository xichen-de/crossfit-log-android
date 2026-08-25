package dev.xichen.crossfitlog.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "workout_sessions")
data class WorkoutSessionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "session_time") val sessionTime: Long,
    @ColumnInfo(name = "session_note") val sessionNote: String,
    @ColumnInfo(name = "photo_filename") val photoFilename: String?,
    @ColumnInfo(name = "thumbnail_filename") val thumbnailFilename: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "movement_records",
    foreignKeys = [ForeignKey(
        entity = WorkoutSessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["session_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("session_id"), Index("normalized_name")],
)
data class MovementRecordEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    val name: String,
    @ColumnInfo(name = "normalized_name") val normalizedName: String,
    val load: String,
    val result: String,
    val note: String,
    @ColumnInfo(name = "display_order") val displayOrder: Int,
)

data class SessionWithMovements(
    @Embedded val session: WorkoutSessionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "session_id",
        entity = MovementRecordEntity::class,
    )
    val movements: List<MovementRecordEntity>,
)

data class MovementSearchRow(
    @ColumnInfo(name = "movement_id") val movementId: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "session_time") val sessionTime: Long,
    @ColumnInfo(name = "movement_name") val movementName: String,
    val load: String,
    val result: String,
    val note: String,
    @ColumnInfo(name = "thumbnail_filename") val thumbnailFilename: String?,
)

data class MovementSuggestionRow(
    @ColumnInfo(name = "normalized_name") val normalizedName: String,
    @ColumnInfo(name = "display_name") val displayName: String,
)
