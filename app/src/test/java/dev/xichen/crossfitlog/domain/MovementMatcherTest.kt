package dev.xichen.crossfitlog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MovementMatcherTest {
    private val matcher = MovementMatcher()
    private val movements = listOf("Pull up", "Push up", "Squat", "Deadlift", "Wall sit", "Row", "Run")

    @Test fun normalizationHandlesSpacingPunctuationPluralAndCase() {
        assertEquals("pull up", normalizeForMatching(" pull-up "))
        assertEquals("pull up", normalizeForMatching("Pull Ups"))
        assertEquals("pull up", normalizeForMatching("PULL UP"))
    }

    @Test fun ranksCommonMovementVariantsFirst() {
        listOf("pull up", "pull-up", "pullup", "PULL UP").forEach { query ->
            assertEquals("Pull up", matcher.rank(query, movements).first().movement)
        }
        assertEquals("Squat", matcher.rank("sqat", movements).first().movement)
        assertEquals("Deadlift", matcher.rank("dead lift", movements).first().movement)
        assertEquals("Wall sit", matcher.rank("wall-sit", movements).first().movement)
        assertEquals("Push up", matcher.rank("pushup", movements).first().movement)
    }

    @Test fun rankingDoesNotTreatRowAndRunAsExact() {
        val run = matcher.rank("row", listOf("Run")).single()
        assertTrue(!run.exact && run.score < 0.90)
    }
}
