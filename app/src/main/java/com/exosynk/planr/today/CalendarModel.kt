package com.exosynk.planr.today

import com.exosynk.planr.alarm.Alarm
import com.exosynk.planr.alarm.AlarmGroup
import com.exosynk.planr.alarm.AlarmScheduler
import com.exosynk.planr.alarm.AlarmTime
import com.exosynk.planr.data.db.dayBit
import com.exosynk.planr.notes.Note
import com.exosynk.planr.reminder.Reminder
import com.exosynk.planr.reminder.ReminderTime
import com.exosynk.planr.reminder.Repeat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** A reminder on one calendar day (repeating reminders appear on every day they recur). */
data class ReminderOccurrence(val reminder: Reminder, val at: LocalDateTime, val isNext: Boolean)

data class DayAgenda(
    val alarms: List<Pair<Alarm, LocalTime>> = emptyList(),
    val reminders: List<ReminderOccurrence> = emptyList(),
    val notes: List<Note> = emptyList(),
) {
    val isEmpty get() = alarms.isEmpty() && reminders.isEmpty() && notes.isEmpty()
}

/** Pure calendar math (unit-tested): what happens on which day. */
object CalendarModel {

    fun reminderDays(
        reminders: List<Reminder>,
        from: LocalDate,
        to: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Map<LocalDate, List<ReminderOccurrence>> {
        val out = HashMap<LocalDate, MutableList<ReminderOccurrence>>()
        reminders.forEach { r ->
            val due = r.dueAt ?: return@forEach
            val base = ReminderTime.toLocal(due, zone)
            fun add(t: LocalDateTime, isNext: Boolean) {
                val d = t.toLocalDate()
                if (!d.isBefore(from) && !d.isAfter(to)) out.getOrPut(d) { mutableListOf() }.add(ReminderOccurrence(r, t, isNext))
            }
            add(base, true)
            // Future repeats of an open repeating reminder (completed one-offs don't repeat).
            if (r.repeatRule != Repeat.NONE && !r.completed) {
                var t = base
                var guard = 0
                while (guard++ < 800) {
                    t = ReminderTime.nextOccurrence(base, r.repeatRule, t) ?: break
                    if (t.toLocalDate().isAfter(to)) break
                    add(t, false)
                }
            }
        }
        return out.mapValues { (_, list) -> list.sortedWith(compareBy({ it.reminder.allDay.not() }, { it.at })) }
    }

    /** Alarms that will ring on [date] (repeating by weekday; one-time on their next ring). */
    fun alarmsOn(
        date: LocalDate,
        alarms: List<Alarm>,
        groups: List<AlarmGroup>,
        now: LocalDateTime,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Pair<Alarm, LocalTime>> {
        val byId = groups.associateBy { it.id }
        return alarms.filter { AlarmScheduler.isArmed(it, byId) }.mapNotNull { a ->
            val time = LocalTime.of(a.hour, a.minute)
            val rings = if (a.isRepeating) {
                val t = date.atTime(time)
                a.daysMask and dayBit(date.dayOfWeek) != 0 && t.isAfter(now.minusDays(1)) &&
                    !(a.skippedAt != 0L && AlarmTime.toMillis(t, zone) == a.skippedAt)
            } else {
                AlarmTime.next(a, now, zone)?.toLocalDate() == date
            }
            if (rings) a to time else null
        }.sortedBy { it.second }
    }

    fun notesByDay(notes: List<Note>, zone: ZoneId = ZoneId.systemDefault()): Map<LocalDate, List<Note>> =
        notes.filter { !it.archived && !it.isEmpty }
            .groupBy { Instant.ofEpochMilli(it.updatedAt).atZone(zone).toLocalDate() }
}
