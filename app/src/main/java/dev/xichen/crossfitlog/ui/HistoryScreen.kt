package dev.xichen.crossfitlog.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.xichen.crossfitlog.data.local.PhotoStore
import dev.xichen.crossfitlog.domain.MovementSearchResult
import dev.xichen.crossfitlog.domain.WorkoutSession
import java.time.Instant
import java.time.ZoneId

private enum class HistoryMode { Movement, TrainingDay }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: HistoryViewModel, photoStore: PhotoStore, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val query by vm.query.collectAsState()
    val results by vm.results.collectAsState()
    val sessions by vm.sessions.collectAsState()
    var mode by remember { mutableStateOf(HistoryMode.Movement) }
    var selectedDay by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var dayInitialized by remember { mutableStateOf(false) }
    val context = LocalContext.current
    LaunchedEffect(sessions) {
        if (!dayInitialized && sessions.isNotEmpty()) {
            selectedDay = sessions.first().sessionTime
            dayInitialized = true
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        TopAppBar(title = { Text("History") }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background), navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = mode.ordinal, containerColor = MaterialTheme.colorScheme.background, divider = {}) {
                Tab(mode == HistoryMode.Movement, { mode = HistoryMode.Movement }, text = { Text("Movement") })
                Tab(mode == HistoryMode.TrainingDay, { mode = HistoryMode.TrainingDay }, text = { Text("Training day") })
            }
            when (mode) {
                HistoryMode.Movement -> MovementHistory(query, { vm.query.value = it }, results, photoStore, onOpen)
                HistoryMode.TrainingDay -> {
                    val selectedSessions = remember(sessions, selectedDay) { sessionsOnDay(sessions, selectedDay) }
                    TrainingDayHistory(selectedDay, selectedSessions, photoStore, onChooseDay = {
                        val date = Instant.ofEpochMilli(selectedDay).atZone(ZoneId.systemDefault()).toLocalDate()
                        val dialog = DatePickerDialog(context, { _, year, month, day ->
                            selectedDay = java.time.LocalDate.of(year, month + 1, day)
                                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        }, date.year, date.monthValue - 1, date.dayOfMonth)
                        dialog.datePicker.maxDate = System.currentTimeMillis()
                        sessions.lastOrNull()?.let {
                            dialog.datePicker.minDate = localDate(it.sessionTime).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        }
                        dialog.show()
                    }, onOpen = onOpen)
                }
            }
        }
    }
}

@Composable
private fun MovementHistory(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<MovementSearchResult>,
    photoStore: PhotoStore,
    onOpen: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            query, onQueryChange, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp).testTag("movement-search"),
            placeholder = { Text("Search movements") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true,
            shape = RoundedCornerShape(16.dp),
        )
        if (results.isEmpty()) Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                if (query.isBlank()) "Start typing to browse your movement history." else "No movements match “${query.trim()}”.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(results) { result ->
                Card(Modifier.fillMaxWidth().clickable { onOpen(result.sessionId) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = CardDefaults.outlinedCardBorder()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        LocalPhoto(photoStore.thumbnailFile(result.thumbnailFilename), "Session whiteboard", Modifier.size(76.dp).clip(RoundedCornerShape(12.dp)))
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(result.movementName, fontWeight = FontWeight.Bold)
                            Text(formatDate(result.sessionTime), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            listOf("Load" to result.load, "Result" to result.result, "Note" to result.note)
                                .filter { it.second.isNotBlank() }
                                .forEach { Text("${it.first}: ${it.second}", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrainingDayHistory(
    selectedDay: Long,
    sessions: List<WorkoutSession>,
    photoStore: PhotoStore,
    onChooseDay: () -> Unit,
    onOpen: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        OutlinedButton(onClick = onChooseDay, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp).testTag("training-day-picker")) {
            Icon(Icons.Outlined.CalendarMonth, null)
            Spacer(Modifier.width(8.dp))
            Text(formatDay(selectedDay))
        }
        if (sessions.isEmpty()) Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("No training sessions were saved on this day.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(sessions, key = { it.id }) { session -> TrainingDayCard(session, photoStore) { onOpen(session.id) } }
        }
    }
}

@Composable
private fun TrainingDayCard(session: WorkoutSession, photoStore: PhotoStore, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = CardDefaults.outlinedCardBorder()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            LocalPhoto(photoStore.thumbnailFile(session.thumbnailFilename), "Session whiteboard", Modifier.size(84.dp).clip(RoundedCornerShape(12.dp)))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(formatDate(session.sessionTime), style = MaterialTheme.typography.labelLarge)
                Text(
                    session.movements.joinToString(" · ") { it.name },
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (session.sessionNote.isNotBlank()) Text(
                    session.sessionNote,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
