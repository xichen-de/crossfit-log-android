package dev.xichen.crossfitlog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhiteboardMovementSuggesterTest {
    private val candidates = listOf("Pull up", "Push up", "Squat", "Deadlift", "Wall sit", "Row", "Run")
    private val suggester = WhiteboardMovementSuggester()

    @Test fun extractsMovementsFromNoisyLines() {
        val recognized = text("10 Pull Ups", "3 rounds Wall Sit", "24 cal Row", "sqat", "dead lift")
        val result = suggester.suggest(recognized, candidates)
        assertTrue(result.containsAll(listOf("Pull up", "Wall sit", "Row", "Squat", "Deadlift")))
    }

    @Test fun builtInCatalogWorksWithoutMovementHistory() {
        val result = suggester.suggest(text("10 pull ups", "15 wall balls", "sqat", "500 m row"), MovementCatalog.names)
        assertTrue(result.containsAll(listOf("Pull-up", "Wall ball", "Squat", "Row")))
    }

    @Test fun acceptsCommonOcrLetterConfusions() {
        val result = suggester.suggest(text("PuII up", "Puil up", "WalI sit"), candidates)
        assertTrue(result.contains("Pull up"))
        assertTrue(result.contains("Wall sit"))
    }

    @Test fun conservativeThresholdRejectsNoiseAndShortAmbiguity() {
        assertTrue(suggester.suggest(text("random heading"), candidates).isEmpty())
        assertFalse(suggester.suggest(text("Run"), listOf("Row")).contains("Row"))
        assertFalse(suggester.suggest(text("Row"), listOf("Run")).contains("Run"))
    }

    @Test fun removesDuplicateOcrLinesElementsAndExistingMovements() {
        val recognized = RecognizedWhiteboardText(
            listOf(
                RecognizedWhiteboardLine("Pull up", listOf("Pull", "up")),
                RecognizedWhiteboardLine("PULL-UPS", listOf("Pull", "ups")),
                RecognizedWhiteboardLine("Squat", listOf("Squat", "Squat")),
            )
        )
        assertEquals(listOf("Squat"), suggester.suggest(recognized, candidates, alreadyPresent = listOf("pull-up")))
    }

    @Test fun selectedSuggestionsAreDeduplicatedAgainstSessionAndEachOther() {
        assertEquals(
            listOf("Squat", "Row"),
            newMovementSuggestions(listOf("Pull up"), listOf("pull-up", "Squat", "squat", "Row")),
        )
    }

    private fun text(vararg lines: String) = RecognizedWhiteboardText(lines.map { RecognizedWhiteboardLine(it) })
}
