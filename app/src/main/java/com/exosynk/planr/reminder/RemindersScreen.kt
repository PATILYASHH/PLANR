package com.exosynk.planr.reminder

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.exosynk.planr.PlanrApp
import com.exosynk.planr.ui.components.Gap
import com.exosynk.planr.ui.components.ModuleHeader
import com.exosynk.planr.ui.theme.Danger
import com.exosynk.planr.ui.theme.Success
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

enum class ReminderView(val title: String) { TODAY("Today"), SCHEDULED("Scheduled"), ANYTIME("Anytime"), ALL("All"), COMPLETED("Completed") }

/** Colours offered for categories (dark-theme friendly). */
val CATEGORY_COLORS = listOf(
    0xFF7C9CFF, 0xFF5BD6A0, 0xFFFFB86B, 0xFFFF6B6B, 0xFFC792EA, 0xFF4DD0E1, 0xFFFFD54F, 0xFFA6A9B6,
).map { it.toInt() }

@Composable
fun RemindersScreen(modifier: Modifier, onEdit: (Long) -> Unit) {
    val app = PlanrApp.instance
    val context = LocalContext.current
    val all by app.db.reminderDao().observeAll().collectAsState(initial = emptyList())
    val categories by app.db.reminderCategoryDao().observeAll().collectAsState(initial = emptyList())
    var view by rememberSaveable { mutableStateOf(ReminderView.TODAY) }
    var category by rememberSaveable { mutableStateOf<Long?>(null) }
    var managing by remember { mutableStateOf(false) }
    val today = LocalDate.now()

    val inCategory = all.filter { category == null || it.categoryId == category }
    val shown = inCategory.filter { view.matches(it, today) }

    Box(modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(20.dp, 24.dp, 20.dp, 96.dp)) {
            item {
                ModuleHeader("Reminders", "Things to do, with or without a time.") {
                    TextButton(onClick = { managing = true }) { Text("Categories") }
                }
                Gap(12.dp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReminderView.entries.forEach { v ->
                        val count = inCategory.count { v.matches(it, today) }
                        FilterChip(
                            selected = view == v,
                            onClick = { view = v },
                            label = { Text(if (v == ReminderView.COMPLETED || count == 0) v.title else "${v.title} $count") },
                        )
                    }
                }
                if (categories.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = category == null, onClick = { category = null }, label = { Text("All lists") })
                        categories.forEach { c ->
                            FilterChip(
                                selected = category == c.id,
                                onClick = { category = c.id },
                                leadingIcon = { Dot(Color(c.color)) },
                                label = { Text(c.name) },
                            )
                        }
                    }
                }
                Gap(8.dp)
                if (view != ReminderView.COMPLETED) {
                    com.exosynk.planr.smart.SmartAddBar(
                        placeholder = "Type it like you'd say it: “call Parth at 9 am”",
                        onMoreOptions = { onEdit(0L) },
                    )
                    Gap(8.dp)
                }
                if (shown.isEmpty()) {
                    Gap(16.dp)
                    Text(emptyText(view), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (view == ReminderView.COMPLETED && shown.isNotEmpty()) {
                    TextButton(onClick = { app.appScope.launch { app.db.reminderDao().deleteCompleted() } }) {
                        Text("Delete all completed", color = Danger)
                    }
                }
            }
            items(shown, key = { it.id }) { r ->
                ReminderRow(r, categories.firstOrNull { it.id == r.categoryId }, today, onClick = { onEdit(r.id) })
            }
        }
        ExtendedFloatingActionButton(
            onClick = { onEdit(0L) },
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text("New reminder") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }

    if (managing) CategoryDialog(categories) { managing = false }
}

private fun ReminderView.matches(r: Reminder, today: LocalDate): Boolean {
    if (this == ReminderView.COMPLETED) return r.completed
    if (r.completed) return false
    val day = r.dueAt?.let { ReminderTime.toLocal(it).toLocalDate() }
    return when (this) {
        ReminderView.TODAY -> day != null && !day.isAfter(today)
        ReminderView.SCHEDULED -> day != null && day.isAfter(today)
        ReminderView.ANYTIME -> day == null
        else -> true
    }
}

private fun emptyText(v: ReminderView) = when (v) {
    ReminderView.TODAY -> "Nothing due today."
    ReminderView.SCHEDULED -> "No upcoming reminders."
    ReminderView.ANYTIME -> "No reminders without a date."
    ReminderView.ALL -> "No reminders yet."
    ReminderView.COMPLETED -> "Nothing completed yet."
}

@Composable
private fun Dot(color: Color) = Box(Modifier.size(10.dp).clip(CircleShape).background(color))

@Composable
private fun ReminderRow(r: Reminder, category: ReminderCategory?, today: LocalDate, onClick: () -> Unit) {
    val app = PlanrApp.instance
    val context = LocalContext.current
    val overdue = !r.completed && r.dueAt != null && r.dueAt < System.currentTimeMillis() &&
        !(r.allDay && ReminderTime.toLocal(r.dueAt).toLocalDate() == today)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = {
            app.appScope.launch {
                if (r.completed) Reminders.reopen(context.applicationContext, r) else Reminders.complete(context.applicationContext, r.id)
            }
        }) {
            Icon(
                if (r.completed) Icons.Outlined.CheckCircle else Icons.Outlined.Circle,
                contentDescription = if (r.completed) "Mark not done" else "Complete",
                tint = if (r.completed) Success else category?.let { Color(it.color) } ?: MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(
                r.title,
                style = MaterialTheme.typography.titleMedium,
                textDecoration = if (r.completed) TextDecoration.LineThrough else null,
                color = if (r.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            val parts = buildList {
                r.dueAt?.let { add(dueText(r, today)) }
                if (r.repeatRule != Repeat.NONE) add(r.repeatRule.title)
                category?.let { add(it.name) }
            }
            if (parts.isNotEmpty()) {
                Text(
                    parts.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (overdue) Danger else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val tint = MaterialTheme.colorScheme.onSurfaceVariant
            if (r.pinned) Icon(Icons.Outlined.PushPin, "Pinned", tint = tint, modifier = Modifier.size(18.dp))
            if (r.repeatRule != Repeat.NONE) Icon(Icons.Outlined.Repeat, "Repeats", tint = tint, modifier = Modifier.size(18.dp))
            if (r.style == AlertStyle.ALARM) Icon(Icons.Outlined.Alarm, "Rings like an alarm", tint = tint, modifier = Modifier.size(18.dp))
            if (r.noteId != null) Icon(Icons.AutoMirrored.Outlined.StickyNote2, "Linked note", tint = tint, modifier = Modifier.size(18.dp))
        }
    }
}

fun dueText(r: Reminder, today: LocalDate = LocalDate.now()): String {
    val t = ReminderTime.toLocal(r.dueAt ?: return "", ZoneId.systemDefault())
    val day = when (t.toLocalDate()) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> t.toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
    return if (r.allDay) day else day + ", " + t.toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
}

@Composable
private fun CategoryDialog(categories: List<ReminderCategory>, onDismiss: () -> Unit) {
    val app = PlanrApp.instance
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(CATEGORY_COLORS[categories.size % CATEGORY_COLORS.size]) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Categories") },
        text = {
            Column {
                categories.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Dot(Color(c.color))
                        Spacer(Modifier.width(10.dp))
                        Text(c.name, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            app.appScope.launch {
                                app.db.reminderDao().uncategorize(c.id)
                                app.db.reminderCategoryDao().delete(c.id)
                            }
                        }) { Text("Delete", color = Danger) }
                    }
                }
                Gap(8.dp)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(24) },
                    label = { Text("New category") },
                    placeholder = { Text("Work, Groceries…") },
                    singleLine = true,
                )
                Gap(8.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CATEGORY_COLORS.forEach { c ->
                        Box(
                            Modifier.size(if (c == color) 28.dp else 22.dp).clip(CircleShape).background(Color(c))
                                .clickable { color = c },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                val n = name.trim()
                name = ""
                app.appScope.launch { app.db.reminderCategoryDao().upsert(ReminderCategory(name = n, color = color)) }
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
