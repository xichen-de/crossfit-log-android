package dev.xichen.crossfitlog.ui

import dev.xichen.crossfitlog.domain.MovementRecord
import dev.xichen.crossfitlog.domain.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DuplicateSessionTest {
    @Test fun duplicateCreatesIndependentDraftWithoutPhoto() {
        val source = WorkoutSession(
            id = "source-session",
            sessionTime = 1L,
            sessionNote = "7+18 scaled",
            photoFilename = "whiteboard.jpg",
            thumbnailFilename = "whiteboard-thumb.jpg",
            createdAt = 2L,
            updatedAt = 3L,
            movements = listOf(
                MovementRecord("movement-1", "source-session", "Back squat", "back squat", "100 kg", "5x3", "RPE 8", 0),
                MovementRecord("movement-2", "source-session", "Pull-up", "pull up", "", "8 reps", "Green band", 1),
            ),
        )

        val draft = source.toDuplicateDraft(now = 10_000L)

        assertNotEquals(source.id, draft.id)
        assertEquals(10_000L, draft.sessionTime)
        assertEquals(10_000L, draft.createdAt)
        assertEquals(source.sessionNote, draft.sessionNote)
        assertNull(draft.photoFilename)
        assertNull(draft.thumbnailFilename)
        assertEquals(source.movements.map { it.name }, draft.movements.map { it.name })
        assertEquals(source.movements.map { it.load }, draft.movements.map { it.load })
        assertEquals(source.movements.map { it.result }, draft.movements.map { it.result })
        assertEquals(source.movements.map { it.note }, draft.movements.map { it.note })
        draft.movements.zip(source.movements).forEach { (copy, original) ->
            assertNotEquals(original.id, copy.id)
        }
    }
}
