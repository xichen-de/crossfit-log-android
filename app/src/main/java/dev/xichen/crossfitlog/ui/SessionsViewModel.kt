package dev.xichen.crossfitlog.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import dev.xichen.crossfitlog.data.repository.WorkoutRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class SessionsViewModel(repository: WorkoutRepository) : ViewModel() {
    val sessions = repository.pagedSessions().cachedIn(viewModelScope)

    /** Total stored sessions; the paged list only knows how many pages it has loaded so far. */
    val sessionCount = repository.observeSessionCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
