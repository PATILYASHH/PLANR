package com.exosynk.planr.reminder

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Pure reminder-time math (unit-tested). */
object ReminderTime {

    /**
     * First occurrence of a series that started at [base] and is strictly after [after].
     * Each step is computed from [base] so monthly reminders on the 31st don't drift.
     */
    fun nextOccurrence(base: LocalDateTime, repeat: Repeat, after: LocalDateTime): LocalDateTime? {
        if (repeat == Repeat.NONE) return null
        var k = 1L
        while (k < 100_000) {
            val t = when (repeat) {
                Repeat.DAILY, Repeat.WEEKDAYS -> base.plusDays(k)
                Repeat.WEEKLY -> base.plusWeeks(k)
                Repeat.MONTHLY -> base.plusMonths(k)
                Repeat.YEARLY -> base.plusYears(k)
                Repeat.NONE -> return null
            }
            val weekdayOk = repeat != Repeat.WEEKDAYS ||
                (t.dayOfWeek != DayOfWeek.SATURDAY && t.dayOfWeek != DayOfWeek.SUNDAY)
            if (t.isAfter(after) && weekdayOk) return t
            k++
        }
        return null
    }

    /** When the reminder's due alert sounds (all-day reminders alert at 9 AM). */
    fun dueAlertAt(r: Reminder, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val due = r.dueAt ?: return null
        if (!r.allDay) return due
        val date = Instant.ofEpochMilli(due).atZone(zone).toLocalDate()
        return date.atTime(Reminder.ALL_DAY_ALERT_HOUR, 0).atZone(zone).toInstant().toEpochMilli()
    }

    enum class Stage { EARLY, DUE }

    /** The next alert strictly after [now]: a snooze, the early warning or the due time. */
    fun nextAlert(r: Reminder, now: Long, zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Stage>? {
        if (r.completed) return null
        if (r.snoozedUntil > now) return r.snoozedUntil to Stage.DUE
        val due = dueAlertAt(r, zone) ?: return null
        val early = if (r.earlyMinutes > 0) due - r.earlyMinutes * 60_000L else null
        return when {
            early != null && early > now -> early to Stage.EARLY
            due > now -> due to Stage.DUE
            else -> null
        }
    }

    fun toLocal(millis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()

    fun toMillis(t: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): Long =
        t.atZone(zone).toInstant().toEpochMilli()
}
