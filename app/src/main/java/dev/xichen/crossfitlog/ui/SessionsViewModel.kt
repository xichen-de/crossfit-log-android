package dev.xichen.crossfitlog.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import dev.xichen.crossfitlog.data.repository.WorkoutRepository

class SessionsViewModel(repository: WorkoutRepository) : ViewModel() {
    val sessions = repository.pagedSessions().cachedIn(viewModelScope)
}
