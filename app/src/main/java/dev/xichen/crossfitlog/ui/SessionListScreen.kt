package dev.xichen.crossfitlog.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalContext
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import dev.xichen.crossfitlog.data.local.PhotoStore
import dev.xichen.crossfitlog.domain.MovementRecord
import dev.xichen.crossfitlog.domain.WorkoutSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionListScreen(
    sessionsFlow: Flow<PagingData<WorkoutSession>>, photoStore: PhotoStore,
    onNew: () -> Unit, onOpen: (String) -> Unit, onHistory: () -> Unit, onSettings: () -> Unit,
) {
    val sessions = sessionsFlow.collectAsLazyPagingItems()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopAppBar(title = { Text("CrossFit Log", style = MaterialTheme.typography.titleSmall) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background), actions = {
            IconButton(onClick = onHistory) { Icon(Icons.Outlined.History, "Movement history") }
            IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Backup and settings") }
        }) },
        floatingActionButton = { ExtendedFloatingActionButton(
            onClick = onNew,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("New session") },
        ) },
    ) { padding ->
        if (sessions.itemCount == 0) Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(Icons.Outlined.FitnessCenter, null, Modifier.padding(22.dp).size(34.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.height(24.dp))
                Text("Your training log is ready", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp)); Text("Photograph the whiteboard, log your results, and watch your progress build.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(24.dp)); Button(onClick = onNew) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("Add first session") }
            }
        } else LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp, 4.dp, 20.dp, 104.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Text("Training log", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${sessions.itemCount} session${if (sessions.itemCount == 1) "" else "s"} recorded",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                Text("Recent sessions", style = MaterialTheme.typography.titleMedium)
            }
            items(sessions.itemCount, key = sessions.itemKey { it.id }) { index ->
                val session = sessions[index]
                if (session != null) SessionJournalCard(session, photoStore) { onOpen(session.id) }
            }
        }
    }
}

@Composable
private fun SessionJournalCard(session: WorkoutSession, photoStore: PhotoStore, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shadowElevation = 2.dp,
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            LocalPhoto(
                photoStore.thumbnailFile(session.thumbnailFilename),
                "Whiteboard from ${formatDate(session.sessionTime)}",
                Modifier.width(116.dp).fillMaxHeight(),
            )
            Column(Modifier.weight(1f).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatDay(session.sessionTime).uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    Icon(Icons.Outlined.ChevronRight, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(9.dp))
                Text(session.movements.firstOrNull()?.name ?: "Training session", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (session.movements.size > 1) {
                    Spacer(Modifier.height(3.dp))
                    Text("+ ${session.movements.size - 1} more movement${if (session.movements.size == 2) "" else "s"}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val first = session.movements.firstOrNull()
                if (first != null && (first.load.isNotBlank() || first.result.isNotBlank())) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        if (first.load.isNotBlank()) SessionValue("LOAD", first.load, Modifier.weight(1f))
                        if (first.result.isNotBlank()) SessionValue("RESULT", first.result, Modifier.weight(1f))
                    }
                }
                if (session.sessionNote.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(session.sessionNote, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SessionValue(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 760)
@Composable
private fun SessionListPreview() {
    val sample = WorkoutSession(
        id = "preview-session", sessionTime = 1_754_658_000_000, sessionNote = "Felt strong today",
        photoFilename = null, thumbnailFilename = null, createdAt = 1_754_658_000_000, updatedAt = 1_754_658_000_000,
        movements = listOf(
            MovementRecord("one", "preview-session", "Back Squat", "back squat", "62.5 kg", "5-5-3-3-1", "", 0),
            MovementRecord("two", "preview-session", "Pull-up", "pull-up", "blue band", "5 × 2", "strict", 1),
        ),
    )
    dev.xichen.crossfitlog.ui.theme.CrossFitLogTheme {
        SessionListScreen(flowOf(PagingData.from(listOf(sample))), PhotoStore(LocalContext.current), {}, {}, {}, {})
    }
}
