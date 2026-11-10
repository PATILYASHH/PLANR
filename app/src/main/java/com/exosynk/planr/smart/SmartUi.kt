package com.exosynk.planr.smart

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.exosynk.planr.PlanrApp
import com.exosynk.planr.alarm.Alarm
import com.exosynk.planr.alarm.AlarmTime
import com.exosynk.planr.alarm.Alarms
import com.exosynk.planr.alarm.toastOnMain
import com.exosynk.planr.notes.Note
import com.exosynk.planr.reminder.ContactAction
import com.exosynk.planr.reminder.Reminder
import com.exosynk.planr.reminder.ReminderContact
import com.exosynk.planr.reminder.ReminderTime
import com.exosynk.planr.reminder.Reminders
import com.exosynk.planr.reminder.Repeat
import com.exosynk.planr.ui.components.daysText
import com.exosynk.planr.ui.theme.Accent
import com.exosynk.planr.ui.theme.AccentSoft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Hand-off from the smart bar to a full editor ("More options"). Consumed once. */
object Drafts {
    var reminder: Reminder? = null
    var alarm: Alarm? = null
    var note: Note? = null
}

/** "Tomorrow, 9:00 AM" / "Fri 14 Nov, all day" / "in 20 minutes". */
fun whenText(at: LocalDateTime, allDay: Boolean, now: LocalDateTime = LocalDateTime.now()): String {
    val today = now.toLocalDate()
    val day = when (at.toLocalDate()) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> at.toLocalDate().format(DateTimeFormatter.ofPattern("EEE d MMM"))
    }
    return if (allDay) "$day, all day" else "$day, " + at.toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
}

/**
 * Type it the way you'd say it. Live preview of what PLANR understood, a kind
 * switch, and, for "call/message someone", a contact selection bar.
 */
@Composable
fun SmartAddBar(
    modifier: Modifier = Modifier,
    placeholder: String = "Try “call Parth at 9 am” or “wake me at 6:30”",
    defaultDay: LocalDate? = null,
    onMoreOptions: (SmartKind) -> Unit,
) {
    val app = PlanrApp.instance
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var kindOverride by remember { mutableStateOf<SmartKind?>(null) }
    var contact by remember { mutableStateOf<ReminderContact?>(null) }
    var nowTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); nowTick++ } }

    val parsed = remember(text, nowTick) { SmartParser.parse(text) }.let { r ->
        // A day picked in the calendar applies when the text names no date.
        if (defaultDay != null && r.kind != SmartKind.NOTE && r.at == null) {
            r.copy(at = defaultDay.atStartOfDay(), allDay = true)
        } else if (defaultDay != null && r.at != null && !text.contains(Regex("tomorrow|today|tonight|mon|tue|wed|thu|fri|sat|sun|\\d+(st|nd|rd|th)|/|jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec|in \\d", RegexOption.IGNORE_CASE))) {
            r.copy(at = defaultDay.atTime(r.at.toLocalTime()))
        } else r
    }
    val kind = kindOverride ?: parsed.kind
    LaunchedEffect(parsed.contact?.nameQuery) { if (parsed.contact == null) contact = null }

    fun reset() {
        text = ""; kindOverride = null; contact = null
    }

    fun buildReminder() = Reminder(
        title = parsed.title.ifBlank { text.trim() },
        dueAt = parsed.at?.let { ReminderTime.toMillis(it) },
        allDay = parsed.allDay,
        repeat = parsed.repeat.name,
    ).withContact(contact)

    fun buildAlarm(): Alarm? {
        val at = parsed.at ?: return null
        val days = parsed.alarmDays
        val tomorrowOrToday = AlarmTime.next(Alarm(hour = at.hour, minute = at.minute), LocalDateTime.now())?.toLocalDate()
        return Alarm(
            label = parsed.title,
            hour = at.hour,
            minute = at.minute,
            daysMask = days,
            date = if (days == 0 && at.toLocalDate() != tomorrowOrToday) at.toLocalDate().toString() else null,
        )
    }

    fun buildNote() = Note(title = parsed.title.ifBlank { text.trim().take(60) }, body = parsed.body)

    val canAdd = text.isNotBlank() && (kind != SmartKind.ALARM || parsed.at != null)

    fun add() {
        val ctx = context.applicationContext
        scope.launch {
            val msg = when (kind) {
                SmartKind.REMINDER -> {
                    val r = buildReminder()
                    Reminders.save(ctx, r)
                    "Reminder: ${r.title}" + (parsed.at?.let { " · " + whenText(it, parsed.allDay) } ?: "")
                }
                SmartKind.ALARM -> {
                    val a = buildAlarm() ?: return@launch
                    val id = Alarms.save(ctx, a)
                    app.db.alarmDao().get(id)?.let { AlarmTime.next(it, LocalDateTime.now()) }
                        ?.let { AlarmTime.untilText(LocalDateTime.now(), it) } ?: "Alarm saved"
                }
                SmartKind.NOTE -> {
                    app.db.noteDao().upsert(buildNote())
                    "Note saved"
                }
            }
            toastOnMain(ctx, msg)
            reset()
        }
    }

    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, if (text.isBlank()) Color.Transparent else Accent.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
            .padding(12.dp),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(300); kindOverride = null },
            placeholder = { Text(placeholder, maxLines = 2) },
            leadingIcon = { Icon(Icons.Default.AutoAwesome, null, tint = Accent) },
            trailingIcon = {
                if (text.isNotEmpty()) {
                    IconButton(onClick = { if (canAdd) add() }, enabled = canAdd) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Add")
                    }
                }
            },
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        if (text.isBlank()) return@Column

        Spacer(Modifier.size(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SmartKind.entries.forEach { k ->
                FilterChip(
                    selected = kind == k,
                    onClick = { kindOverride = k },
                    label = { Text(k.name.lowercase().replaceFirstChar { it.uppercase() }) },
                )
            }
        }
        Spacer(Modifier.size(4.dp))
        // What PLANR understood
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (kind != SmartKind.NOTE) {
                val at = parsed.at
                InfoChip(
                    if (kind == SmartKind.ALARM) Icons.Outlined.Alarm else Icons.Outlined.Event,
                    when {
                        at == null && kind == SmartKind.ALARM -> "Add a time, e.g. 6:30 am"
                        at == null -> "No date (anytime)"
                        else -> whenText(at, parsed.allDay)
                    },
                )
                if (kind == SmartKind.ALARM && parsed.alarmDays != 0) InfoChip(Icons.Outlined.Repeat, daysText(parsed.alarmDays))
                if (kind == SmartKind.REMINDER && parsed.repeat != Repeat.NONE) InfoChip(Icons.Outlined.Repeat, parsed.repeat.title)
            }
            if (parsed.title.isNotBlank()) {
                Text(
                    "“${parsed.title}”",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (kind == SmartKind.REMINDER) {
            parsed.contact?.let { intent ->
                Spacer(Modifier.size(8.dp))
                ContactBar(
                    query = intent.nameQuery,
                    action = intent.action,
                    selected = contact,
                    onSelect = { contact = it },
                )
            }
        }
        Spacer(Modifier.size(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {
                when (kind) {
                    SmartKind.REMINDER -> Drafts.reminder = buildReminder()
                    SmartKind.ALARM -> Drafts.alarm = buildAlarm() ?: Alarm(label = parsed.title)
                    SmartKind.NOTE -> Drafts.note = buildNote()
                }
                onMoreOptions(kind)
                reset()
            }) { Text("More options") }
            Spacer(Modifier.weight(1f))
            FilledTonalButton(onClick = { add() }, enabled = canAdd) {
                Text(
                    when (kind) {
                        SmartKind.REMINDER -> "Add reminder"
                        SmartKind.ALARM -> "Set alarm"
                        SmartKind.NOTE -> "Save note"
                    }
                )
            }
        }
    }
}

@Composable
private fun InfoChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    AssistChip(onClick = {}, leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) }, label = { Text(text) })
}

/**
 * Contact selection bar for "call/message someone": matching contacts as chips
 * (tap to select), a search box when the name isn't found, and Call/Message/WhatsApp.
 */
@Composable
fun ContactBar(
    query: String,
    action: ContactAction,
    selected: ReminderContact?,
    onSelect: (ReminderContact?) -> Unit,
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(Contacts.hasPermission(context)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var search by remember(query) { mutableStateOf(query) }
    var chosenAction by remember(action) { mutableStateOf(action) }
    val matches by produceState(emptyList<ContactMatch>(), search, granted) {
        value = if (granted) withContext(Dispatchers.IO) { Contacts.search(context, search) } else emptyList()
    }
    // One clear match: pick it automatically (the user can still change it).
    LaunchedEffect(matches) {
        if (selected == null && matches.size == 1 || (selected == null && matches.isNotEmpty() && Contacts.rank(matches[0].name, search) == 0 &&
                matches.drop(1).none { Contacts.rank(it.name, search) == 0 })
        ) {
            onSelect(ReminderContact(matches[0].name, matches[0].number, chosenAction))
        }
    }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(AccentSoft.copy(alpha = 0.45f)).padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Contacts, null, tint = Accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                selected?.let { "${chosenAction.verb} ${it.name} · ${it.number}" } ?: "Who is “$query”?",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (selected != null) {
                IconButton(onClick = { onSelect(null) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Remove contact", modifier = Modifier.size(16.dp))
                }
            }
        }
        if (!granted) {
            Text(
                "Allow contacts so PLANR can find “$query” and add a one-tap Call button. Contacts never leave your phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { permission.launch(Manifest.permission.READ_CONTACTS) }) { Text("Allow contacts") }
            return@Column
        }
        Spacer(Modifier.size(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            matches.forEach { m ->
                val isSel = selected?.name == m.name && selected.number == m.number
                Row(
                    Modifier.clip(RoundedCornerShape(50))
                        .background(if (isSel) Accent else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { onSelect(ReminderContact(m.name, m.number, chosenAction)) }
                        .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(m.name, selected = isSel)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            m.name,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "${m.label} ${m.number}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isSel) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (matches.isEmpty()) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it.take(40) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                placeholder = { Text("Search contacts") },
                supportingText = { Text("No contact matches “$search”") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ContactAction.entries.forEach { a ->
                FilterChip(
                    selected = chosenAction == a,
                    onClick = {
                        chosenAction = a
                        selected?.let { onSelect(it.copy(action = a)) }
                    },
                    label = { Text(a.verb) },
                )
            }
        }
    }
}

@Composable
fun Avatar(name: String, selected: Boolean = false, size: Int = 32) {
    val initials = name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }
    val palette = listOf(0xFF7C9CFF, 0xFF5BD6A0, 0xFFFFB86B, 0xFFC792EA, 0xFF4DD0E1, 0xFFFF6B6B)
    val bg = if (selected) Color.White.copy(alpha = 0.25f) else Color(palette[(name.hashCode() and 0x7fffffff) % palette.size])
    Box(Modifier.size(size.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Text(initials, color = Color(0xFF0B1230), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
    }
}
