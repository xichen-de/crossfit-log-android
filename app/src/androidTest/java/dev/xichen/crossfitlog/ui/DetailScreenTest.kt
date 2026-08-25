package dev.xichen.crossfitlog.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import dev.xichen.crossfitlog.data.local.PhotoStore
import dev.xichen.crossfitlog.domain.MovementRecord
import dev.xichen.crossfitlog.domain.WorkoutSession
import dev.xichen.crossfitlog.ui.theme.CrossFitLogTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class DetailScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sessionWithoutPhotoDoesNotReservePhotoSpace() {
        val session = WorkoutSession(
            id = "session",
            sessionTime = 1_700_000_000_000,
            sessionNote = "",
            photoFilename = null,
            thumbnailFilename = null,
            createdAt = 1_700_000_000_000,
            updatedAt = 1_700_000_000_000,
            movements = listOf(MovementRecord("movement", "session", "Back squat", "back squat", "100 kg", "5x3", "", 0)),
        )
        val store = PhotoStore(ApplicationProvider.getApplicationContext())

        compose.setContent {
            CrossFitLogTheme {
                DetailScreen(MutableStateFlow(session), store, onBack = {}, onEdit = {}, onDuplicate = {})
            }
        }

        compose.onNodeWithText("Duplicate").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("No whiteboard photo").assertCountEquals(0)
        compose.onNodeWithText("Back squat").assertIsDisplayed()
    }
}
