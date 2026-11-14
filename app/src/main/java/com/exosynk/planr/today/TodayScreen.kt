package com.exosynk.planr.today

import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AutoMode
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.exosynk.planr.PlanrApp
import com.exosynk.planr.alarm.AlarmTime
import com.exosynk.planr.reminder.ContactActions
import com.exosynk.planr.reminder.Reminders
import com.exosynk.planr.reminder.Repeat
import com.exosynk.planr.smart.SmartAddBar
import com.exosynk.planr.smart.SmartKind
import com.exosynk.planr.ui.components.Gap
import com.exosynk.planr.ui.components.formatDuration
import com.exosynk.planr.ui.theme.Accent
import com.exosynk.planr.ui.theme.AccentSoft
import com.exosynk.planr.ui.theme.Success
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val NoteGreen = Color(0xFF5BD6A0)
private val AlarmOrange = Color(0xFFFFB86B)
private val EventPurple = Color(0xFFC792EA)

/** Where Today's buttons go. */
class TodayNav(
    val newAlarm: () -> Unit,
    val newReminder: () -> Unit,
    val newNote: () -> Unit,
    val openAlarm: (Long) -> Unit,
    val openReminder: (Long) -> Unit,
    val openNote: (Long) -> Unit,
    val focus: () -> Unit,
    val offGrid: () -> Unit,
    val routines: () -> Unit,
    val habit: () -> Unit,
    val settings: () -> Unit,
    val about: () -> Unit,
    val search: () -> Unit,
)

/**
 * The hub: type anything (smart add), see the month with dots for reminders,
 * notes and alarms, and everything planned for the selected day.
 */
@Composable
fun TodayScreen(modifier: Modifier, nav: TodayNav) {
    val app = PlanrApp.instance
    val context = LocalContext.current
    val alarms by app.db.alarmDao().observeAll().collectAsState(initial = emptyList())
    val groups by app.db.alarmGroupDao().observeAll().collectAsState(initial = emptyList())
    val reminders by app.db.reminderDao().observeAll().collectAsState(initial = emptyList())
    val notes by app.db.noteDao().observeAll().collectAsState(initial = emptyList())
    val progress by app.progress.state.collectAsState()

    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = LocalDateTime.now() } }
    val today = now.toLocalDate()
    var selectedEpoch by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    val selected = LocalDate.ofEpochDay(selectedEpoch)
    var monthView by rememberSaveable { mutableStateOf(false) }
    var shownMonthEpoch by rememberSaveable { mutableStateOf(today.withDayOfMonth(1).toEpochDay()) }
    val shownMonth = YearMonth.from(LocalDate.ofEpochDay(shownMonthEpoch))

    // Visible range: the whole shown month (plus padding weeks), or the selected week.
    val rangeFrom = if (monthView) shownMonth.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    else selected.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val rangeTo = if (monthView) shownMonth.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    else rangeFrom.plusDays(6)
    val reminderDays = remember(reminders, rangeFrom, rangeTo) { CalendarModel.reminderDays(reminders, rangeFrom, rangeTo) }
    val noteDays = remember(notes) { CalendarModel.notesByDay(notes) }
    // Phone calendar events (Google, Samsung…), read on-device when turned on.
    val settings by app.settings.state.collectAsState()
    var calTick by remember { mutableStateOf(0) }
    val showCalendar = settings.deviceCalendar && DeviceCalendar.hasPermission(context)
    val calendarPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        app.settings.update(app.settings.current.copy(deviceCalendar = granted), force = true)
        calTick++
    }
    val eventDays by produceState(emptyMap<LocalDate, List<CalendarEvent>>(), showCalendar, rangeFrom, rangeTo, calTick, today) {
        value = if (showCalendar) withContext(Dispatchers.IO) {
            DeviceCalendar.byDay(DeviceCalendar.events(context, rangeFrom, rangeTo))
        } else emptyMap()
    }
    var calendarPromptHidden by rememberSaveable { mutableStateOf(false) }
    val agenda = DayAgenda(
        alarms = CalendarModel.alarmsOn(selected, alarms, groups, now),
        reminders = reminderDays[selected].orEmpty(),
        notes = noteDays[selected].orEmpty(),
    )
    val overdue = reminders.filter { r ->
        !r.completed && r.dueAt != null && r.dueAt < System.currentTimeMillis() &&
            com.exosynk.planr.reminder.ReminderTime.toLocal(r.dueAt).toLocalDate().isBefore(today)
    }
    val screenMs by produceState(0L, now) {
        value = withContext(Dispatchers.IO) { app.usage.todayUsage().values.sum() }
    }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 16.dp, 20.dp, 32.dp)) {
        item {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        today.format(DateTimeFormatter.ofPattern("EEEE")),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        today.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = nav.search) { Icon(Icons.Outlined.Search, contentDescription = "Search") }
                IconButton(onClick = nav.about) { Icon(Icons.Outlined.Info, contentDescription = "About PLANR") }
                IconButton(onClick = nav.settings) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") }
            }
            Gap(16.dp)
            SmartAddBar(
                defaultDay = selected.takeIf { it != today },
                placeholder = if (selected == today) "Try “call Parth at 9 am” or “wake me at 6:30”"
                else "Add to ${selected.format(DateTimeFormatter.ofPattern("EEE d MMM"))}: “dentist at 4 pm”",
                onMoreOptions = { kind ->
                    when (kind) {
                        SmartKind.REMINDER -> nav.newReminder()
                        SmartKind.ALARM -> nav.newAlarm()
                        SmartKind.NOTE -> nav.newNote()
                    }
                },
            )
            Gap(16.dp)

            // Calendar
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer).padding(12.dp).animateContentSize(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        (if (monthView) shownMonth.atDay(1) else selected)
                            .format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                    IconButton(onClick = {
                        if (monthView) shownMonthEpoch = shownMonth.minusMonths(1).atDay(1).toEpochDay()
                        else selectedEpoch = selected.minusWeeks(1).toEpochDay()
                    }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous") }
                    IconButton(onClick = {
                        if (monthView) shownMonthEpoch = shownMonth.plusMonths(1).atDay(1).toEpochDay()
                        else selectedEpoch = selected.plusWeeks(1).toEpochDay()
                    }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
                    FilterChip(selected = !monthView, onClick = { monthView = false }, label = { Text("Week") })
                    FilterChip(selected = monthView, onClick = {
                        monthView = true
                        shownMonthEpoch = selected.withDayOfMonth(1).toEpochDay()
                    }, label = { Text("Month") })
                    Spacer(Modifier.weight(1f))
                    if (selected != today) {
                        TextButton(onClick = {
                            selectedEpoch = today.toEpochDay()
                            shownMonthEpoch = today.withDayOfMonth(1).toEpochDay()
                        }) { Text("Today") }
                    }
                }
                Gap(4.dp)
                // Weekday header
                Row(Modifier.fillMaxWidth()) {
                    DayOfWeek.entries.forEach { d ->
                        Text(
                            d.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                var d = rangeFrom
                while (!d.isAfter(rangeTo)) {
                    val weekStart = d
                    Row(Modifier.fillMaxWidth()) {
                        for (i in 0..6) {
                            val day = weekStart.plusDays(i.toLong())
                            DayCell(
                                day = day,
                                today = today,
                                selected = day == selected,
                                inMonth = !monthView || YearMonth.from(day) == shownMonth,
                                reminders = reminderDays[day]?.count { !it.reminder.completed } ?: 0,
                                notes = noteDays[day]?.size ?: 0,
                                events = eventDays[day]?.size ?: 0,
                                alarms = CalendarModel.alarmsOn(day, alarms, groups, now).isNotEmpty(),
                                modifier = Modifier.weight(1f),
                            ) { selectedEpoch = day.toEpochDay() }
                        }
                    }
                    d = d.plusWeeks(1)
                }
                Row(Modifier.padding(start = 8.dp, top = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Legend(Accent, "Reminders")
                    Legend(NoteGreen, "Notes")
                    Legend(AlarmOrange, "Alarms")
                    if (showCalendar) Legend(EventPurple, "Events")
                }
            }
            if (!settings.deviceCalendar && !calendarPromptHidden) {
                Gap(12.dp)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer).padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Event, null, tint = EventPurple)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Show your phone's calendar events here too",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        if (DeviceCalendar.hasPermission(context)) {
                            app.settings.update(app.settings.current.copy(deviceCalendar = true), force = true)
                        } else {
                            calendarPermission.launch(android.Manifest.permission.READ_CALENDAR)
                        }
                    }) { Text("Show") }
                    TextButton(onClick = { calendarPromptHidden = true }) { Text("Not now") }
                }
            }
            Gap(20.dp)

            // Agenda for the selected day
            Text(
                when (selected) {
                    today -> "Today"
                    today.plusDays(1) -> "Tomorrow"
                    today.minusDays(1) -> "Yesterday"
                    else -> selected.format(DateTimeFormatter.ofPattern("EEEE, d MMMM"))
                },
                style = MaterialTheme.typography.titleLarge,
            )
            Gap(8.dp)
            if (selected == today && overdue.isNotEmpty()) {
                Text("${overdue.size} overdue", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                overdue.take(5).forEach { r ->
                    AgendaReminder(
                        com.exosynk.planr.today.ReminderOccurrence(r, com.exosynk.planr.reminder.ReminderTime.toLocal(r.dueAt!!), true),
                        overdue = true,
                        onOpen = { nav.openReminder(r.id) },
                    )
                }
                Gap(8.dp)
            }
            val dayEvents = eventDays[selected].orEmpty()
            if (agenda.isEmpty && dayEvents.isEmpty()) {
                Text(
                    "Nothing planned. Type above to add a reminder, alarm or note for this day.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            dayEvents.forEach { ev ->
                AgendaRow(
                    icon = Icons.Outlined.Event,
                    tint = Color(ev.color).takeIf { ev.color != 0 } ?: EventPurple,
                    title = ev.title,
                    subtitle = listOfNotNull(
                        if (ev.allDay) "All day" else ev.begin.toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)) +
                            "–" + ev.end.toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)),
                        ev.location,
                        ev.calendar.takeIf { it.isNotBlank() },
                    ).joinToString(" · "),
                    onClick = { runCatching { context.startActivity(DeviceCalendar.openIntent(ev.eventId)) } },
                )
            }
            agenda.alarms.forEach { (a, t) ->
                AgendaRow(
                    icon = Icons.Outlined.Alarm,
                    tint = AlarmOrange,
                    title = t.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)) + (if (a.label.isNotBlank()) " · ${a.label}" else ""),
                    subtitle = if (selected == today) {
                        AlarmTime.next(a, now)?.takeIf { it.toLocalDate() == today }?.let { AlarmTime.untilText(now, it) } ?: "Rang already"
                    } else null,
                    onClick = { nav.openAlarm(a.id) },
                )
            }
            agenda.reminders.forEach { occ ->
                AgendaReminder(occ, overdue = false, onOpen = { nav.openReminder(occ.reminder.id) })
            }
            agenda.notes.forEach { n ->
                AgendaRow(
                    icon = Icons.AutoMirrored.Outlined.StickyNote2,
                    tint = NoteGreen,
                    title = n.title.ifBlank { n.preview.lineSequence().firstOrNull().orEmpty() }.ifBlank { "Note" },
                    subtitle = n.preview.takeIf { n.title.isNotBlank() && it.isNotBlank() }?.lineSequence()?.firstOrNull(),
                    onClick = { nav.openNote(n.id) },
                )
            }
            Gap(24.dp)

            // Quick actions
            Text("Quick actions", style = MaterialTheme.typography.titleLarge)
            Gap(8.dp)
            val actions = listOf(
                Triple(Icons.Outlined.Alarm, "Alarm", nav.newAlarm),
                Triple(Icons.Outlined.TaskAlt, "Reminder", nav.newReminder),
                Triple(Icons.AutoMirrored.Outlined.StickyNote2, "Note", nav.newNote),
                Triple(Icons.Outlined.Timer, "Focus", nav.focus),
                Triple(Icons.Outlined.DarkMode, "Off-Grid", nav.offGrid),
                Triple(Icons.Outlined.AutoMode, "Routines", nav.routines),
            )
            actions.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (icon, label, go) -> QuickAction(icon, label, Modifier.weight(1f), go) }
                }
                Gap(10.dp)
            }
            Gap(10.dp)

            // Habit glance
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(Brush.horizontalGradient(listOf(AccentSoft, MaterialTheme.colorScheme.surfaceContainer)))
                    .clickable(onClick = nav.habit).padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Screen time today", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatDuration(screenMs), style = MaterialTheme.typography.headlineMedium)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Level ${progress.stats.level}", style = MaterialTheme.typography.titleMedium)
                    Text("Streak ${progress.stats.currentStreak}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDate,
    today: LocalDate,
    selected: Boolean,
    inMonth: Boolean,
    reminders: Int,
    notes: Int,
    alarms: Boolean,
    events: Int = 0,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier.padding(2.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape)
                .background(if (selected) Accent else Color.Transparent)
                .then(if (day == today && !selected) Modifier.border(1.5.dp, Accent, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${day.dayOfMonth}",
                fontWeight = if (day == today || selected) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    selected -> MaterialTheme.colorScheme.onPrimary
                    !inMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
        Row(Modifier.height(8.dp).padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            if (reminders > 0) Dot(Accent)
            if (notes > 0) Dot(NoteGreen)
            if (alarms) Dot(AlarmOrange)
            if (events > 0) Dot(EventPurple)
        }
    }
}

@Composable
private fun Dot(c: Color) = Box(Modifier.size(5.dp).clip(CircleShape).background(c))

@Composable
private fun Legend(c: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(c)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AgendaRow(icon: ImageVector, tint: Color, title: String, subtitle: String?, onClick: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(tint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing()
    }
}

@Composable
private fun AgendaReminder(occ: ReminderOccurrence, overdue: Boolean, onOpen: () -> Unit) {
    val app = PlanrApp.instance
    val context = LocalContext.current
    val r = occ.reminder
    val done = r.completed
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer).clickable(onClick = onOpen).padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = {
                app.appScope.launch {
                    if (done) Reminders.reopen(context.applicationContext, r) else Reminders.complete(context.applicationContext, r.id)
                }
            },
            enabled = occ.isNext || done,
        ) {
            Icon(
                if (done) Icons.Outlined.CheckCircle else Icons.Outlined.Circle,
                contentDescription = if (done) "Mark not done" else "Complete",
                tint = if (done) Success else Accent,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                r.title,
                style = MaterialTheme.typography.titleMedium,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (r.allDay) "All day" else occ.at.toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))) +
                        (if (overdue) " · " + occ.at.toLocalDate().format(DateTimeFormatter.ofPattern("d MMM")) else ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (r.repeatRule != Repeat.NONE) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Outlined.Repeat, "Repeats", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        r.contact?.let { c ->
            IconButton(onClick = {
                ContactActions.intent(context, c)?.let { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
            }) { Icon(Icons.Outlined.Call, contentDescription = "${c.action.verb} ${c.name}", tint = Success) }
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.aspectRatio(1.25f).clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer).clickable(onClick = onClick).padding(12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(AccentSoft), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Accent)
        }
        Gap(6.dp)
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}
