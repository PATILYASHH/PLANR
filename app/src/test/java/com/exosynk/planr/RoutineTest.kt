package com.exosynk.planr

import com.exosynk.planr.notes.Checklist
import com.exosynk.planr.notes.ChecklistItem
import com.exosynk.planr.routine.Action
import com.exosynk.planr.routine.RingerMode
import com.exosynk.planr.routine.RoutineCodec
import com.exosynk.planr.routine.RoutineTime
import com.exosynk.planr.routine.Trigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class RoutineTest {

    private fun at(day: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, day, h, m)

    @Test fun triggersRoundTrip() {
        val all = listOf(
            Trigger.Manual, Trigger.Time(600, 900, 0b0011111), Trigger.Time(420), Trigger.AlarmDismissed(),
            Trigger.AlarmDismissed(5), Trigger.Charging, Trigger.BatteryLow(15), Trigger.Headphones,
            Trigger.AppOpened("com.x"), Trigger.OffGridStarted, Trigger.OffGridEnded, Trigger.FocusFinished,
        )
        all.forEach { assertEquals(it, RoutineCodec.decodeTrigger(RoutineCodec.encodeTrigger(it))) }
        assertNull(RoutineCodec.decodeTrigger("{\"type\":\"teleport\"}"))
        assertNull(RoutineCodec.decodeTrigger("nope"))
    }

    @Test fun actionsRoundTripAndDropUnknown() {
        val list = listOf(
            Action.Dnd(true), Action.Ringer(RingerMode.SILENT), Action.MediaVolume(40), Action.StartOffGrid(120),
            Action.StartFocus(50, 2), Action.Gateway(false), Action.AlarmGroupSwitch(3, true), Action.Notify("hi"),
            Action.TodayBrief, Action.OpenApp("com.y"),
        )
        assertEquals(list, RoutineCodec.decodeActions(RoutineCodec.encodeActions(list)))
        assertEquals(listOf(Action.Dnd(true)), RoutineCodec.decodeActions("[{\"type\":\"dnd\",\"on\":true},{\"type\":\"warp\"}]"))
    }

    @Test fun stateTriggers() {
        assertTrue(Trigger.Time(600, 700).isState)
        assertFalse(Trigger.Time(600).isState)
        assertTrue(Trigger.Charging.isState)
        assertFalse(Trigger.AlarmDismissed().isState)
    }

    @Test fun overnightWindow() {
        val bed = Trigger.Time(23 * 60, 7 * 60)
        assertTrue(RoutineTime.isInWindow(bed, at(9, 23, 30)))
        assertTrue(RoutineTime.isInWindow(bed, at(10, 6, 59)))
        assertFalse(RoutineTime.isInWindow(bed, at(10, 7)))
        // Inside the window the next wake-up is the end; outside it's the next start.
        assertEquals(at(10, 7), RoutineTime.nextBoundary(bed, at(10, 2)))
        assertEquals(at(10, 23), RoutineTime.nextBoundary(bed, at(10, 12)))
    }

    @Test fun instantTimeOnlyOnChosenDays() {
        val weekday = Trigger.Time(8 * 60, days = 0b0011111)
        assertEquals(at(16, 8), RoutineTime.nextStart(weekday, at(13, 9))) // Fri 9:00 → Mon 8:00
    }

    @Test fun checklistRoundTripAndFromText() {
        val items = listOf(ChecklistItem("milk", true), ChecklistItem("eggs \"large\""))
        assertEquals(items, Checklist.decode(Checklist.encode(items)))
        assertEquals(listOf(ChecklistItem("a"), ChecklistItem("b")), Checklist.fromText("- a\n\n☐ b\n"))
        assertEquals(emptyList<ChecklistItem>(), Checklist.decode("garbage"))
    }
}
