package dev.xichen.crossfitlog.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutDaoTest {
    private lateinit var db: CrossFitDatabase
    private lateinit var dao: WorkoutDao

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), CrossFitDatabase::class.java).build()
        dao = db.workoutDao()
    }
    @After fun close() = db.close()

    @Test fun sessionsAndMovementsAreOrderedAndSearchIsPartial() = runTest {
        val old = WorkoutSessionEntity("old", 100, "", null, null, 1, 1)
        val recent = WorkoutSessionEntity("recent", 200, "", null, null, 2, 2)
        dao.insertComplete(old, listOf(MovementRecordEntity("m1", "old", "Front Squat", "front squat", "", "", "", 0)))
        dao.insertComplete(recent, listOf(
            MovementRecordEntity("m3", "recent", "Pull-up", "pull-up", "", "", "", 1),
            MovementRecordEntity("m2", "recent", "Back Squat", "back squat", "", "", "", 0),
        ))
        val sessions = dao.observeSessions().first()
        assertEquals(listOf("recent", "old"), sessions.map { it.session.id })
        assertEquals(listOf("Back Squat", "Pull-up"), sessions.first().movements.sortedBy { it.displayOrder }.map { it.name })
        assertEquals(listOf("recent", "old"), dao.searchMovements("squat").first().map { it.sessionId })
        assertEquals(1, dao.searchMovements("BACK").first().size)
    }

    @Test fun deletingSessionCascadesMovements() = runTest {
        val session = WorkoutSessionEntity("one", 1, "", null, null, 1, 1)
        dao.insertComplete(session, listOf(MovementRecordEntity("m", "one", "WOD", "wod", "", "", "", 0)))
        dao.deleteComplete(session)
        assertTrue(dao.searchMovements("").first().isEmpty())
    }
}
