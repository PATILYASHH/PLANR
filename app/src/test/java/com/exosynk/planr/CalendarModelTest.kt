package com.exosynk.planr

import com.exosynk.planr.alarm.Alarm
import com.exosynk.planr.alarm.AlarmGroup
import com.exosynk.planr.notes.Note
import com.exosynk.planr.reminder.Reminder
import com.exosynk.planr.today.CalendarModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class CalendarModelTest {
    private val utc = ZoneOffset.UTC
    private fun d(day: Int) = LocalDate.of(2026, 11, day)
    private fun ms(t: LocalDateTime) = t.toInstant(utc).toEpochMilli()

    @Test fun repeatingReminderAppearsOnEveryOccurrenceInRange() {
        val r = Reminder(id = 1, title = "Gym", dueAt = ms(d(9).atTime(7, 0)), repeat = "WEEKDAYS")
        val days = CalendarModel.reminderDays(listOf(r), d(9), d(15), utc)
        assertEquals(setOf(d(9), d(10), d(11), d(12), d(13)), days.keys) // Mon–Fri, not the weekend
        assertTrue(days[d(9)]!!.single().isNext)
        assertTrue(!days[d(10)]!!.single().isNext)
    }

    @Test fun completedOneOffStaysOnItsDayOnly() {
        val r = Reminder(id = 1, title = "x", dueAt = ms(d(10).atTime(9, 0)), completed = true)
        assertEquals(setOf(d(10)), CalendarModel.reminderDays(listOf(r), d(1), d(30), utc).keys)
    }

    @Test fun alarmsOnDays() {
        val now = d(9).atTime(8, 0) // Monday morning
        val weekday = Alarm(id = 1, hour = 6, daysMask = 0b0011111)
        val once = Alarm(id = 2, hour = 7, minute = 30)
        val grouped = Alarm(id = 3, hour = 5, daysMask = 0b1111111, groupId = 9)
        val groups = listOf(AlarmGroup(9, "Off", enabled = false))
        val all = listOf(weekday, once, grouped)
        assertEquals(listOf(1L), CalendarModel.alarmsOn(d(10), all, groups, now, utc).map { it.first.id } - 2L)
        assertTrue(CalendarModel.alarmsOn(d(10), all, groups, now, utc).any { it.first.id == 2L }) // once → tomorrow
        assertTrue(CalendarModel.alarmsOn(d(14), all, groups, now, utc).isEmpty()) // Saturday, group off
    }

    @Test fun notesByEditDay() {
        val n = Note(id = 1, title = "a", updatedAt = ms(d(12).atTime(15, 0)))
        val archived = n.copy(id = 2, archived = true)
        assertEquals(listOf(n), CalendarModel.notesByDay(listOf(n, archived), utc)[d(12)])
    }
}
