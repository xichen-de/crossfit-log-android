package dev.xichen.crossfitlog.ui

import dev.xichen.crossfitlog.domain.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TrainingDayTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private fun millis(day: Int, hour: Int) = LocalDate.of(2026, 8, day).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    private fun session(id: String, time: Long) = WorkoutSession(id, time, "", null, null, time, time, emptyList())

    @Test fun sessionsAreFilteredByLocalCalendarDay() {
        val sessions = listOf(session("early", millis(15, 1)), session("late", millis(15, 23)), session("other", millis(16, 1)))
        assertEquals(listOf("early", "late"), sessionsOnDay(sessions, millis(15, 12), zone).map { it.id })
    }
}
