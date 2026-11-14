package com.exosynk.planr.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AutoMode
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.exosynk.planr.PlanrApp
import com.exosynk.planr.reminder.dueText
import com.exosynk.planr.ui.components.daysText
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** One result row. */
private data class Hit(val icon: ImageVector, val title: String, val subtitle: String, val open: () -> Unit)

/** Search across alarms, reminders, notes and routines. */
@Composable
fun SearchScreen(
    modifier: Modifier,
    onBack: () -> Unit,
    openAlarm: (Long) -> Unit,
    openReminder: (Long) -> Unit,
    openNote: (Long) -> Unit,
    openRoutine: (Long) -> Unit,
) {
    BackHandler(onBack = onBack)
    val db = PlanrApp.instance.db
    val alarms by db.alarmDao().observeAll().collectAsState(initial = emptyList())
    val reminders by db.reminderDao().observeAll().collectAsState(initial = emptyList())
    val notes by db.noteDao().observeAll().collectAsState(initial = emptyList())
    val routines by db.routineDao().observeAll().collectAsState(initial = emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val q = query.trim()
    fun String.has() = q.isNotEmpty() && contains(q, ignoreCase = true)
    val sections: List<Pair<String, List<Hit>>> = if (q.isEmpty()) emptyList() else listOf(
        "Reminders" to reminders.filter { it.title.has() || it.notes.has() || (it.contactName?.has() == true) }.map { r ->
            Hit(Icons.Outlined.TaskAlt, r.title, listOfNotNull(
                r.dueAt?.let { dueText(r) } ?: "Anytime",
                "done".takeIf { r.completed },
                r.contactName,
            ).joinToString(" · ")) { openReminder(r.id) }
        },
        "Notes" to notes.filter { it.title.has() || it.preview.has() }.map { n ->
            Hit(Icons.AutoMirrored.Outlined.StickyNote2, n.title.ifBlank { n.preview.lineSequence().firstOrNull().orEmpty() }.ifBlank { "Note" },
                n.preview.lineSequence().firstOrNull { it.contains(q, true) } ?: n.preview.lineSequence().firstOrNull().orEmpty()) { openNote(n.id) }
        },
        "Alarms" to alarms.filter { a ->
            val time = LocalTime.of(a.hour, a.minute).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
            a.label.has() || time.has()
        }.map { a ->
            Hit(Icons.Outlined.Alarm,
                LocalTime.of(a.hour, a.minute).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)) + (if (a.label.isNotBlank()) " · ${a.label}" else ""),
                daysText(a.daysMask) + if (a.enabled) "" else " · off") { openAlarm(a.id) }
        },
        "Routines" to routines.filter { it.name.has() }.map { r ->
            Hit(Icons.Outlined.AutoMode, r.name, if (r.enabled) "On" else "Off") { openRoutine(r.id) }
        },
    ).filter { it.second.isNotEmpty() }

    Column(modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(80) },
                placeholder = { Text("Search reminders, notes, alarms, routines") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, contentDescription = "Clear") }
                },
                singleLine = true,
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
        }
        LazyColumn(contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 32.dp)) {
            if (q.isNotEmpty() && sections.isEmpty()) {
                item { Text("Nothing matches “$q”.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            sections.forEach { (title, hits) ->
                item(key = "h-$title") {
                    Text("$title · ${hits.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                }
                items(hits) { h ->
                    Row(
                        Modifier.fillMaxWidth().clickable(onClick = h.open).padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(h.icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(h.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (h.subtitle.isNotBlank()) {
                                Text(h.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}
