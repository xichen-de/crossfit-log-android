package dev.xichen.crossfitlog.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.xichen.crossfitlog.data.backup.BackupCodec
import dev.xichen.crossfitlog.data.backup.BackupService
import dev.xichen.crossfitlog.data.backup.PreparedBackup
import dev.xichen.crossfitlog.data.export.DataExportRange
import dev.xichen.crossfitlog.data.export.DataExportService
import dev.xichen.crossfitlog.data.export.PreparedDataExport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface SettingsEvent {
    data class Message(val text: String) : SettingsEvent
    data class LaunchBackupSave(val suggestedFilename: String) : SettingsEvent
    data class CopyExport(val export: PreparedDataExport) : SettingsEvent
    data class Restored(val message: String) : SettingsEvent
}

/**
 * Owns backup, restore, and export work so it survives configuration changes. A restore must not
 * be abandoned halfway through by a rotation: its completion is what tells the app to rebuild
 * screens that still reference the closed database.
 */
class SettingsViewModel(
    private val backupService: BackupService,
    private val dataExportService: DataExportService,
    private val appVersion: String,
) : ViewModel() {
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var preparedBackup: PreparedBackup? = null
    private var pendingExportRange: DataExportRange? = null

    fun createBackup() = run(fallback = "The backup could not be created.") {
        val prepared = backupService.prepare(appVersion)
        backupService.discard(preparedBackup)
        preparedBackup = prepared
        _events.send(SettingsEvent.LaunchBackupSave(prepared.suggestedFilename))
        null
    }

    fun saveBackup(uri: Uri?) {
        val prepared = preparedBackup ?: return
        preparedBackup = null
        if (uri == null) backupService.discard(prepared)
        else run(fallback = "The backup could not be saved.") { backupService.save(prepared, uri); "Backup saved." }
    }

    fun restore(uri: Uri?) {
        if (uri == null) return
        run(fallback = "The backup could not be read.") {
            val report = backupService.restore(uri)
            _events.send(SettingsEvent.Restored(report.message()))
            null
        }
    }

    /** Remembers the range while the system file picker is open. */
    fun beginFileExport(range: DataExportRange) { pendingExportRange = range }

    fun exportToFile(uri: Uri?) {
        val range = pendingExportRange ?: return
        pendingExportRange = null
        if (uri != null) run(fallback = "The export could not be saved.") {
            val result = dataExportService.export(uri, range)
            "Exported ${sessionsLabel(result.sessionCount)}."
        }
    }

    fun copyExport(range: DataExportRange) = run(fallback = "The export could not be created.") {
        _events.send(SettingsEvent.CopyExport(dataExportService.prepare(range)))
        null
    }

    private fun run(fallback: String, block: suspend () -> String?) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val message = try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                BackupCodec.friendlyFailure(error, fallback)
            } finally {
                _busy.value = false
            }
            message?.let { _events.send(SettingsEvent.Message(it)) }
        }
    }

    override fun onCleared() {
        backupService.discard(preparedBackup)
        preparedBackup = null
    }
}

internal fun sessionsLabel(count: Int) = "$count session${if (count == 1) "" else "s"}"
