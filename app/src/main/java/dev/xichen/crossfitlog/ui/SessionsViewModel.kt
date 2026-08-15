package dev.xichen.crossfitlog.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.xichen.crossfitlog.data.repository.WorkoutRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class SessionsViewModel(repository: WorkoutRepository) : ViewModel() {
    val sessions = repository.observeSessions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
