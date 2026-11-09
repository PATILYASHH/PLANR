package com.exosynk.planr.ui.screens

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.exosynk.planr.PlanrApp
import com.exosynk.planr.data.OffGridStore
import com.exosynk.planr.data.UnlockChallenge
import com.exosynk.planr.data.db.Schedule
import com.exosynk.planr.data.db.ScheduleMode
import com.exosynk.planr.data.db.packageSet
import com.exosynk.planr.data.db.toPackageString
import com.exosynk.planr.ui.components.AppPickerSheet
import com.exosynk.planr.ui.components.Gap
import com.exosynk.planr.ui.components.SectionCard
import com.exosynk.planr.ui.components.formatClock
import com.exosynk.planr.ui.theme.Danger
import kotlinx.coroutines.launch

private val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")

@Composable
fun SchedulesScreen(modifier: Modifier) {
    val app = PlanrApp.instance
    val schedules by app.db.scheduleDao().observeAll().collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<Schedule?>(null) }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(20.dp, 24.dp, 20.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Schedules", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Block apps during set hours, or allow only a few.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Gap(12.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        editing = Schedule(
                            name = "Bedtime", startMinute = 23 * 60, endMinute = 7 * 60,
                            mode = ScheduleMode.OFFGRID,
                        )
                    }) { Text("+ Bedtime Off-Grid") }
                    OutlinedButton(onClick = {
                        editing = Schedule(
                            name = "Study hours", startMinute = 17 * 60, endMinute = 20 * 60,
                            daysMask = 0b0011111, mode = ScheduleMode.BLOCK,
                        )
                    }) { Text("+ Study hours") }
                }
            }
            items(schedules, key = { it.id }) { s ->
                SectionCard(onClick = { editing = s }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${formatClock(s.startMinute)} – ${formatClock(s.endMinute)} · " +
                                    "${daysText(s.daysMask)} · " +
                                    modeText(s),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = s.enabled, onCheckedChange = { on ->
                            app.appScope.launch { app.db.scheduleDao().upsert(s.copy(enabled = on)) }
                        })
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { editing = Schedule(name = "", startMinute = 9 * 60, endMinute = 17 * 60) },
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text("New schedule") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }

    editing?.let { s ->
        ScheduleEditor(
            initial = s,
            onDismiss = { editing = null },
            onSave = { updated ->
                editing = null
                app.appScope.launch { app.db.scheduleDao().upsert(updated) }
            },
            onDelete = if (s.id == 0L) null else ({
                editing = null
                app.appScope.launch { app.db.scheduleDao().delete(s) }
            }),
        )
    }
}

private fun modeText(s: Schedule): String {
    val n = s.packageSet().size
    return when (s.mode) {
        ScheduleMode.BLOCK -> "Block $n apps"
        ScheduleMode.ALLOW -> "Allow only $n apps"
        ScheduleMode.OFFGRID -> "Off-Grid" + if (n > 0) " + $n apps" else ""
    }
}

private fun daysText(mask: Int): String = when (mask) {
    0b1111111 -> "Every day"
    0b0011111 -> "Weekdays"
    0b1100000 -> "Weekends"
    else -> dayLabels.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.joinToString("")
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ScheduleEditor(
    initial: Schedule,
    onDismiss: () -> Unit,
    onSave: (Schedule) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initial.name) }
    var start by remember { mutableIntStateOf(initial.startMinute) }
    var end by remember { mutableIntStateOf(initial.endMinute) }
    var days by remember { mutableIntStateOf(initial.daysMask) }
    var mode by remember { mutableStateOf(initial.mode) }
    var packages by remember { mutableStateOf(initial.packageSet()) }
    var picking by remember { mutableStateOf(false) }
    var challenge by remember { mutableStateOf(initial.challenge) }

    fun pickTime(current: Int, set: (Int) -> Unit) {
        TimePickerDialog(context, { _, h, m -> set(h * 60 + m) }, current / 60, current % 60, false).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "New schedule" else "Edit schedule") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(30) },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Gap(12.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickTime(start) { start = it } }) {
                        Text("From ${formatClock(start)}")
                    }
                    OutlinedButton(onClick = { pickTime(end) { end = it } }) {
                        Text("To ${formatClock(end)}")
                    }
                }
                Gap(12.dp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    dayLabels.forEachIndexed { i, label ->
                        val bit = 1 shl i
                        FilterChip(
                            selected = days and bit != 0,
                            onClick = { days = days xor bit },
                            label = { Text(label) },
                        )
                    }
                }
                Gap(8.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = mode == ScheduleMode.BLOCK,
                        onClick = { mode = ScheduleMode.BLOCK },
                        label = { Text("Block these") },
                    )
                    FilterChip(
                        selected = mode == ScheduleMode.ALLOW,
                        onClick = { mode = ScheduleMode.ALLOW },
                        label = { Text("Allow only") },
                    )
                    FilterChip(
                        selected = mode == ScheduleMode.OFFGRID,
                        onClick = {
                            mode = ScheduleMode.OFFGRID
                            packages = packages.take(OffGridStore.MAX_EXTRA_APPS).toSet()
                        },
                        label = { Text("Off-Grid") },
                    )
                }
                if (mode == ScheduleMode.OFFGRID) {
                    Gap(4.dp)
                    Text(
                        "Whole phone locks for this window. Calls & camera always work; " +
                            "the apps you choose stay usable.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Gap(4.dp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        UnlockChallenge.entries.forEach { c ->
                            FilterChip(
                                selected = challenge == c.name,
                                onClick = { challenge = c.name },
                                label = { Text(c.title) },
                            )
                        }
                    }
                }
                Gap(8.dp)
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Choose apps (${packages.size})")
                }
                if (onDelete != null) {
                    Gap(4.dp)
                    TextButton(onClick = onDelete) { Text("Delete schedule", color = Danger) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = days != 0 && (packages.isNotEmpty() || mode != ScheduleMode.BLOCK),
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.ifBlank { "Schedule" },
                            startMinute = start,
                            endMinute = end,
                            daysMask = days,
                            mode = mode,
                            packages = packages.toPackageString(),
                            challenge = challenge,
                        )
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (picking) {
        AppPickerSheet(
            title = when (mode) {
                ScheduleMode.BLOCK -> "Apps to block"
                ScheduleMode.ALLOW -> "Apps to allow"
                ScheduleMode.OFFGRID -> "Apps to keep during Off-Grid"
            },
            initiallySelected = packages,
            maxSelection = if (mode == ScheduleMode.OFFGRID) OffGridStore.MAX_EXTRA_APPS else Int.MAX_VALUE,
            onDismiss = { picking = false },
            onConfirm = { packages = it; picking = false },
        )
    }
}
