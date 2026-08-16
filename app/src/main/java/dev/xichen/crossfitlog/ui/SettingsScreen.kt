package dev.xichen.crossfitlog.ui

import android.app.Activity
import android.app.DatePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.xichen.crossfitlog.BuildConfig
import dev.xichen.crossfitlog.data.backup.BackupCodec
import dev.xichen.crossfitlog.data.backup.BackupService
import dev.xichen.crossfitlog.data.backup.PreparedBackup
import dev.xichen.crossfitlog.data.export.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(backupService: BackupService, dataExportService: DataExportService, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    var showRangeChoices by remember { mutableStateOf(false) }
    var chosenRange by remember { mutableStateOf<DataExportRange?>(null) }
    var fileRange by remember { mutableStateOf<DataExportRange?>(null) }
    var preparedBackup by remember { mutableStateOf<PreparedBackup?>(null) }
    var showRestoreConfirmation by remember { mutableStateOf(false) }

    DisposableEffect(backupService) {
        onDispose { backupService.discard(preparedBackup) }
    }

    fun runOperation(block: suspend () -> String) {
        if (!busy) scope.launch {
            busy = true
            val message = runCatching { block() }.getOrElse(BackupCodec::friendlyFailure)
            busy = false
            snackbar.showSnackbar(message)
        }
    }

    val dataExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        val range = fileRange
        fileRange = null
        if (uri != null && range != null) runOperation {
            val result = dataExportService.export(uri, range)
            "Exported ${result.sessionCount} session${if (result.sessionCount == 1) "" else "s"}."
        }
    }
    val backupExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        val prepared = preparedBackup
        preparedBackup = null
        if (uri != null && prepared != null) runOperation { backupService.save(prepared, uri); "Backup saved." }
        else backupService.discard(prepared)
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null && !busy) scope.launch {
            busy = true
            val message = runCatching { backupService.restore(uri).message() }.getOrElse(BackupCodec::friendlyFailure)
            busy = false
            snackbar.showSnackbar(message)
            // Restoring replaces the Room instance. Recreate to discard ViewModels holding the closed DAO.
            (context as? Activity)?.recreate()
        }
    }

    if (showRestoreConfirmation) AlertDialog(
        onDismissRequest = { showRestoreConfirmation = false },
        title = { Text("Replace current data?") },
        text = { Text("Restoring a backup replaces every current session and workout photo. The archive is fully validated before anything is changed.") },
        confirmButton = {
            TextButton(onClick = {
                showRestoreConfirmation = false
                restore.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
            }) { Text("Choose backup") }
        },
        dismissButton = { TextButton(onClick = { showRestoreConfirmation = false }) { Text("Cancel") } },
    )

    if (showRangeChoices) AlertDialog(
        onDismissRequest = { showRangeChoices = false },
        title = { Text("Select time span") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RangeChoice("Last 4 weeks") { chosenRange = presetDataExportRange(DataExportPreset.Last4Weeks); showRangeChoices = false }
                RangeChoice("Last 12 weeks") { chosenRange = presetDataExportRange(DataExportPreset.Last12Weeks); showRangeChoices = false }
                RangeChoice("This year") { chosenRange = presetDataExportRange(DataExportPreset.ThisYear); showRangeChoices = false }
                RangeChoice("Custom date range") {
                    showRangeChoices = false
                    showCustomDateRangePicker(context) { chosenRange = it }
                }
                RangeChoice("Complete history") { chosenRange = presetDataExportRange(DataExportPreset.CompleteHistory); showRangeChoices = false }
            }
        },
        confirmButton = { TextButton(onClick = { showRangeChoices = false }) { Text("Cancel") } },
    )

    chosenRange?.let { range ->
        AlertDialog(
            onDismissRequest = { chosenRange = null },
            title = { Text("Export ${range.label.lowercase()}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("The export includes workout details and notes. Photos are omitted.")
                    Button(onClick = {
                        fileRange = range
                        chosenRange = null
                        dataExport.launch(dataExportFileName(range))
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.FileDownload, null); Spacer(Modifier.width(8.dp)); Text("Save JSON file")
                    }
                    OutlinedButton(onClick = {
                        chosenRange = null
                        runOperation {
                            val result = dataExportService.prepare(range)
                            context.getSystemService(ClipboardManager::class.java)
                                .setPrimaryClip(ClipData.newPlainText("CrossFit data export", result.content))
                            "Copied ${result.sessionCount} session${if (result.sessionCount == 1) "" else "s"}."
                        }
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.ContentCopy, null); Spacer(Modifier.width(8.dp)); Text("Copy JSON")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { chosenRange = null }) { Text("Cancel") } },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopAppBar(title = { Text("Settings") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Your data", style = MaterialTheme.typography.headlineMedium)
            Text("Export a summary or move your complete log to another device.", color = MaterialTheme.colorScheme.onSurfaceVariant)

            SettingsCard(Icons.Outlined.Share, "Share workout data", "Choose a time span, then save or copy a readable export. Photos are not included.") {
                Button(onClick = { showRangeChoices = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Share, null); Spacer(Modifier.width(8.dp)); Text("Export selected data")
                }
            }

            SettingsCard(Icons.Outlined.Restore, "Backup & restore", "Save a complete database and every workout image to any location offered by Android, including cloud drives.") {
                OutlinedButton(onClick = {
                    if (!busy) scope.launch {
                        busy = true
                        runCatching { backupService.prepare(BuildConfig.VERSION_NAME) }
                            .onSuccess {
                                preparedBackup = it
                                busy = false
                                backupExport.launch(it.suggestedFilename)
                            }
                            .onFailure {
                                busy = false
                                snackbar.showSnackbar(BackupCodec.friendlyFailure(it))
                            }
                    }
                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.FileDownload, null); Spacer(Modifier.width(8.dp)); Text("Create backup")
                }
                OutlinedButton(onClick = { showRestoreConfirmation = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Restore, null); Spacer(Modifier.width(8.dp)); Text("Restore backup")
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            SettingsCard(Icons.Outlined.Lock, "Local by design", "Your live log stays in this app’s private database and is never transmitted automatically.") {}
            Text("CrossFit Log ${BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = CardDefaults.outlinedCardBorder()) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(10.dp)) {
                Icon(icon, null, Modifier.padding(10.dp).size(22.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun RangeChoice(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label, modifier = Modifier.fillMaxWidth()) }
}

private fun showCustomDateRangePicker(context: Context, onSelected: (DataExportRange) -> Unit) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val suggestedStart = today.minusWeeks(12)
    val startDialog = DatePickerDialog(context, { _, startYear, startMonth, startDay ->
        val start = LocalDate.of(startYear, startMonth + 1, startDay)
        val endDialog = DatePickerDialog(context, { _, endYear, endMonth, endDay ->
            onSelected(customDataExportRange(start, LocalDate.of(endYear, endMonth + 1, endDay), zone))
        }, today.year, today.monthValue - 1, today.dayOfMonth)
        endDialog.datePicker.minDate = start.atStartOfDay(zone).toInstant().toEpochMilli()
        endDialog.datePicker.maxDate = today.atStartOfDay(zone).toInstant().toEpochMilli()
        endDialog.setTitle("End date")
        endDialog.show()
    }, suggestedStart.year, suggestedStart.monthValue - 1, suggestedStart.dayOfMonth)
    startDialog.datePicker.maxDate = Instant.now().toEpochMilli()
    startDialog.setTitle("Start date")
    startDialog.show()
}
