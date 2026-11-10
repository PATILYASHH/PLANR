package com.exosynk.planr.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AutoMode
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.exosynk.planr.PlanrApp
import com.exosynk.planr.alarm.AlarmTime
import com.exosynk.planr.alarm.Alarms
import com.exosynk.planr.reminder.Reminders
import com.exosynk.planr.reminder.dueText
import com.exosynk.planr.routine.RoutineEngine
import com.exosynk.planr.ui.MODULE_ALARM
import com.exosynk.planr.ui.MODULE_NOTES
import com.exosynk.planr.ui.MODULE_REMINDERS
import com.exosynk.planr.ui.MODULE_ROUTINES
import com.exosynk.planr.ui.components.Gap
import com.exosynk.planr.ui.components.SectionCard
import com.exosynk.planr.ui.theme.Danger
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Habit home's "Today": the other modules at a glance, live from the database. */
@Composable
fun TodayCard(onOpenModule: (Int) -> Unit, onOpenReminder: (Long) -> Unit) {
    val app = PlanrApp.instance
    val context = LocalContext.current
    val alarms by app.db.alarmDao().observeAll().collectAsState(initial = emptyList())
    val groups by app.db.alarmGroupDao().observeAll().collectAsState(initial = emptyList())
    val reminders by app.db.reminderDao().observeAll().collectAsState(initial = emptyList())
    val notes by app.db.noteDao().observeAll().collectAsState(initial = emptyList())
    val routines by app.db.routineDao().observeAll().collectAsState(initial = emptyList())
    val active by produceState(emptySet<Long>(), routines) { value = RoutineEngine.activeIds(app) }

    val now = LocalDateTime.now()
    val today = LocalDate.now()
    val next = Alarms.nextOf(alarms, groups, now)
    val due = reminders.filter { r ->
        !r.completed && r.dueAt != null && !com.exosynk.planr.reminder.ReminderTime.toLocal(r.dueAt).toLocalDate().isAfter(today)
    }
    val pinned = notes.count { it.pinned && !it.archived }
    val running = routines.filter { it.id in active }

    SectionCard {
        Text("Today", style = MaterialTheme.typography.titleLarge)
        Gap(4.dp)
        Line(Icons.Outlined.Alarm, onClick = { onOpenModule(MODULE_ALARM) }) {
            Text(
                next?.let { (a, at) ->
                    at.toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)) +
                        (if (at.toLocalDate() == today) " today" else if (at.toLocalDate() == today.plusDays(1)) " tomorrow" else "") +
                        (if (a.label.isNotBlank()) " · ${a.label}" else "") +
                        " · " + AlarmTime.untilText(now, at).removePrefix("Alarm ")
                } ?: "No alarms set",
            )
        }
        Line(Icons.Outlined.Circle, onClick = { onOpenModule(MODULE_REMINDERS) }) {
            Text(if (due.isEmpty()) "Nothing due today" else "${due.size} due today")
        }
        due.take(4).forEach { r ->
            Row(
                Modifier.fillMaxWidth().padding(start = 32.dp).clickable { onOpenReminder(r.id) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { app.appScope.launch { Reminders.complete(context.applicationContext, r.id) } }) {
                    Icon(Icons.Outlined.Circle, contentDescription = "Complete", modifier = Modifier.size(20.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(r.title, style = MaterialTheme.typography.bodyLarge)
                    val overdue = r.dueAt!! < System.currentTimeMillis() && !r.allDay
                    Text(
                        dueText(r, today),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (overdue) Danger else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (pinned > 0) {
            Line(Icons.AutoMirrored.Outlined.StickyNote2, onClick = { onOpenModule(MODULE_NOTES) }) {
                Text("$pinned pinned ${if (pinned == 1) "note" else "notes"}")
            }
        }
        Line(Icons.Outlined.AutoMode, onClick = { onOpenModule(MODULE_ROUTINES) }) {
            Text(
                when {
                    running.isNotEmpty() -> "Running: " + running.joinToString(", ") { it.name }
                    routines.isEmpty() -> "No routines yet"
                    else -> "${routines.count { it.enabled }} routines on"
                }
            )
        }
        if (routines.isEmpty() && alarms.isEmpty()) {
            TextButton(onClick = { onOpenModule(MODULE_ROUTINES) }) { Text("Try a routine like “Good morning” →") }
        }
    }
}

@Composable
private fun Line(icon: ImageVector, onClick: () -> Unit, content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { content() }
    }
}
