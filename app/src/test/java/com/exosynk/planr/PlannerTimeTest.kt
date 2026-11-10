package com.exosynk.planr

import com.exosynk.planr.alarm.Alarm
import com.exosynk.planr.alarm.AlarmGroup
import com.exosynk.planr.alarm.AlarmTime
import com.exosynk.planr.alarm.Alarms
import com.exosynk.planr.reminder.Reminder
import com.exosynk.planr.reminder.ReminderTime
import com.exosynk.planr.reminder.Repeat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class PlannerTimeTest {

    private val utc = ZoneOffset.UTC
    // 2026-11-09 is a Monday
    private fun at(day: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, day, h, m)
    private fun ms(t: LocalDateTime) = t.toInstant(utc).toEpochMilli()

    @Test fun oneTimeAlarmRingsTodayOrTomorrow() {
        val a = Alarm(hour = 7, minute = 30)
        assertEquals(at(9, 7, 30), AlarmTime.next(a, at(9, 6), utc))
        assertEquals(at(10, 7, 30), AlarmTime.next(a, at(9, 7, 30), utc)) // exactly now → tomorrow
    }

    @Test fun datedAlarmInThePastNeverRings() {
        val a = Alarm(hour = 7, date = "2026-11-08")
        assertNull(AlarmTime.next(a, at(9, 6), utc))
        assertEquals(at(20, 7), AlarmTime.next(a.copy(date = "2026-11-20"), at(9, 6), utc))
    }

    @Test fun weekdayAlarmSkipsWeekend() {
        val a = Alarm(hour = 6, daysMask = 0b0011111)
        assertEquals(at(16, 6), AlarmTime.next(a, at(13, 7), utc)) // Fri after 6 → Mon
    }

    @Test fun skipNextJumpsOneOccurrence() {
        val a = Alarm(hour = 6, daysMask = 0b1111111, skippedAt = ms(at(10, 6)))
        assertEquals(at(11, 6), AlarmTime.next(a, at(9, 22), utc))
    }

    @Test fun groupOffSilencesItsAlarms() {
        val groups = listOf(AlarmGroup(1, "Work", enabled = false))
        val a1 = Alarm(id = 1, hour = 6, daysMask = 0b1111111, groupId = 1)
        val a2 = Alarm(id = 2, hour = 8, daysMask = 0b1111111)
        val next = Alarms.nextOf(listOf(a1, a2), groups, at(9, 5))
        assertEquals(2L, next?.first?.id)
    }

    @Test fun untilTextRoundsUp() {
        assertEquals("Alarm in 7 hours 30 minutes", AlarmTime.untilText(at(9, 23, 30), at(10, 7)))
        assertEquals("Alarm in 1 minute", AlarmTime.untilText(at(9, 6, 59).plusSeconds(30), at(9, 7)))
    }

    @Test fun monthlyRepeatDoesNotDrift() {
        val base = LocalDateTime.of(2026, 1, 31, 9, 0)
        val feb = ReminderTime.nextOccurrence(base, Repeat.MONTHLY, base)!!
        assertEquals(LocalDateTime.of(2026, 2, 28, 9, 0), feb)
        assertEquals(LocalDateTime.of(2026, 3, 31, 9, 0), ReminderTime.nextOccurrence(base, Repeat.MONTHLY, feb))
    }

    @Test fun weekdaysRepeatSkipsWeekend() {
        val fri = at(13, 9)
        assertEquals(at(16, 9), ReminderTime.nextOccurrence(fri, Repeat.WEEKDAYS, fri))
        assertNull(ReminderTime.nextOccurrence(fri, Repeat.NONE, fri))
    }

    @Test fun reminderAlertsEarlyThenDue() {
        val r = Reminder(title = "x", dueAt = ms(at(9, 10)), earlyMinutes = 15)
        assertEquals(ms(at(9, 9, 45)) to ReminderTime.Stage.EARLY, ReminderTime.nextAlert(r, ms(at(9, 9)), utc))
        assertEquals(ms(at(9, 10)) to ReminderTime.Stage.DUE, ReminderTime.nextAlert(r, ms(at(9, 9, 50)), utc))
        assertNull(ReminderTime.nextAlert(r, ms(at(9, 10)), utc))
        assertNull(ReminderTime.nextAlert(r.copy(completed = true), ms(at(9, 9)), utc))
    }

    @Test fun allDayReminderAlertsAtNine() {
        val r = Reminder(title = "x", dueAt = ms(at(9, 0)), allDay = true)
        assertEquals(ms(at(9, 9)), ReminderTime.dueAlertAt(r, utc))
    }

    @Test fun snoozeWins() {
        val r = Reminder(title = "x", dueAt = ms(at(9, 10)), snoozedUntil = ms(at(9, 10, 10)))
        assertEquals(ms(at(9, 10, 10)) to ReminderTime.Stage.DUE, ReminderTime.nextAlert(r, ms(at(9, 10, 1)), utc))
    }
}
