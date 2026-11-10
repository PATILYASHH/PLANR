package com.exosynk.planr.today

import com.exosynk.planr.PlanrApp
import com.exosynk.planr.alarm.Alarm
import com.exosynk.planr.alarm.Alarms
import com.exosynk.planr.reminder.Reminder
import com.exosynk.planr.reminder.ReminderTime
import com.exosynk.planr.routine.RoutineEngine
import com.exosynk.planr.ui.components.formatDuration
import java.text.DateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date

/** One snapshot of the day across every module. */
data class TodayData(
    val nextAlarm: Pair<Alarm, LocalDateTime>?,
    /** Not completed and due today, oldest first. */
    val dueToday: List<Reminder>,
    /** Not completed and due before today. */
    val overdue: List<Reminder>,
    val pinnedNotes: Int,
    val activeRoutines: List<String>,
    val level: Int,
    val offGridStreak: Int,
    val focusMinutesToday: Int,
    val screenTimeYesterdayMs: Long,
)

object TodaySummary {

    suspend fun load(now: LocalDateTime = LocalDateTime.now()): TodayData {
        val app = PlanrApp.instance
        val zone = ZoneId.systemDefault()
        val today = now.toLocalDate()
        val next = Alarms.nextOf(app.db.alarmDao().getAll(), app.db.alarmGroupDao().getAll(), now)
        val open = app.db.reminderDao().getAll().filter { !it.completed && it.dueAt != null }
        fun day(r: Reminder) = ReminderTime.toLocal(r.dueAt!!, zone).toLocalDate()
        val dueToday = open.filter { day(it) == today }.sortedBy { it.dueAt }
        val overdue = open.filter { day(it).isBefore(today) }.sortedBy { it.dueAt }
        val routines = app.db.routineDao().getAll()
        val active = RoutineEngine.activeIds(app)
        val yesterday = today.minusDays(1).toString()
        val stats = app.progress.state.value.stats
        return TodayData(
            nextAlarm = next,
            dueToday = dueToday,
            overdue = overdue,
            pinnedNotes = app.db.noteDao().getAll().count { it.pinned && !it.archived },
            activeRoutines = routines.filter { it.id in active }.map { it.name },
            level = stats.level,
            offGridStreak = stats.currentStreak,
            focusMinutesToday = app.focus.state.value.minutesToday,
            screenTimeYesterdayMs = app.db.dailyUsageDao().since(yesterday).filter { it.day == yesterday }.sumOf { it.ms },
        )
    }

    /** Plain text for notifications. */
    fun text(d: TodayData, today: LocalDate = LocalDate.now()): String = buildList {
        val reminders = d.dueToday.size
        add(
            when (reminders) {
                0 -> "No reminders due today"
                1 -> "1 reminder today: ${d.dueToday[0].title}"
                else -> "$reminders reminders today, first: ${d.dueToday[0].title}"
            }
        )
        if (d.overdue.isNotEmpty()) add("${d.overdue.size} overdue")
        d.nextAlarm?.let { (a, at) ->
            val time = DateFormat.getTimeInstance(DateFormat.SHORT)
                .format(Date(at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()))
            val whenText = if (at.toLocalDate() == today) "today" else if (at.toLocalDate() == today.plusDays(1)) "tomorrow" else at.toLocalDate().toString()
            add("Next alarm $time $whenText" + if (a.label.isNotBlank()) " (${a.label})" else "")
        }
        if (d.screenTimeYesterdayMs > 0) add("Screen time yesterday ${formatDuration(d.screenTimeYesterdayMs)}")
        add("Level ${d.level} · Off-Grid streak ${d.offGridStreak}")
    }.joinToString("\n")
}
