package dev.xichen.crossfitlog.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import dev.xichen.crossfitlog.data.local.PhotoStore
import dev.xichen.crossfitlog.ui.theme.CrossFitLogTheme
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test

class SessionListScreenTest {
    @get:Rule val compose = createComposeRule()

    private val loaded = LoadStates(LoadState.NotLoading(true), LoadState.NotLoading(true), LoadState.NotLoading(true))
    private val loading = LoadStates(LoadState.Loading, LoadState.NotLoading(false), LoadState.NotLoading(false))

    @Test fun emptyStateOffersFirstSession() {
        val store = PhotoStore(ApplicationProvider.getApplicationContext())
        compose.setContent { CrossFitLogTheme { SessionListScreen(flowOf(PagingData.empty(loaded)), store, {}, {}, {}, {}) } }
        compose.onNodeWithText("Your training log is ready").assertIsDisplayed()
        compose.onNodeWithText("Add first session").assertIsDisplayed()
    }

    @Test fun emptyStateWaitsForFirstPage() {
        val store = PhotoStore(ApplicationProvider.getApplicationContext())
        compose.setContent { CrossFitLogTheme { SessionListScreen(flowOf(PagingData.empty(loading)), store, {}, {}, {}, {}) } }
        compose.onNodeWithText("Your training log is ready").assertDoesNotExist()
    }
}
