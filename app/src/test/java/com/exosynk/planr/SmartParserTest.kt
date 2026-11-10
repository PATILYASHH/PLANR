package com.exosynk.planr

import com.exosynk.planr.reminder.ContactAction
import com.exosynk.planr.reminder.Repeat
import com.exosynk.planr.smart.ContactIntent
import com.exosynk.planr.smart.Contacts
import com.exosynk.planr.smart.SmartKind
import com.exosynk.planr.smart.SmartParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class SmartParserTest {

    // Monday 2026-11-09
    private fun at(day: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, day, h, m)
    private val morning = at(9, 8, 0)
    private fun p(s: String, now: LocalDateTime = morning) = SmartParser.parse(s, now)

    @Test fun callParthAt9am() {
        val r = p("call to parth at 9 am")
        assertEquals(SmartKind.REMINDER, r.kind)
        assertEquals("Call Parth", r.title)
        assertEquals(at(9, 9), r.at)
        assertEquals(ContactIntent(ContactAction.CALL, "parth"), r.contact)
    }

    @Test fun pastTimeMovesToTomorrow() {
        assertEquals(at(10, 9), p("call parth at 9 am", now = at(9, 10)).at)
    }

    @Test fun bareHourPicksNextOccurrence() {
        assertEquals(at(9, 9), p("call mom at 9", now = at(9, 8)).at)   // 9 AM still ahead
        assertEquals(at(9, 21), p("call mom at 9", now = at(9, 12)).at) // 9 PM next
        assertEquals(at(10, 9), p("call mom at 9", now = at(9, 22)).at) // tomorrow 9 AM
    }

    @Test fun contactWithTopicAndTomorrow() {
        val r = p("remind me to message Rahul Sharma about the project tomorrow at 4pm")
        assertEquals("Message Rahul Sharma about the project", r.title)
        assertEquals(at(10, 16), r.at)
        assertEquals(ContactIntent(ContactAction.MESSAGE, "Rahul Sharma"), r.contact)
    }

    @Test fun whatsapp() {
        assertEquals(ContactAction.WHATSAPP, p("whatsapp priya tonight")!!.contact!!.action)
        assertEquals(at(9, 20), p("whatsapp priya tonight").at)
    }

    @Test fun relative() {
        val r = p("in 20 min check the oven")
        assertEquals(at(9, 8, 20), r.at)
        assertEquals("Check the oven", r.title)
        assertEquals(at(9, 8, 30), p("take tea out in half an hour").at)
        assertEquals(at(9, 10), p("stretch in 2 hours").at)
    }

    @Test fun weekdayAndAllDay() {
        val r = p("pay rent on friday")
        assertEquals(at(13, 0), r.at)
        assertTrue(r.allDay)
        assertEquals("Pay rent", r.title)
    }

    @Test fun datesWithMonths() {
        assertEquals(LocalDateTime.of(2026, 12, 25, 10, 0), p("gift shopping 25th december 10am").at)
        assertEquals(LocalDateTime.of(2027, 3, 1, 0, 0), p("renew passport march 1st").at.let { it })
        assertEquals(LocalDateTime.of(2026, 12, 1, 0, 0), p("pay bill on the 1st").at)
        assertEquals(LocalDateTime.of(2026, 12, 5, 0, 0), p("dentist on 5/12").at)
    }

    @Test fun repeats() {
        val r = p("take vitamins every day at 9:30 pm")
        assertEquals(Repeat.DAILY, r.repeat)
        assertEquals(at(9, 21, 30), r.at)
        assertEquals("Take vitamins", r.title)
        val w = p("team standup every monday 10am")
        assertEquals(Repeat.WEEKLY, w.repeat)
        assertEquals(at(9, 10), w.at) // today is Monday and 10 AM is ahead
    }

    @Test fun alarms() {
        val a = p("wake me up at 6:30 weekdays", now = at(9, 22))
        assertEquals(SmartKind.ALARM, a.kind)
        assertEquals(6, a.at!!.hour)
        assertEquals(30, a.at!!.minute)
        assertEquals(0b0011111, a.alarmDays)
        val b = p("set alarm for 7 tomorrow")
        assertEquals(SmartKind.ALARM, b.kind)
        assertEquals(at(10, 7), b.at) // alarms default to the morning
        assertNull(p("set alarm").at)
    }

    @Test fun notes() {
        val n = p("note: gift ideas for mom")
        assertEquals(SmartKind.NOTE, n.kind)
        assertEquals("Gift ideas for mom", n.title)
    }

    @Test fun plainTextStaysPlain() {
        val r = p("buy milk")
        assertEquals("Buy milk", r.title)
        assertNull(r.at)
        assertNull(r.contact)
        assertNull(p("call back later").contact) // "back" is not a name
    }

    @Test fun meridiemAndNoon() {
        assertEquals(at(9, 12), p("lunch with team at noon").at)
        assertEquals(at(9, 0, 30).plusDays(1), p("bins at 12:30 am").at)
        assertEquals(at(9, 14), p("doctor tomorrow afternoon").at!!.minusDays(1))
    }

    @Test fun contactRanking() {
        assertEquals(0, Contacts.rank("Parth", "parth"))
        assertEquals(0, Contacts.rank("Parth Patil", "parth"))
        assertEquals(1, Contacts.rank("Amit Parthasarathy", "parth"))
        assertEquals(2, Contacts.rank("Separth", "parth"))
    }
}
