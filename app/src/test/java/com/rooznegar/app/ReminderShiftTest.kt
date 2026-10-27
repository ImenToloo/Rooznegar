package com.rooznegar.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ReminderShiftTest {
    @Test fun keepsClockTimeOnNewDay() {
        val at = LocalDate.parse("2030-01-10").atTime(14, 30).atZone(TEHRAN).toInstant().toEpochMilli()
        val moved = shiftReminder(at, LocalDate.parse("2030-01-12"), now = 0L)
        assertEquals(LocalDate.parse("2030-01-12").atTime(14, 30).atZone(TEHRAN).toInstant().toEpochMilli(), moved)
    }

    @Test fun dropsReminderIfNewTimeIsPast() {
        val at = LocalDate.parse("2030-01-10").atTime(14, 30).atZone(TEHRAN).toInstant().toEpochMilli()
        assertNull(shiftReminder(at, LocalDate.parse("2030-01-12"), now = Long.MAX_VALUE))
    }

    @Test fun nullStaysNull() = assertNull(shiftReminder(null, LocalDate.parse("2030-01-12")))
}
