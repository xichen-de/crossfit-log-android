package dev.xichen.crossfitlog.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TrainingDayTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private fun millis(day: Int, hour: Int, month: Int = 8) = LocalDate.of(2026, month, day).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    @Test fun dayBoundsCoverTheLocalCalendarDay() {
        val day = dayBounds(LocalDate.of(2026, 8, 15), zone)
        assertTrue(millis(15, 0) in day)
        assertTrue(millis(15, 23) in day)
        assertFalse(millis(16, 0) in day)
        assertFalse(millis(14, 23) in day)
    }

    @Test fun dayBoundsFollowDaylightSavingChanges() {
        val shortDay = dayBounds(LocalDate.of(2026, 3, 29), zone)
        assertEquals(23 * 60 * 60 * 1000L, shortDay.last - shortDay.first + 1)
    }
}
