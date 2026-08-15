package dev.xichen.crossfitlog.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import dev.xichen.crossfitlog.data.local.PhotoStore
import dev.xichen.crossfitlog.ui.theme.CrossFitLogTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class SessionListScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun emptyStateOffersFirstSession() {
        val store = PhotoStore(ApplicationProvider.getApplicationContext())
        compose.setContent { CrossFitLogTheme { SessionListScreen(MutableStateFlow(emptyList()), store, {}, {}, {}, {}) } }
        compose.onNodeWithText("Your training log is ready").assertIsDisplayed()
        compose.onNodeWithText("Add first session").assertIsDisplayed()
    }
}
