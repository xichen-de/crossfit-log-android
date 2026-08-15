package dev.xichen.crossfitlog.data.repository

import dev.xichen.crossfitlog.data.local.*
import dev.xichen.crossfitlog.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class WorkoutRepository(private val dao: WorkoutDao) {
    fun observeSessions(): Flow<List<WorkoutSession>> = dao.observeSessions().map { rows -> rows.map(::toDomain) }
    fun observeSession(id: String): Flow<WorkoutSession?> = dao.observeSession(id).map { it?.let(::toDomain) }
    suspend fun getSession(id: String): WorkoutSession? = dao.getSession(id)?.let(::toDomain)
    suspend fun getAllSessions(): List<WorkoutSession> = dao.getAllSessions().map(::toDomain)
    suspend fun exists(id: String): Boolean = dao.sessionExists(id)

    fun search(query: String): Flow<List<MovementSearchResult>> {
        val normalizedQuery = normalizeMovementName(query)
        if (normalizedQuery.isBlank()) return flowOf(emptyList())
        return dao.searchMovements(normalizedQuery).map { rows ->
            rows.map { MovementSearchResult(it.sessionId, it.sessionTime, it.movementName, it.load, it.result, it.note, it.thumbnailFilename) }
        }
    }

    fun suggestions(prefix: String): Flow<List<String>> {
        val normalizedPrefix = normalizeMovementName(prefix)
        if (normalizedPrefix.isBlank()) return flowOf(emptyList())
        return dao.movementSuggestions(normalizedPrefix).map { rows -> rows.map { it.displayName } }
    }

    suspend fun create(session: WorkoutSession) = dao.insertComplete(session.toEntity(), session.movements.map { it.toEntity() })
    suspend fun update(session: WorkoutSession) = dao.replaceComplete(session.toEntity(), session.movements.map { it.toEntity() })
    suspend fun delete(session: WorkoutSession) = dao.deleteComplete(session.toEntity())

    private fun toDomain(value: SessionWithMovements) = WorkoutSession(
        id = value.session.id,
        sessionTime = value.session.sessionTime,
        sessionNote = value.session.sessionNote,
        photoFilename = value.session.photoFilename,
        thumbnailFilename = value.session.thumbnailFilename,
        createdAt = value.session.createdAt,
        updatedAt = value.session.updatedAt,
        movements = value.movements.sortedBy { it.displayOrder }.map {
            MovementRecord(it.id, it.sessionId, it.name, it.normalizedName, it.load, it.result, it.note, it.displayOrder)
        },
    )

    private fun WorkoutSession.toEntity() = WorkoutSessionEntity(id, sessionTime, sessionNote, photoFilename, thumbnailFilename, createdAt, updatedAt)
    private fun MovementRecord.toEntity() = MovementRecordEntity(id, sessionId, name, normalizedName, load, result, note, displayOrder)
}
