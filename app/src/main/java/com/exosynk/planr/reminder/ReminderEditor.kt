package com.exosynk.planr.reminder

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.exosynk.planr.PlanrApp
import com.exosynk.planr.notes.Note
import com.exosynk.planr.smart.ContactBar
import com.exosynk.planr.smart.SmartParser
import com.exosynk.planr.smart.whenText
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material3.TextButton
import com.exosynk.planr.ui.components.Chips
import com.exosynk.planr.ui.components.EditorPage
import com.exosynk.planr.ui.components.Gap
import com.exosynk.planr.ui.components.SectionCard
import com.exosynk.planr.ui.components.SwitchRow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Create or edit a reminder. [fromNoteId] pre-fills a new reminder from a note and links them.
 */
@Composable
fun ReminderEditor(
    modifier: Modifier,
    reminderId: Long,
    fromNoteId: Long?,
    onOpenNote: (Long) -> Unit,
    onDone: () -> Unit,
) {
    val app = PlanrApp.instance
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val categories by app.db.reminderCategoryDao().observeAll().collectAsState(initial = emptyList())
    var loaded by remember { mutableStateOf(false) }
    var r by remember {
        // New reminders default to the next whole hour.
        val at = LocalDateTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0)
        mutableStateOf(Reminder(title = "", dueAt = ReminderTime.toMillis(at)))
    }

    LaunchedEffect(reminderId, fromNoteId) {
        val draft = com.exosynk.planr.smart.Drafts.reminder
        com.exosynk.planr.smart.Drafts.reminder = null
        if (reminderId != 0L) {
            app.db.reminderDao().get(reminderId)?.let { r = it }
        } else if (draft != null) {
            r = draft
        } else if (fromNoteId != null) {
            app.db.noteDao().get(fromNoteId)?.let { n: Note ->
                r = r.copy(title = n.title.ifBlank { n.preview.lineSequence().firstOrNull().orEmpty() }.take(120), noteId = n.id)
            }
        }
        loaded = true
    }
    if (!loaded) return

    val linkedNote by produceState<Note?>(null, r.noteId) { value = r.noteId?.let { app.db.noteDao().get(it) } }
    val due = r.dueAt?.let { ReminderTime.toLocal(it) }

    fun setDue(t: LocalDateTime?) {
        r = r.copy(dueAt = t?.let { ReminderTime.toMillis(it) })
    }

    EditorPage(
        modifier = modifier,
        title = if (reminderId == 0L) "New reminder" else "Edit reminder",
        onBack = onDone,
        onSave = {
            scope.launch {
                Reminders.save(context.applicationContext, r.copy(title = r.title.trim(), completed = false, completedAt = null))
                onDone()
            }
        },
        saveEnabled = r.title.isNotBlank(),
        actions = {
            if (reminderId != 0L) {
                IconButton(onClick = {
                    scope.launch { Reminders.delete(context.applicationContext, reminderId); onDone() }
                }) { Icon(Icons.Outlined.Delete, contentDescription = "Delete reminder") }
            }
        },
    ) {
        OutlinedTextField(
            value = r.title,
            onValueChange = { r = r.copy(title = it.take(120)) },
            label = { Text("Reminder") },
            placeholder = { Text("e.g. Call Parth at 9 am tomorrow") },
            modifier = Modifier.fillMaxWidth(),
        )
        // Smart: understand dates, times, repeats and "call/message someone" in the title.
        val smart = remember(r.title) { SmartParser.parse(r.title) }
        val smartAt = smart.at
        val smartDiffers = smartAt != null && (r.dueAt == null || ReminderTime.toMillis(smartAt) != r.dueAt || smart.allDay != r.allDay)
        if (smartDiffers || (smart.repeat != Repeat.NONE && smart.repeat != r.repeatRule)) {
            Gap(6.dp)
            AssistChip(
                onClick = {
                    r = r.copy(
                        title = smart.title.ifBlank { r.title },
                        dueAt = smartAt?.let { ReminderTime.toMillis(it) } ?: r.dueAt,
                        allDay = if (smartAt != null) smart.allDay else r.allDay,
                        repeat = if (smart.repeat != Repeat.NONE) smart.repeat.name else r.repeat,
                    )
                },
                leadingIcon = { Icon(Icons.Default.AutoAwesome, null) },
                label = {
                    Text(
                        "Set " + listOfNotNull(
                            smartAt?.let { whenText(it, smart.allDay) },
                            smart.repeat.takeIf { it != Repeat.NONE }?.title?.lowercase(),
                        ).joinToString(", ") + " and tidy title",
                    )
                },
            )
        }
        Gap(8.dp)
        // Contact: from "call X" in the title, or linked by hand.
        var linking by remember { mutableStateOf(false) }
        val contactQuery = smart.contact?.nameQuery
        when {
            r.contact != null && !linking -> AssistChip(
                onClick = { linking = true },
                leadingIcon = { Icon(Icons.Outlined.Call, null) },
                label = { Text("${r.contact!!.action.verb} ${r.contact!!.name} · ${r.contact!!.number}") },
                trailingIcon = {
                    Icon(Icons.Default.Close, contentDescription = "Remove contact",
                        modifier = Modifier.clickable { r = r.withContact(null) })
                },
            )
            contactQuery != null || linking -> ContactBar(
                query = contactQuery ?: "",
                action = smart.contact?.action ?: r.contact?.action ?: ContactAction.CALL,
                selected = r.contact,
                onSelect = { r = r.withContact(it); if (it != null) linking = false },
            )
            else -> TextButton(onClick = { linking = true }) {
                Icon(Icons.Outlined.Call, null)
                Text("  Link a contact (adds a Call button)")
            }
        }
        Gap(8.dp)
        OutlinedTextField(
            value = r.notes,
            onValueChange = { r = r.copy(notes = it.take(1000)) },
            label = { Text("Details (optional)") },
            modifier = Modifier.fillMaxWidth().height(110.dp),
        )
        linkedNote?.let { n ->
            Gap(8.dp)
            AssistChip(
                onClick = { onOpenNote(n.id) },
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.StickyNote2, null) },
                label = { Text("Note: " + n.title.ifBlank { "Untitled" }) },
            )
        }
        Gap(12.dp)

        SectionCard {
            SwitchRow("Date & time", if (due == null) "No date: shows under Anytime" else null, due != null) { on ->
                setDue(if (on) LocalDateTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0) else null)
            }
            if (due != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        DatePickerDialog(context, { _, y, m, d ->
                            setDue(LocalDate.of(y, m + 1, d).atTime(due.toLocalTime()))
                        }, due.year, due.monthValue - 1, due.dayOfMonth).show()
                    }) { Text(due.toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))) }
                    if (!r.allDay) {
                        OutlinedButton(onClick = {
                            TimePickerDialog(context, { _, h, min ->
                                setDue(due.toLocalDate().atTime(LocalTime.of(h, min)))
                            }, due.hour, due.minute, DateFormat.is24HourFormat(context)).show()
                        }) { Text(due.toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))) }
                    }
                }
                SwitchRow("All day", "Alerts at ${Reminder.ALL_DAY_ALERT_HOUR} AM", r.allDay) { r = r.copy(allDay = it) }
                Chips("Repeat", Repeat.entries.toList(), r.repeatRule, { it.title }) { r = r.copy(repeat = it.name) }
                Gap(8.dp)
                Chips(
                    "Early reminder",
                    listOf(0, 5, 15, 30, 60, 24 * 60),
                    r.earlyMinutes,
                    { m -> when (m) { 0 -> "None"; 60 -> "1 hour"; 1440 -> "1 day"; else -> "$m min" } },
                ) { r = r.copy(earlyMinutes = it) }
                Gap(8.dp)
                Chips("Alert", AlertStyle.entries.toList(), r.style, { it.title }) { r = r.copy(alertStyle = it.name) }
            }
        }
        Gap(12.dp)

        SectionCard {
            if (categories.isEmpty()) {
                Text("Category", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Add categories (Work, Groceries…) from the Reminders screen.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Chips(
                    "Category",
                    listOf<Long?>(null) + categories.map { it.id },
                    r.categoryId,
                    { id -> categories.firstOrNull { it.id == id }?.name ?: "None" },
                ) { r = r.copy(categoryId = it) }
            }
            SwitchRow("Pin to top", null, r.pinned) { r = r.copy(pinned = it) }
        }
        Gap(32.dp)
    }
}
