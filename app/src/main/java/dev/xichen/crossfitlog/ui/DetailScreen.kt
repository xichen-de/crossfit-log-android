package dev.xichen.crossfitlog.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.xichen.crossfitlog.data.local.PhotoStore
import dev.xichen.crossfitlog.domain.WorkoutSession
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(sessionFlow: StateFlow<WorkoutSession?>, photoStore: PhotoStore, onBack: () -> Unit, onEdit: () -> Unit) {
    val session by sessionFlow.collectAsState()
    var showPhotoViewer by remember { mutableStateOf(false) }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { TopAppBar(title = { Text("Session") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } }, actions = {
        FilledTonalButton(onClick = onEdit, modifier = Modifier.padding(end = 8.dp)) { Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Edit") }
    }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)) }) { padding ->
        val value = session
        if (value == null) Box(Modifier.fillMaxSize().padding(padding)) { CircularProgressIndicator(Modifier.padding(32.dp)) }
        else LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp, 4.dp, 20.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            item {
                Column {
                    Text("TRAINING SESSION", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text(formatDay(value.sessionTime), style = MaterialTheme.typography.displaySmall)
                    Spacer(Modifier.height(5.dp))
                    Text("${formatDate(value.sessionTime).substringAfter("· ")}  ·  ${value.movements.size} movement${if (value.movements.size == 1) "" else "s"}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (value.sessionNote.isNotBlank()) {
                        Spacer(Modifier.height(14.dp))
                        Text("“${value.sessionNote}”", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                val photoFile = photoStore.photoFile(value.photoFilename)
                Box(
                    Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(20.dp))
                        .clickable(enabled = photoFile != null) { showPhotoViewer = true },
                ) {
                    LocalPhoto(photoFile, "Whiteboard photo", Modifier.fillMaxSize(), ContentScale.Fit)
                    if (photoFile != null) Surface(
                        modifier = Modifier.align(androidx.compose.ui.Alignment.BottomEnd).padding(10.dp),
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.scrim.copy(alpha = .65f),
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Fullscreen, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.inverseOnSurface)
                            Spacer(Modifier.width(5.dp))
                            Text("Tap to zoom", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.inverseOnSurface)
                        }
                    }
                }
            }
            item {
                Text("MOVEMENTS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(value.movements.size) { index ->
                val movement = value.movements[index]
                Surface(
                    shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shadowElevation = 2.dp,
                ) {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        Text("${index + 1}".padStart(2, '0'), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(5.dp))
                        Text(movement.name, style = MaterialTheme.typography.titleLarge)
                        if (movement.load.isNotBlank() || movement.result.isNotBlank()) {
                            Spacer(Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                DetailMetric("LOAD", movement.load.ifBlank { "—" }, Modifier.weight(1f))
                                DetailMetric("RESULT", movement.result.ifBlank { "—" }, Modifier.weight(1f))
                            }
                        }
                        if (movement.note.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                    Text("NOTE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(3.dp))
                                    Text(movement.note, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showPhotoViewer) FullscreenPhotoViewer(photoStore.photoFile(session?.photoFilename), "Whiteboard photo", onDismiss = { showPhotoViewer = false })
}

@Composable private fun DetailMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(3.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}
