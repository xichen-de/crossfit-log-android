package dev.xichen.crossfitlog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MovementSuggestionPolicyTest {
    private val candidates = listOf(
        "Pull-up", "Push-up", "Push press", "Push jerk", "Power clean", "Power snatch",
        "Squat", "Run", "Row", "Deadlift", "Wall sit", "Burpee",
    )

    @Test fun oneCharacterQueryShowsNothing() {
        assertTrue(rankMovementSuggestions("p", candidates).isEmpty())
    }

    @Test fun twoCharacterQueryOnlyUsesPrefixes() {
        val suggestions = rankMovementSuggestions("pu", candidates)
        assertTrue(suggestions.isNotEmpty())
        assertTrue(suggestions.all { it.lowercase().startsWith("pu") })
    }

    @Test fun usefulTypoStillFindsMovement() {
        assertEquals("Squat", rankMovementSuggestions("sqat", candidates).first())
    }

    @Test fun unrelatedWeakMatchesAreNotShown() {
        assertTrue(rankMovementSuggestions("zzzz", candidates).isEmpty())
    }

    @Test fun exactMovementShowsNoRedundantSuggestions() {
        assertTrue(rankMovementSuggestions("Push-up", candidates).isEmpty())
    }

    @Test fun resultCountIsCapped() {
        assertTrue(rankMovementSuggestions("pu", candidates).size <= 3)
    }
}
