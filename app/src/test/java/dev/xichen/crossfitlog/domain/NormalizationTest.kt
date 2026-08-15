package dev.xichen.crossfitlog.domain

import org.junit.Assert.*
import org.junit.Test

class NormalizationTest {
    @Test fun normalizationTrimsAndIgnoresCaseAndPunctuation() {
        assertEquals("back squat", normalizeMovementName("  BACK Squat  "))
        assertEquals(normalizeMovementName("Back Squat"), normalizeMovementName("back squat"))
        assertEquals("clean jerk", normalizeMovementName("Clean & Jerk"))
        assertEquals("deja vu", normalizeMovementName("déjà-vu"))
    }

    @Test fun emptyMovementNamesAreRejected() {
        assertFalse(isMovementNameValid("  \n "))
        assertTrue(isMovementNameValid("WOD"))
    }

    @Test fun surroundingWhitespaceIsRemoved() { assertEquals("62.5 kg", cleanText(" 62.5 kg \n")) }
}
