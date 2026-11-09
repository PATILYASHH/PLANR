package com.planr.app

import com.planr.app.data.db.Schedule
import com.planr.app.data.db.isActiveAt
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
}
