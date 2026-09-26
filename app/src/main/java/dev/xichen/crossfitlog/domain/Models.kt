package dev.xichen.crossfitlog.domain

import java.text.Normalizer
import java.util.Locale

data class WorkoutSession(
    val id: String,
    val sessionTime: Long,
    val sessionNote: String,
    val photoFilename: String?,
    val thumbnailFilename: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val movements: List<MovementRecord>,
)

data class MovementRecord(
    val id: String,
    val sessionId: String,
    val name: String,
    val normalizedName: String,
    val load: String,
    val result: String,
    val note: String,
    val displayOrder: Int,
)

data class MovementSearchResult(
    val movementId: String,
    val sessionId: String,
    val sessionTime: Long,
    val movementName: String,
    val load: String,
    val result: String,
    val note: String,
    val thumbnailFilename: String?,
)

private val combiningMarks = Regex("\\p{M}+")
private val nonAlphanumeric = Regex("[^a-z0-9]+")

fun normalizeMovementName(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
    .replace(combiningMarks, "")
    .lowercase(Locale.ROOT)
    .replace(nonAlphanumeric, " ")
    .trim()
fun cleanText(value: String): String = value.trim()
fun isMovementNameValid(value: String): Boolean = cleanText(value).isNotEmpty()
