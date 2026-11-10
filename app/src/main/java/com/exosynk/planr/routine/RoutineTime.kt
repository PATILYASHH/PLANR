package com.exosynk.planr.routine

import com.exosynk.planr.data.db.Schedule
import com.exosynk.planr.data.db.dayBit
import com.exosynk.planr.data.db.isActiveAt
import com.exosynk.planr.data.db.windowEnd
import java.time.LocalDateTime

/** Pure time math for time-triggered routines (unit-tested). */
object RoutineTime {

    private fun Trigger.Time.asSchedule() =
        Schedule(name = "", startMinute = start, endMinute = end, daysMask = days)

    /** True while a time *window* is running. */
    fun isInWindow(t: Trigger.Time, now: LocalDateTime): Boolean =
        t.isWindow && t.asSchedule().isActiveAt(now)

    /** Next start strictly after [now]. */
    fun nextStart(t: Trigger.Time, now: LocalDateTime): LocalDateTime? {
        for (i in 0L..8L) {
            val day = now.toLocalDate().plusDays(i)
            if (t.days and dayBit(day.dayOfWeek) == 0) continue
            val at = day.atStartOfDay().plusMinutes(t.start.toLong())
            if (at.isAfter(now)) return at
        }
        return null
    }

    /** Next moment the routine engine must wake up for this trigger (a start, or the end of a running window). */
    fun nextBoundary(t: Trigger.Time, now: LocalDateTime): LocalDateTime? {
        val start = nextStart(t, now)
        val end = if (isInWindow(t, now)) t.asSchedule().windowEnd(now) else null
        return listOfNotNull(start, end).minOrNull()
    }
}
