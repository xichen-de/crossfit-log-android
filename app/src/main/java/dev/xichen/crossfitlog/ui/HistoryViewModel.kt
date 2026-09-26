package dev.xichen.crossfitlog.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.xichen.crossfitlog.data.repository.WorkoutRepository
import dev.xichen.crossfitlog.domain.WorkoutSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class TrainingDayState(
    val date: LocalDate,
    val sessions: List<WorkoutSession>,
    val previousTrainingDay: LocalDate?,
    val nextTrainingDay: LocalDate?,
    val earliestTrainingDay: LocalDate?,
)

@OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val savedState: SavedStateHandle,
    private val repository: WorkoutRepository,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    val query = MutableStateFlow("")
    val results = query.debounce(150).flatMapLatest { repository.search(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val selectedEpochDay = savedState.getStateFlow<Long?>(SELECTED_DAY_KEY, null)

    init {
        // Open the training-day view on the most recent session rather than on an empty today.
        if (selectedEpochDay.value == null) viewModelScope.launch {
            val latest = repository.latestSessionTime()?.let { localDate(it, zoneId) } ?: LocalDate.now(zoneId)
            if (selectedEpochDay.value == null) savedState[SELECTED_DAY_KEY] = latest.toEpochDay()
        }
    }

    /** Only the selected day's sessions are loaded; neighbouring days come from MIN/MAX lookups. */
    val trainingDay: StateFlow<TrainingDayState?> = selectedEpochDay.filterNotNull().map(LocalDate::ofEpochDay)
        .flatMapLatest { date ->
            val day = dayBounds(date, zoneId)
            combine(
                repository.observeSessionsBetween(day.first, day.last),
                repository.observeLatestSessionTimeBefore(day.first),
                repository.observeEarliestSessionTimeAfter(day.last),
                repository.observeEarliestSessionTimeAfter(Long.MIN_VALUE),
            ) { sessions, previous, next, earliest ->
                TrainingDayState(
                    date = date,
                    sessions = sessions,
                    previousTrainingDay = previous?.let { localDate(it, zoneId) },
                    nextTrainingDay = next?.let { localDate(it, zoneId) },
                    earliestTrainingDay = earliest?.let { localDate(it, zoneId) },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectDay(date: LocalDate) {
        savedState[SELECTED_DAY_KEY] = date.toEpochDay()
    }

    private companion object {
        const val SELECTED_DAY_KEY = "selectedEpochDay"
    }
}
