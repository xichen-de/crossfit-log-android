package dev.xichen.crossfitlog.data.repository

import dev.xichen.crossfitlog.data.local.MovementRecordEntity
import dev.xichen.crossfitlog.data.local.SessionWithMovements
import dev.xichen.crossfitlog.data.local.WorkoutDao
import dev.xichen.crossfitlog.data.local.WorkoutSessionEntity
import dev.xichen.crossfitlog.domain.MovementMatcher
import dev.xichen.crossfitlog.domain.MovementRecord
import dev.xichen.crossfitlog.domain.MovementSearchResult
import dev.xichen.crossfitlog.domain.WorkoutSession
import dev.xichen.crossfitlog.domain.mergeMovementCandidates
import dev.xichen.crossfitlog.domain.normalizeMovementName
import dev.xichen.crossfitlog.domain.rankMovementSuggestions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class WorkoutRepository(private val dao: WorkoutDao, private val movementMatcher: MovementMatcher = MovementMatcher()) {
    fun observeSessions(): Flow<List<WorkoutSession>> = dao.observeSessions().map { rows -> rows.map(::toDomain) }
    fun observeSession(id: String): Flow<WorkoutSession?> = dao.observeSession(id).map { it?.let(::toDomain) }
    suspend fun getSession(id: String): WorkoutSession? = dao.getSession(id)?.let(::toDomain)
    suspend fun getAllSessions(): List<WorkoutSession> = dao.getAllSessions().map(::toDomain)
    suspend fun exists(id: String): Boolean = dao.sessionExists(id)

    fun search(query: String): Flow<List<MovementSearchResult>> {
        val normalizedQuery = normalizeMovementName(query)
        if (normalizedQuery.isBlank()) return flowOf(emptyList())
        return dao.searchMovements(normalizedQuery).map { rows ->
            rows.map { MovementSearchResult(it.movementId, it.sessionId, it.sessionTime, it.movementName, it.load, it.result, it.note, it.thumbnailFilename) }
        }
    }

    /** Merged, catalog-resolved candidate names; only changes when the underlying history does, not per query. */
    fun observeMovementCandidateNames(): Flow<List<String>> =
        dao.observeMovementCandidates().map { rows ->
            withContext(Dispatchers.Default) { mergeMovementCandidates(rows.map { it.displayName }) }
        }

    fun rankSuggestions(prefix: String, candidates: List<String>): List<String> {
        return rankMovementSuggestions(prefix, candidates, movementMatcher)
    }

    suspend fun movementCandidates(): List<String> = withContext(Dispatchers.Default) {
        mergeMovementCandidates(dao.movementCandidates().map { it.displayName })
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
