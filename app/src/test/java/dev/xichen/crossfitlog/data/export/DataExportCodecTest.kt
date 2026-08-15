package dev.xichen.crossfitlog.data.export

import dev.xichen.crossfitlog.domain.MovementRecord
import dev.xichen.crossfitlog.domain.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DataExportCodecTest {
    private fun session(id: String, time: Long, movement: String, photo: String? = null) = WorkoutSession(
        id, time, "session note", photo, photo, time, time,
        listOf(MovementRecord("m-$id", id, movement, movement.lowercase(), "60 kg", "5", "note", 0)),
    )

    @Test fun selectedRangeIsChronologicalAndOmitsPhotos() {
        val value = DataExportCodec.build(
            listOf(session("late", 300, "Snatch", "late.jpg"), session("early", 100, "Squat"), session("middle", 200, "Clean")),
            DataExportRange("Selected", "selected", 150, 300),
            exportedAt = 400,
        )
        assertEquals(listOf("middle", "late"), value.sessions.map { it.id })
        assertEquals(2, value.sessionCount)
        assertEquals(2, value.movementCount)
        val encoded = DataExportCodec.encode(value)
        assertTrue(encoded.contains("\"crossfit-log-export\""))
        assertFalse(encoded.contains("photoFilename"))
        assertFalse(encoded.contains("late.jpg"))
    }

    @Test fun customRangeIncludesTheEntireLocalEndDay() {
        val zone = ZoneId.of("Europe/Berlin")
        val range = customDataExportRange(LocalDate.of(2026, 3, 28), LocalDate.of(2026, 3, 29), zone)
        assertEquals(LocalDate.of(2026, 3, 28), Instant.ofEpochMilli(range.startInclusive!!).atZone(zone).toLocalDate())
        assertEquals(LocalDate.of(2026, 3, 29), Instant.ofEpochMilli(range.endInclusive).atZone(zone).toLocalDate())
        assertTrue(dataExportFileName(range, range.endInclusive, zone).endsWith(".json"))
    }
}
