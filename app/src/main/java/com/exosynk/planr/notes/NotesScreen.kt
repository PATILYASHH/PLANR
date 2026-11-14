package com.exosynk.planr.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.exosynk.planr.PlanrApp
import com.exosynk.planr.ui.components.Gap
import com.exosynk.planr.ui.components.ModuleHeader

/** Card colours (index 0 = default surface). Muted so they work on the dark theme. */
val NOTE_COLORS = listOf(
    0x00000000, 0xFF2A3354, 0xFF1F3B30, 0xFF3D3320, 0xFF402626, 0xFF33264A, 0xFF1C3A40, 0xFF3A3A3A,
).map { it.toInt() }

/** The same eight choices, as light pastels for the light theme. */
private val NOTE_COLORS_LIGHT = listOf(
    0x00000000, 0xFFDDE3FF, 0xFFD6F5E5, 0xFFFFF1CC, 0xFFFFDAD6, 0xFFEADCFF, 0xFFD3F2F7, 0xFFE6E7EB,
).map { it.toInt() }

@Composable
fun noteColor(index: Int): Color {
    if (index <= 0 || index >= NOTE_COLORS.size) return MaterialTheme.colorScheme.surfaceContainer
    return Color(if (com.exosynk.planr.ui.theme.isLightScheme()) NOTE_COLORS_LIGHT[index] else NOTE_COLORS[index])
}

/** [onOpen] gets a note id, 0 for a new text note or -1 for a new checklist. */
@Composable
fun NotesScreen(modifier: Modifier, onOpen: (Long) -> Unit) {
    val app = PlanrApp.instance
    val notes by app.db.noteDao().observeAll().collectAsState(initial = emptyList())
    val reminders by app.db.reminderDao().observeAll().collectAsState(initial = emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    var archived by rememberSaveable { mutableStateOf(false) }

    val withReminder = reminders.filter { !it.completed }.mapNotNull { it.noteId }.toSet()
    val shown = notes.filter { it.archived == archived }.filter { n ->
        query.isBlank() || n.title.contains(query, true) || n.preview.contains(query, true)
    }

    Box(modifier.fillMaxSize()) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            contentPadding = PaddingValues(20.dp, 24.dp, 20.dp, 120.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalItemSpacing = 10.dp,
        ) {
            item(span = StaggeredGridItemSpan.FullLine) {
                Column {
                    ModuleHeader("Notes", "Ideas, lists and anything worth keeping.")
                    Gap(12.dp)
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        placeholder = { Text("Search notes") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Gap(8.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !archived, onClick = { archived = false }, label = { Text("Notes") })
                        FilterChip(selected = archived, onClick = { archived = true }, label = { Text("Archived") })
                    }
                    if (shown.isEmpty()) {
                        Gap(16.dp)
                        Text(
                            when {
                                query.isNotBlank() -> "No notes match “$query”."
                                archived -> "No archived notes."
                                else -> "No notes yet. Tip: during Off-Grid you can jot thoughts from the lock screen."
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(shown, key = { it.id }) { n -> NoteCard(n, n.id in withReminder) { onOpen(n.id) } }
        }
        Column(Modifier.align(Alignment.BottomEnd).padding(20.dp), horizontalAlignment = Alignment.End) {
            SmallFloatingActionButton(onClick = { onOpen(NEW_CHECKLIST) }) {
                Icon(Icons.Outlined.Checklist, contentDescription = "New checklist")
            }
            Gap(12.dp)
            ExtendedFloatingActionButton(
                onClick = { onOpen(0L) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("New note") },
            )
        }
    }
}

const val NEW_CHECKLIST = -1L

@Composable
private fun NoteCard(n: Note, hasReminder: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = noteColor(n.color)),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                if (n.title.isNotBlank()) {
                    Text(
                        n.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Box(Modifier.weight(1f))
                }
                if (n.pinned) Icon(Icons.Outlined.PushPin, "Pinned", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                if (hasReminder) Icon(Icons.Outlined.Notifications, "Has a reminder", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (n.preview.isNotBlank()) {
                if (n.title.isNotBlank()) Gap(4.dp)
                Text(
                    n.preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 10,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
