package com.exosynk.planr.alarm

import com.exosynk.planr.data.db.dayBit
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Pure alarm-time math (unit-tested). */
object AlarmTime {

    /** Next time [alarm] rings strictly after [now], or null if it never will again. Ignores enabled/group state. */
    fun next(alarm: Alarm, now: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime? {
        val time = LocalTime.of(alarm.hour.coerceIn(0, 23), alarm.minute.coerceIn(0, 59))
        if (!alarm.isRepeating) {
            val date = alarm.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            if (date != null) return date.atTime(time).takeIf { it.isAfter(now) }
            val today = now.toLocalDate().atTime(time)
            return if (today.isAfter(now)) today else today.plusDays(1)
        }
        for (i in 0L..14L) {
            val day = now.toLocalDate().plusDays(i)
            if (alarm.daysMask and dayBit(day.dayOfWeek) == 0) continue
            val t = day.atTime(time)
            if (!t.isAfter(now)) continue
            if (alarm.skippedAt != 0L && toMillis(t, zone) == alarm.skippedAt) continue
            return t
        }
        return null
    }

    fun toMillis(t: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): Long =
        t.atZone(zone).toInstant().toEpochMilli()

    /** "Alarm in 7 hours 30 minutes" (rounded up to the next minute, like Samsung Clock). */
    fun untilText(now: LocalDateTime, at: LocalDateTime): String {
        val mins = (Duration.between(now, at).toMillis() + 59_999L) / 60_000L
        val d = mins / (24 * 60)
        val h = (mins / 60) % 24
        val m = mins % 60
        val parts = buildList {
            if (d > 0) add("$d ${if (d == 1L) "day" else "days"}")
            if (h > 0) add("$h ${if (h == 1L) "hour" else "hours"}")
            if (m > 0 || isEmpty()) add("$m ${if (m == 1L) "minute" else "minutes"}")
        }
        return "Alarm in " + parts.joinToString(" ")
    }
}
