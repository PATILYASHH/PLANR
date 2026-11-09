package com.exosynk.planr

import com.exosynk.planr.data.db.Schedule
import com.exosynk.planr.data.db.isActiveAt
import com.exosynk.planr.data.db.windowEnd
import com.exosynk.planr.data.db.windowStartDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ScheduleTest {

    // 2026-11-09 is a Monday
    private fun at(day: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, day, h, m)

    @Test fun daytimeRange() {
        val s = Schedule(name = "Work", startMinute = 9 * 60, endMinute = 17 * 60, daysMask = 0b0011111)
        assertTrue(s.isActiveAt(at(9, 10)))
        assertFalse(s.isActiveAt(at(9, 17)))
        assertFalse(s.isActiveAt(at(14, 10))) // Saturday
    }

    @Test fun overnightRangeUsesStartDay() {
        // Friday-only 23:00 → 07:00 should still be active early Saturday
        val s = Schedule(name = "Night", startMinute = 23 * 60, endMinute = 7 * 60, daysMask = 0b0010000)
        assertTrue(s.isActiveAt(at(13, 23, 30)))  // Fri
        assertTrue(s.isActiveAt(at(14, 6, 59)))   // Sat morning
        assertFalse(s.isActiveAt(at(14, 23, 30))) // Sat night
    }

    @Test fun disabledNeverActive() {
        val s = Schedule(name = "x", startMinute = 0, endMinute = 0, enabled = false)
        assertFalse(s.isActiveAt(at(9, 12)))
    }

    @Test fun overnightWindowEndsNextMorning() {
        val s = Schedule(name = "Bedtime", startMinute = 23 * 60, endMinute = 7 * 60)
        assertEquals(at(10, 7), s.windowEnd(at(9, 23, 15)))
        // After midnight the window still belongs to the previous evening
        assertEquals(at(9, 0).toLocalDate(), s.windowStartDate(at(10, 2)))
        assertEquals(at(10, 7), s.windowEnd(at(10, 2)))
    }

    @Test fun daytimeWindowEndsSameDay() {
        val s = Schedule(name = "Study", startMinute = 17 * 60, endMinute = 20 * 60)
        assertEquals(at(9, 20), s.windowEnd(at(9, 18)))
    }
}
