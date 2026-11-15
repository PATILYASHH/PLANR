package com.exosynk.planr

import com.exosynk.planr.reminder.ContactAction
import com.exosynk.planr.reminder.Repeat
import com.exosynk.planr.smart.ContactIntent
import com.exosynk.planr.smart.Hinglish
import com.exosynk.planr.smart.SmartKind
import com.exosynk.planr.smart.SmartParser
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class HinglishTest {
    // Monday 2026-11-09, 08:00
    private fun at(day: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, day, h, m)
    private val now = at(9, 8)
    private fun p(s: String) = SmartParser.parse(s, now)

    @Test fun callParthTomorrowAtNine() {
        val r = p("kal 9 baje parth ko call karna")
        assertEquals(SmartKind.REMINDER, r.kind)
        assertEquals("Call Parth", r.title)
        assertEquals(at(10, 9), r.at)
        assertEquals(ContactIntent(ContactAction.CALL, "parth"), r.contact)
    }

    @Test fun whatsappMummyInTheEvening() {
        val r = p("mummy ko whatsapp karna shaam ko")
        assertEquals(ContactIntent(ContactAction.WHATSAPP, "mummy"), r.contact)
        assertEquals(at(9, 18), r.at)
    }

    @Test fun nightMeansPm() {
        val r = p("roz raat 10 baje dawai")
        assertEquals(Repeat.DAILY, r.repeat)
        assertEquals(at(9, 22), r.at)
        assertEquals("Dawai", r.title)
        assertEquals(at(9, 7), p("subah 7 baje walk").at!!.plusDays(-1)) // 7 AM already passed → tomorrow
    }

    @Test fun alarmInHinglish() {
        val a = p("subah 6 baje alarm laga do")
        assertEquals(SmartKind.ALARM, a.kind)
        assertEquals(6, a.at!!.hour)
        val b = p("mujhe kal 5:30 baje utha dena")
        assertEquals(SmartKind.ALARM, b.kind)
        assertEquals(at(10, 5, 30), b.at)
    }

    @Test fun weekdaysAndRemindMe() {
        val r = p("har somvar 10 baje meeting yaad dilana")
        assertEquals(Repeat.WEEKLY, r.repeat)
        assertEquals(at(9, 10), r.at)
        assertEquals("Meeting", r.title)
        assertEquals(at(11, 0), p("budhvar ko bill bharna").at) // Wednesday, all day
    }

    @Test fun englishAndNotesUntouched() {
        assertEquals("call Parth at 9 am", Hinglish.normalize("call Parth at 9 am"))
        assertEquals("note: kal ki baat", Hinglish.normalize("note: kal ki baat"))
        assertEquals(SmartKind.NOTE, p("note: kal ki baat").kind)
    }
}
