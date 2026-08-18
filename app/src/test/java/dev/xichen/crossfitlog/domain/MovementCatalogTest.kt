package dev.xichen.crossfitlog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MovementCatalogTest {
    @Test fun catalogHasUniqueNormalizedCanonicalNames() {
        val normalized = MovementCatalog.names.map(::normalizeMovementName)
        assertEquals(normalized.size, normalized.toSet().size)
        assertTrue(MovementCatalog.names.size >= 100)
        assertTrue(MovementCatalog.names.containsAll(listOf(
            "Squat", "Air squat", "Clean and jerk", "Pull-up", "Wall ball", "Run", "Row", "Ski erg",
        )))
    }

    @Test fun catalogSpellingWinsForExactNormalizedAlias() {
        val merged = mergeMovementCandidates(listOf("Pull up", "My movement"), listOf("Pull-up", "Air squat"))
        assertEquals(listOf("Pull-up", "My movement", "Air squat"), merged)
    }

    @Test fun confidentUnambiguousTyposResolveToCatalogSpelling() {
        assertEquals(listOf("Squat"), mergeMovementCandidates(listOf("sqat"), listOf("Squat")))
        assertEquals(listOf("Wall sit"), mergeMovementCandidates(listOf("WalI sit"), listOf("Wall sit")))
        assertEquals(listOf("Deadlift"), mergeMovementCandidates(listOf("Dead lift"), listOf("Deadlift")))
    }

    @Test fun weakOrAmbiguousHistoryNamesRemainCustom() {
        assertEquals(
            listOf("Sandbag get-up", "Sandbag clean", "Turkish get-up"),
            mergeMovementCandidates(listOf("Sandbag get-up"), listOf("Sandbag clean", "Turkish get-up")),
        )
        assertEquals(
            listOf("Power", "Power clean", "Power snatch"),
            mergeMovementCandidates(listOf("Power"), listOf("Power clean", "Power snatch")),
        )
    }

    @Test fun shortNamesAreNeverFuzzyCollapsed() {
        assertEquals(listOf("Ruw", "Run", "Row"), mergeMovementCandidates(listOf("Ruw"), listOf("Run", "Row")))
    }
}
