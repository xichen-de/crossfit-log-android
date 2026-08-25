package dev.xichen.crossfitlog.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.xichen.crossfitlog.CrossFitLogApplication
import dev.xichen.crossfitlog.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SessionFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun clearData() {
        val app = compose.activity.application as CrossFitLogApplication
        runBlocking { app.database.clearAllTables() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Add first session").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun createEditSearchAndDeleteSession() {
        compose.onNodeWithText("Add first session").performClick()
        compose.onNodeWithTag("movement-name-0").performTextInput("Back Squat")
        compose.onNodeWithContentDescription("Close editor").performClick()
        compose.onNodeWithText("Discard this session?").assertIsDisplayed()
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithText("Add movement").performClick()
        compose.onNodeWithTag("movement-name-1").performTextInput("Pull-up")
        compose.onNodeWithText("Save").performClick()

        compose.waitUntil(5_000) { compose.onAllNodesWithText("Back Squat").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Duplicate").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Close editor").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Close editor").performClick()
        compose.onNodeWithText("Discard this session?").assertIsDisplayed()
        compose.onNodeWithText("Discard").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Duplicate").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Edit session").performClick()
        compose.onNodeWithTag("movement-name-0").performTextReplacement("Front Squat")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Front Squat").fetchSemanticsNodes().isNotEmpty() }

        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithContentDescription("Movement history").performClick()
        compose.onNodeWithText("Training day").performClick()
        compose.onNodeWithTag("training-day-picker").assertIsDisplayed()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Front Squat").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Front Squat").performClick()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("training-day-picker").assertIsDisplayed()
        compose.onNodeWithText("Movement").performClick()
        compose.onNodeWithTag("movement-search").performTextInput("squat")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Front Squat").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Front Squat").performClick()
        compose.onNodeWithContentDescription("Edit session").performClick()
        compose.onNodeWithText("Delete session").performScrollTo().performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Your training log is ready").fetchSemanticsNodes().isNotEmpty() }
    }
}
