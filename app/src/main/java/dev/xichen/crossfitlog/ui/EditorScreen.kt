package dev.xichen.crossfitlog.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import dev.xichen.crossfitlog.data.local.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EditorScreen(vm: EditorViewModel, photoStore: PhotoStore, onBack: () -> Unit, onSaved: () -> Unit, onDeleted: () -> Unit) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var cameraOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var photoExpanded by rememberSaveable { mutableStateOf(true) }
    var showPhotoViewer by rememberSaveable { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { vm.importPhoto(context.contentResolver, it) } }
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }
    LaunchedEffect(state.draft.photoFilename) {
        if (state.draft.photoFilename != null) photoExpanded = true else showPhotoViewer = false
    }
    if (cameraOpen) {
        BackHandler { cameraOpen = false }
        CameraCaptureScreen(photoStore, onCancel = { cameraOpen = false }, onCaptured = { file ->
            cameraOpen = false; vm.importPhoto(context.contentResolver, Uri.fromFile(file))
        }, onError = { message -> cameraOpen = false; vm.showError(message) })
        return
    }
    fun requestClose() {
        if (state.hasUnsavedChanges) confirmDiscard = true else onBack()
    }
    BackHandler(onBack = ::requestClose)
    if (confirmDiscard) AlertDialog(
        onDismissRequest = { confirmDiscard = false },
        title = { Text("Discard this session?") },
        text = { Text("Your unsaved changes will be lost.") },
        confirmButton = {
            TextButton(onClick = onBack) { Text("Discard", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
    )
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete this session?") }, text = { Text("The workout record and its managed photos will be permanently removed.") }, confirmButton = {
        TextButton(onClick = { confirmDelete = false; vm.delete(onDeleted) }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
    }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } })
    state.ocrSuggestions?.let { suggestions ->
        var selected by remember(suggestions) { mutableStateOf(suggestions.toSet()) }
        AlertDialog(
            onDismissRequest = vm::dismissOcrSuggestions,
            title = { Text("Suggested movements") },
            text = {
                if (suggestions.isEmpty()) Text("No confident movement matches were found. Try a clearer photo or add movements manually.")
                else Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                    Text("Review the movements found on the whiteboard.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    suggestions.forEach { suggestion ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                selected = if (suggestion in selected) selected - suggestion else selected + suggestion
                            }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = suggestion in selected,
                                onCheckedChange = { checked -> selected = if (checked) selected + suggestion else selected - suggestion },
                            )
                            Text(suggestion)
                        }
                    }
                }
            },
            confirmButton = {
                if (suggestions.isEmpty()) TextButton(onClick = vm::dismissOcrSuggestions) { Text("Done") }
                else TextButton(onClick = { vm.addOcrSuggestions(selected) }, enabled = selected.isNotEmpty()) { Text("Add selected") }
            },
            dismissButton = { if (suggestions.isNotEmpty()) TextButton(onClick = vm::dismissOcrSuggestions) { Text("Cancel") } },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopAppBar(title = { Text(if (vm.isEditing) "Edit session" else "New session") }, navigationIcon = {
            IconButton(onClick = ::requestClose) { Icon(Icons.Outlined.Close, "Close editor") }
        }, actions = {
            if (state.saving) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            else Button(onClick = vm::save, modifier = Modifier.padding(end = 8.dp), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)) { Text("Save") }
        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Button(
                    onClick = vm::addMovement,
                    enabled = !state.loading && !state.saving,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Icon(Icons.Outlined.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add movement")
                }
            }
        },
    ) { padding ->
        if (state.loading) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            if (state.draft.photoFilename != null) stickyHeader(key = "whiteboard-reference") {
                StickyPhotoReference(
                    file = vm.photoFile(),
                    expanded = photoExpanded,
                    onToggle = { photoExpanded = !photoExpanded },
                    onOpen = { showPhotoViewer = true },
                )
            }
            item {
                if (state.draft.photoFilename == null) {
                    Text("Whiteboard", style = MaterialTheme.typography.titleMedium)
                }
                if (state.photoProcessing) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.whiteboardScanning) LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { cameraOpen = true }, enabled = !state.photoProcessing && !state.whiteboardScanning, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.PhotoCamera, null); Spacer(Modifier.width(6.dp)); Text("Camera") }
                    OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !state.photoProcessing && !state.whiteboardScanning, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text("Choose") }
                }
                if (state.draft.photoFilename != null) {
                    OutlinedButton(
                        onClick = vm::scanWhiteboard,
                        enabled = !state.photoProcessing && !state.whiteboardScanning,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.whiteboardScanning) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Outlined.DocumentScanner, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (state.whiteboardScanning) "Scanning…" else "Scan whiteboard")
                    }
                    TextButton(
                        onClick = vm::removePhoto,
                        enabled = !state.photoProcessing && !state.whiteboardScanning,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Icon(Icons.Outlined.DeleteOutline, null); Spacer(Modifier.width(6.dp)); Text("Remove photo") }
                }
            }
            item {
                Text("When", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                val dateTime = Instant.ofEpochMilli(state.draft.sessionTime).atZone(ZoneId.systemDefault())
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
                    DatePickerDialog(context, { _, y, month, day ->
                        val cal = Calendar.getInstance().apply { timeInMillis = state.draft.sessionTime; set(y, month, day) }
                        vm.setTime(cal.timeInMillis)
                    }, dateTime.year, dateTime.monthValue - 1, dateTime.dayOfMonth).show()
                }) { Icon(Icons.Outlined.CalendarMonth, null); Spacer(Modifier.width(8.dp)); Text(formatDate(state.draft.sessionTime), modifier = Modifier.weight(1f)); Icon(Icons.Outlined.ChevronRight, null) }
                TextButton(modifier = Modifier.fillMaxWidth(), onClick = {
                    TimePickerDialog(context, { _, hour, minute ->
                        val cal = Calendar.getInstance().apply { timeInMillis = state.draft.sessionTime; set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute) }
                        vm.setTime(cal.timeInMillis)
                    }, dateTime.hour, dateTime.minute, true).show()
                }) { Icon(Icons.Outlined.Schedule, null); Spacer(Modifier.width(8.dp)); Text("Change time") }
            }
            item { Text("Movements", style = MaterialTheme.typography.titleMedium) }
            items(state.draft.movements.size, key = { state.draft.movements[it].id }) { index ->
                MovementEditor(index, state.draft.movements[index], vm, state.draft.movements.size)
            }
            item {
                Text("Session note", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                TextField(state.draft.sessionNote, vm::setNote, Modifier.fillMaxWidth(), placeholder = { Text("How did the session feel?") }, minLines = 3, shape = RoundedCornerShape(14.dp))
            }
            if (vm.isEditing) item {
                TextButton(onClick = { confirmDelete = true }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Delete, null); Spacer(Modifier.width(8.dp)); Text("Delete session") }
            }
        }
    }
    state.error?.let { message -> AlertDialog(onDismissRequest = vm::clearError, title = { Text("Couldn’t continue") }, text = { Text(message) }, confirmButton = { TextButton(onClick = vm::clearError) { Text("OK") } }) }
    if (showPhotoViewer) FullscreenPhotoViewer(vm.photoFile(), "Selected whiteboard photo", onDismiss = { showPhotoViewer = false })
}

@Composable
private fun StickyPhotoReference(file: java.io.File?, expanded: Boolean, onToggle: () -> Unit, onOpen: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        tonalElevation = 3.dp,
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 14.dp, vertical = 10.dp).testTag("whiteboard-reference-toggle"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.PushPin, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Whiteboard reference", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(if (expanded) "Fold" else "Show", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(4.dp))
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, if (expanded) "Fold photo" else "Show photo")
            }
            if (expanded) Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clickable(onClick = onOpen).testTag("whiteboard-reference-photo")) {
                LocalPhoto(file, "Selected whiteboard photo", Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = .65f),
                ) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Fullscreen, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.inverseOnSurface)
                        Spacer(Modifier.width(5.dp))
                        Text("Tap to zoom", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.inverseOnSurface)
                    }
                }
            }
        }
    }
}

@Composable
private fun MovementEditor(index: Int, movement: EditorMovement, vm: EditorViewModel, count: Int) {
    val candidates by vm.movementCandidates.collectAsState()
    var debouncedName by remember { mutableStateOf(movement.name) }
    LaunchedEffect(movement.name) {
        delay(150)
        debouncedName = movement.name
    }
    var suggestions by remember { mutableStateOf(emptyList<String>()) }
    var noteExpanded by rememberSaveable(movement.id) { mutableStateOf(movement.note.isNotBlank()) }
    LaunchedEffect(debouncedName, candidates) {
        suggestions = if (movement.name == debouncedName) {
            withContext(Dispatchers.Default) { vm.rankSuggestions(debouncedName, candidates) }
        } else {
            emptyList()
        }
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(8.dp)) {
                    Text("${index + 1}", Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.width(10.dp))
                Text(if (movement.name.isBlank()) "New movement" else movement.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1)
                IconButton(onClick = { vm.moveMovement(index, -1) }, enabled = index > 0) { Icon(Icons.Outlined.KeyboardArrowUp, "Move up") }
                IconButton(onClick = { vm.moveMovement(index, 1) }, enabled = index < count - 1) { Icon(Icons.Outlined.KeyboardArrowDown, "Move down") }
                IconButton(onClick = { vm.removeMovement(index) }, enabled = count > 1) { Icon(Icons.Outlined.RemoveCircleOutline, "Remove movement") }
            }
            TextField(movement.name, { vm.updateMovement(index, movement.copy(name = it)) }, Modifier.fillMaxWidth().testTag("movement-name-$index"), label = { Text("Movement name *") }, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), shape = RoundedCornerShape(14.dp))
            if (movement.name.isNotBlank() && suggestions.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(suggestions) { suggestion -> SuggestionChip(onClick = { vm.updateMovement(index, movement.copy(name = suggestion)) }, label = { Text(suggestion) }) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextField(movement.load, { vm.updateMovement(index, movement.copy(load = it)) }, Modifier.weight(1f), label = { Text("Load") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                TextField(movement.result, { vm.updateMovement(index, movement.copy(result = it)) }, Modifier.weight(1f), label = { Text("Result") }, singleLine = true, shape = RoundedCornerShape(14.dp))
            }
            if (noteExpanded || movement.note.isNotBlank()) {
                TextField(movement.note, { vm.updateMovement(index, movement.copy(note = it)) }, Modifier.fillMaxWidth(), label = { Text("Note") }, shape = RoundedCornerShape(14.dp))
            } else {
                TextButton(onClick = { noteExpanded = true }) {
                    Icon(Icons.Outlined.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add note")
                }
            }
        }
    }
}
