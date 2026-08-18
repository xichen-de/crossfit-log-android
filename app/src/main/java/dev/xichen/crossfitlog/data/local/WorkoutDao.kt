package dev.xichen.crossfitlog.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class WorkoutDao {
    @Transaction
    @Query("SELECT * FROM workout_sessions ORDER BY session_time DESC, created_at DESC")
    abstract fun observeSessions(): Flow<List<SessionWithMovements>>

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    abstract fun observeSession(id: String): Flow<SessionWithMovements?>

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    abstract suspend fun getSession(id: String): SessionWithMovements?

    @Transaction
    @Query("SELECT * FROM workout_sessions ORDER BY session_time DESC, created_at DESC")
    abstract suspend fun getAllSessions(): List<SessionWithMovements>

    @Query("SELECT EXISTS(SELECT 1 FROM workout_sessions WHERE id = :id)")
    abstract suspend fun sessionExists(id: String): Boolean

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertSession(session: WorkoutSessionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertMovements(movements: List<MovementRecordEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertSession(session: WorkoutSessionEntity)

    @Query("DELETE FROM movement_records WHERE session_id = :sessionId")
    abstract suspend fun deleteMovements(sessionId: String)

    @Delete
    abstract suspend fun deleteSessionEntity(session: WorkoutSessionEntity)

    @Transaction
    open suspend fun insertComplete(session: WorkoutSessionEntity, movements: List<MovementRecordEntity>) {
        insertSession(session)
        insertMovements(movements)
    }

    @Transaction
    open suspend fun replaceComplete(session: WorkoutSessionEntity, movements: List<MovementRecordEntity>) {
        upsertSession(session)
        deleteMovements(session.id)
        insertMovements(movements)
    }

    @Transaction
    open suspend fun deleteComplete(session: WorkoutSessionEntity) {
        deleteSessionEntity(session)
    }

    @Query(
        """SELECT m.session_id, s.session_time, m.name AS movement_name,
            m.load, m.result, m.note, s.thumbnail_filename
            FROM movement_records m JOIN workout_sessions s ON s.id = m.session_id
            WHERE m.normalized_name LIKE '%' || :normalizedQuery || '%'
            ORDER BY s.session_time DESC, m.display_order ASC
            LIMIT 50"""
    )
    abstract fun searchMovements(normalizedQuery: String): Flow<List<MovementSearchRow>>

    @Query(
        """SELECT normalized_name, MIN(name) AS display_name FROM movement_records
            GROUP BY normalized_name ORDER BY COUNT(*) DESC, display_name ASC"""
    )
    abstract fun observeMovementCandidates(): Flow<List<MovementSuggestionRow>>

    @Query(
        """SELECT normalized_name, MIN(name) AS display_name FROM movement_records
            GROUP BY normalized_name ORDER BY COUNT(*) DESC, display_name ASC"""
    )
    abstract suspend fun movementCandidates(): List<MovementSuggestionRow>
}
