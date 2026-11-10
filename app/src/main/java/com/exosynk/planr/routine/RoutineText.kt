package com.exosynk.planr.routine

import com.exosynk.planr.ui.components.daysText
import com.exosynk.planr.ui.components.formatClock
import com.exosynk.planr.ui.components.formatDuration

/** Name lookups so routine descriptions show "YouTube" rather than a package name. */
class RoutineLabels(
    val app: (String) -> String,
    val alarm: (Long) -> String?,
    val group: (Long) -> String?,
)

object RoutineText {

    fun trigger(t: Trigger, l: RoutineLabels): String = when (t) {
        Trigger.Manual -> "you tap Run"
        is Trigger.Time -> {
            val days = daysText(t.days).let { if (it == "Every day") "every day" else it }
            if (t.isWindow) "it's ${formatClock(t.start)}–${formatClock(t.end)}, $days"
            else "it's ${formatClock(t.start)}, $days"
        }
        is Trigger.AlarmDismissed -> t.alarmId?.let { "the “${l.alarm(it) ?: "deleted"}” alarm is dismissed" }
            ?: "any alarm is dismissed"
        Trigger.Charging -> "the phone is charging"
        is Trigger.BatteryLow -> "battery is at ${t.percent}% or less"
        Trigger.Headphones -> "headphones are connected"
        is Trigger.AppOpened -> "${l.app(t.packageName)} is open"
        Trigger.OffGridStarted -> "Off-Grid starts"
        Trigger.OffGridEnded -> "Off-Grid ends"
        Trigger.FocusFinished -> "a focus session finishes"
    }

    fun action(a: Action, l: RoutineLabels): String = when (a) {
        is Action.Dnd -> if (a.on) "Do Not Disturb on" else "Do Not Disturb off"
        is Action.Ringer -> "sound mode: ${a.mode.title.lowercase()}"
        is Action.MediaVolume -> "media volume ${a.percent}%"
        is Action.StartOffGrid -> "go Off-Grid for ${formatDuration(a.minutes * 60_000L)}"
        is Action.StartFocus -> "focus ${a.minutes} min × ${a.cycles}"
        is Action.Gateway -> if (a.on) "Mindful Gateway on" else "Mindful Gateway off"
        is Action.AlarmGroupSwitch -> "alarm group “${l.group(a.groupId) ?: "deleted"}” ${if (a.on) "on" else "off"}"
        is Action.Notify -> "notify “${a.text}”"
        Action.TodayBrief -> "show today's brief"
        is Action.OpenApp -> "open ${l.app(a.packageName)}"
    }

    /** Short titles for the "add action" / trigger pickers. */
    val triggerChoices: List<Pair<String, Trigger>> = listOf(
        "Time" to Trigger.Time(7 * 60),
        "Alarm dismissed" to Trigger.AlarmDismissed(),
        "Charging" to Trigger.Charging,
        "Battery low" to Trigger.BatteryLow(20),
        "Headphones" to Trigger.Headphones,
        "App opened" to Trigger.AppOpened(""),
        "Off-Grid starts" to Trigger.OffGridStarted,
        "Off-Grid ends" to Trigger.OffGridEnded,
        "Focus finished" to Trigger.FocusFinished,
        "Manual" to Trigger.Manual,
    )

    val actionChoices: List<Pair<String, Action>> = listOf(
        "Do Not Disturb" to Action.Dnd(true),
        "Sound mode" to Action.Ringer(RingerMode.VIBRATE),
        "Media volume" to Action.MediaVolume(30),
        "Start Off-Grid" to Action.StartOffGrid(60),
        "Start Focus" to Action.StartFocus(25, 1),
        "Mindful Gateway" to Action.Gateway(true),
        "Alarm group on/off" to Action.AlarmGroupSwitch(0, false),
        "Show a message" to Action.Notify("Time to…"),
        "Today's brief" to Action.TodayBrief,
        "Open an app" to Action.OpenApp(""),
    )
}
