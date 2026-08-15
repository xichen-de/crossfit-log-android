package dev.xichen.crossfitlog.data.export

import android.content.ContentResolver
import android.net.Uri
import dev.xichen.crossfitlog.data.repository.WorkoutRepository
import dev.xichen.crossfitlog.domain.WorkoutSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class DataExportRange(
    val label: String,
    val fileLabel: String,
    val startInclusive: Long?,
    val endInclusive: Long,
)

enum class DataExportPreset { Last4Weeks, Last12Weeks, ThisYear, CompleteHistory }

fun presetDataExportRange(
    preset: DataExportPreset,
    now: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): DataExportRange {
    val today = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()
    return when (preset) {
        DataExportPreset.Last4Weeks -> DataExportRange("Last 4 weeks", "last-4-weeks", today.minusWeeks(4).atStartOfDay(zoneId).toInstant().toEpochMilli(), now)
        DataExportPreset.Last12Weeks -> DataExportRange("Last 12 weeks", "last-12-weeks", today.minusWeeks(12).atStartOfDay(zoneId).toInstant().toEpochMilli(), now)
        DataExportPreset.ThisYear -> DataExportRange("This year", today.year.toString(), today.withDayOfYear(1).atStartOfDay(zoneId).toInstant().toEpochMilli(), now)
        DataExportPreset.CompleteHistory -> DataExportRange("Complete history", "complete", null, now)
    }
}

fun customDataExportRange(start: LocalDate, end: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): DataExportRange {
    require(!end.isBefore(start)) { "The end date must be on or after the start date." }
    val formatter = DateTimeFormatter.ISO_LOCAL_DATE
    return DataExportRange(
        label = "${start.format(formatter)} to ${end.format(formatter)}",
        fileLabel = "custom_${start.format(formatter)}_to_${end.format(formatter)}",
        startInclusive = start.atStartOfDay(zoneId).toInstant().toEpochMilli(),
        endInclusive = end.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1,
    )
}

fun dataExportFileName(range: DataExportRange, now: Long = System.currentTimeMillis(), zoneId: ZoneId = ZoneId.systemDefault()): String {
    val day = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
    return "CrossFit_Export_${day}_${range.fileLabel}.json"
}

@Serializable
data class ExportRangeMetadata(val label: String, val startDate: String?, val endDate: String)

@Serializable
data class ExportMovement(val name: String, val load: String, val result: String, val note: String)

@Serializable
data class ExportSession(
    val id: String,
    val date: String,
    val sessionNote: String,
    val updatedAt: String,
    val movements: List<ExportMovement>,
)

@Serializable
data class CrossFitDataExport(
    val exportSchemaVersion: Int = 1,
    val type: String = "crossfit-log-export",
    val exportedAt: String,
    val range: ExportRangeMetadata,
    val sessionCount: Int,
    val movementCount: Int,
    val sessions: List<ExportSession>,
)

data class PreparedDataExport(val content: String, val sessionCount: Int, val movementCount: Int)

object DataExportCodec {
    private val json = Json { prettyPrint = true; explicitNulls = false; encodeDefaults = true }

    fun build(sessions: List<WorkoutSession>, range: DataExportRange, exportedAt: Long): CrossFitDataExport {
        val selected = sessions
            .filter { it.sessionTime <= range.endInclusive && (range.startInclusive == null || it.sessionTime >= range.startInclusive) }
            .sortedBy { it.sessionTime }
        return CrossFitDataExport(
            exportedAt = Instant.ofEpochMilli(exportedAt).toString(),
            range = ExportRangeMetadata(
                range.label,
                range.startInclusive?.let { Instant.ofEpochMilli(it).toString() },
                Instant.ofEpochMilli(range.endInclusive).toString(),
            ),
            sessionCount = selected.size,
            movementCount = selected.sumOf { it.movements.size },
            sessions = selected.map { session ->
                ExportSession(
                    id = session.id,
                    date = Instant.ofEpochMilli(session.sessionTime).toString(),
                    sessionNote = session.sessionNote,
                    updatedAt = Instant.ofEpochMilli(session.updatedAt).toString(),
                    movements = session.movements.map { ExportMovement(it.name, it.load, it.result, it.note) },
                )
            },
        )
    }

    fun encode(value: CrossFitDataExport): String = json.encodeToString(value)
}

class DataExportService(
    private val resolver: ContentResolver,
    private val repository: WorkoutRepository,
) {
    suspend fun prepare(range: DataExportRange, exportedAt: Long = System.currentTimeMillis()): PreparedDataExport =
        withContext(Dispatchers.IO) {
            val value = DataExportCodec.build(repository.getAllSessions(), range, exportedAt)
            require(value.sessions.isNotEmpty()) { "No sessions were found in this time span." }
            PreparedDataExport(DataExportCodec.encode(value), value.sessionCount, value.movementCount)
        }

    suspend fun export(uri: Uri, range: DataExportRange): PreparedDataExport = withContext(Dispatchers.IO) {
        val prepared = prepare(range)
        resolver.openOutputStream(uri, "w")?.bufferedWriter()?.use { it.write(prepared.content) }
            ?: error("The selected destination could not be opened.")
        prepared
    }
}
