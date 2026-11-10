package com.exosynk.planr.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.NotificationAdd
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.exosynk.planr.PlanrApp
import com.exosynk.planr.reminder.dueText
import com.exosynk.planr.ui.components.EditorPage
import com.exosynk.planr.ui.components.Gap
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Notes save automatically when you leave. [noteId]: an id, 0 = new note, [NEW_CHECKLIST] = new checklist.
 * "Remind me" creates a reminder linked to the note.
 */
@Composable
fun NoteEditor(
    modifier: Modifier,
    noteId: Long,
    onRemind: (noteId: Long) -> Unit,
    onOpenReminder: (Long) -> Unit,
    onDone: () -> Unit,
) {
    val app = PlanrApp.instance
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(noteId <= 0L) }
    var note by remember {
        mutableStateOf((if (noteId == 0L) com.exosynk.planr.smart.Drafts.note else null) ?: Note(isChecklist = noteId == NEW_CHECKLIST))
    }
    LaunchedEffect(Unit) { com.exosynk.planr.smart.Drafts.note = null }
    var items by remember { mutableStateOf(if (noteId == NEW_CHECKLIST) listOf(ChecklistItem("")) else emptyList()) }
    var palette by remember { mutableStateOf(false) }

    LaunchedEffect(noteId) {
        if (noteId > 0L) {
            app.db.noteDao().get(noteId)?.let { note = it; items = it.items }
            loaded = true
        }
    }
    if (!loaded) return

    val linked by remember(note.id) {
        if (note.id == 0L) flowOf(emptyList()) else app.db.reminderDao().observeForNote(note.id)
    }.collectAsState(initial = emptyList())

    fun current(): Note = note.copy(
        checklist = if (note.isChecklist) Checklist.encode(items.filter { it.text.isNotBlank() }) else note.checklist,
        updatedAt = System.currentTimeMillis(),
    )

    /** Saves (or drops an empty new note) and returns the id, or 0 if nothing was kept. */
    suspend fun persist(): Long {
        val n = current()
        if (n.isEmpty) {
            if (n.id != 0L) {
                app.db.reminderDao().unlinkNote(n.id)
                app.db.noteDao().delete(n.id)
            }
            return 0L
        }
        val row = app.db.noteDao().upsert(n)
        val id = if (n.id != 0L) n.id else row
        note = n.copy(id = id)
        return id
    }

    fun close() = scope.launch { persist(); onDone() }

    EditorPage(
        modifier = modifier,
        title = "",
        onBack = { close() },
        onSave = null,
        actions = {
            IconButton(onClick = { note = note.copy(pinned = !note.pinned) }) {
                Icon(
                    Icons.Outlined.PushPin,
                    contentDescription = if (note.pinned) "Unpin" else "Pin",
                    tint = if (note.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = {
                scope.launch { val id = persist(); if (id != 0L) onRemind(id) }
            }) { Icon(Icons.Outlined.NotificationAdd, contentDescription = "Remind me") }
            IconButton(onClick = {
                note = if (note.isChecklist) {
                    note.copy(isChecklist = false, body = listOf(note.body, Checklist.toText(items)).filter { it.isNotBlank() }.joinToString("\n"))
                } else {
                    items = Checklist.fromText(note.body).ifEmpty { listOf(ChecklistItem("")) }
                    note.copy(isChecklist = true, body = "")
                }
            }) { Icon(Icons.Outlined.Checklist, contentDescription = "Toggle checklist") }
            IconButton(onClick = { palette = !palette }) { Icon(Icons.Outlined.Palette, contentDescription = "Colour") }
            IconButton(onClick = {
                note = note.copy(archived = !note.archived)
                close()
            }) {
                Icon(if (note.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive, contentDescription = "Archive")
            }
            IconButton(onClick = {
                scope.launch {
                    if (note.id != 0L) {
                        app.db.reminderDao().unlinkNote(note.id)
                        app.db.noteDao().delete(note.id)
                    }
                    onDone()
                }
            }) { Icon(Icons.Outlined.Delete, contentDescription = "Delete note") }
        },
    ) {
        if (palette) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                NOTE_COLORS.indices.forEach { i ->
                    Box(
                        Modifier.size(32.dp).background(noteColor(i), CircleShape)
                            .border(
                                if (i == note.color) 2.dp else 1.dp,
                                if (i == note.color) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                CircleShape,
                            )
                            .clickable { note = note.copy(color = i) },
                    )
                }
            }
            Gap(8.dp)
        }
        val fieldColors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        )
        Box(Modifier.fillMaxWidth().background(noteColor(note.color), MaterialTheme.shapes.large)) {
            androidx.compose.foundation.layout.Column {
                TextField(
                    value = note.title,
                    onValueChange = { note = note.copy(title = it.take(120)) },
                    placeholder = { Text("Title") },
                    textStyle = MaterialTheme.typography.titleLarge,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (note.isChecklist) {
                    items.forEachIndexed { i, item ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = item.done, onCheckedChange = { d ->
                                items = items.toMutableList().also { it[i] = item.copy(done = d) }
                            })
                            TextField(
                                value = item.text,
                                onValueChange = { t -> items = items.toMutableList().also { it[i] = item.copy(text = t.take(200)) } },
                                placeholder = { Text("List item") },
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    textDecoration = if (item.done) TextDecoration.LineThrough else null,
                                ),
                                colors = fieldColors,
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { items = items.toMutableList().also { it.removeAt(i) } }) {
                                Icon(Icons.Default.Close, contentDescription = "Remove item")
                            }
                        }
                    }
                    TextButton(onClick = { items = items + ChecklistItem("") }) {
                        Icon(Icons.Default.Add, null)
                        Text("Add item")
                    }
                } else {
                    TextField(
                        value = note.body,
                        onValueChange = { note = note.copy(body = it.take(20_000)) },
                        placeholder = { Text("Note") },
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp),
                    )
                }
            }
        }
        if (linked.isNotEmpty()) {
            Gap(12.dp)
            Text("Reminders", style = MaterialTheme.typography.labelLarge)
            linked.forEach { r ->
                AssistChip(
                    onClick = { scope.launch { persist(); onOpenReminder(r.id) } },
                    leadingIcon = { Icon(Icons.Outlined.Notifications, null) },
                    label = { Text(r.title + (r.dueAt?.let { " · " + dueText(r) } ?: "") + if (r.completed) " · done" else "") },
                )
            }
        }
        Gap(32.dp)
    }
}
