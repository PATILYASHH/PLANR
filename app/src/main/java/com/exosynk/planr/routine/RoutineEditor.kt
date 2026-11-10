package com.exosynk.planr.routine

import android.app.TimePickerDialog
import android.content.Intent
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.exosynk.planr.PlanrApp
import com.exosynk.planr.ui.components.AppPickerSheet
import com.exosynk.planr.ui.components.Chips
import com.exosynk.planr.ui.components.DayChips
import com.exosynk.planr.ui.components.EditorPage
import com.exosynk.planr.ui.components.Gap
import com.exosynk.planr.ui.components.SectionCard
import com.exosynk.planr.ui.components.SwitchRow
import com.exosynk.planr.ui.components.formatClock
import com.exosynk.planr.ui.theme.Danger
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun RoutineEditor(modifier: Modifier, routineId: Long, onDone: () -> Unit) {
    val app = PlanrApp.instance
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val alarms by app.db.alarmDao().observeAll().collectAsState(initial = emptyList())
    val groups by app.db.alarmGroupDao().observeAll().collectAsState(initial = emptyList())

    var loaded by remember { mutableStateOf(routineId == 0L) }
    var name by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf(true) }
    var trigger by remember { mutableStateOf<Trigger>(Trigger.Time(7 * 60)) }
    var actions by remember { mutableStateOf(listOf<Action>()) }
    var undo by remember { mutableStateOf(true) }
    var pickingAppFor by remember { mutableStateOf<Int?>(null) } // -1 = trigger, else action index

    LaunchedEffect(routineId) {
        if (routineId != 0L) {
            app.db.routineDao().get(routineId)?.let { r ->
                name = r.name
                enabled = r.enabled
                trigger = r.parsedTrigger
                actions = r.parsedActions
                undo = r.undoOnEnd
            }
            loaded = true
        }
    }
    if (!loaded) return

    val valid = name.isNotBlank() && actions.isNotEmpty() &&
        (trigger !is Trigger.AppOpened || (trigger as Trigger.AppOpened).packageName.isNotBlank()) &&
        actions.none { (it is Action.OpenApp && it.packageName.isBlank()) || (it is Action.AlarmGroupSwitch && groups.none { g -> g.id == it.groupId }) }

    EditorPage(
        modifier = modifier,
        title = if (routineId == 0L) "New routine" else "Edit routine",
        onBack = onDone,
        saveEnabled = valid,
        onSave = {
            scope.launch {
                Routines.save(
                    context.applicationContext,
                    Routine.of(name.trim(), trigger, actions, undo, id = routineId, enabled = enabled),
                )
                onDone()
            }
        },
        actions = {
            if (routineId != 0L) {
                IconButton(onClick = { scope.launch { Routines.delete(context.applicationContext, routineId); onDone() } }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete routine")
                }
            }
        },
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(40) },
            label = { Text("Routine name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Gap(12.dp)

        // ---- IF ----
        SectionCard {
            Text("If", style = MaterialTheme.typography.titleLarge)
            Gap(4.dp)
            Chips(
                null,
                RoutineText.triggerChoices.map { it.first },
                RoutineText.triggerChoices.firstOrNull { it.second::class == trigger::class }?.first ?: "Manual",
                { it },
            ) { label ->
                val t = RoutineText.triggerChoices.first { it.first == label }.second
                if (t::class != trigger::class) trigger = t
            }
            Gap(8.dp)
            TriggerParams(trigger, alarms, onChange = { trigger = it }, onPickApp = { pickingAppFor = -1 }, appLabel = { app.apps.labelOf(it) })
            if (trigger.isState) {
                SwitchRow(
                    "Undo when it ends",
                    "Put Do Not Disturb, sound mode, volume, Gateway and alarm groups back the way they were",
                    undo,
                ) { undo = it }
            }
        }
        Gap(12.dp)

        // ---- THEN ----
        SectionCard {
            Text("Then", style = MaterialTheme.typography.titleLarge)
            actions.forEachIndexed { i, a ->
                Gap(8.dp)
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            RoutineText.actionChoices.firstOrNull { it.second::class == a::class }?.first ?: "",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        ActionParams(
                            a,
                            groups = groups.map { it.id to it.name },
                            appLabel = { app.apps.labelOf(it) },
                            onChange = { na -> actions = actions.toMutableList().also { it[i] = na } },
                            onPickApp = { pickingAppFor = i },
                        )
                    }
                    IconButton(onClick = { actions = actions.toMutableList().also { it.removeAt(i) } }) {
                        Icon(Icons.Default.Close, contentDescription = "Remove action")
                    }
                }
            }
            Gap(8.dp)
            var menu by remember { mutableStateOf(false) }
            Box {
                FilledTonalButton(onClick = { menu = true }) {
                    Icon(Icons.Default.Add, null)
                    Text("Add action")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    RoutineText.actionChoices.forEach { (label, proto) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = {
                            menu = false
                            val a = if (proto is Action.AlarmGroupSwitch) proto.copy(groupId = groups.firstOrNull()?.id ?: 0) else proto
                            actions = actions + a
                        })
                    }
                }
            }
            val needsDnd = actions.any { it is Action.Dnd || (it is Action.Ringer && it.mode == RingerMode.SILENT) }
            val nm = context.getSystemService(android.app.NotificationManager::class.java)
            if (needsDnd && nm?.isNotificationPolicyAccessGranted != true) {
                Gap(8.dp)
                Text("Do Not Disturb and Mute need Do Not Disturb access.", color = Danger)
                OutlinedButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }) { Text("Grant access") }
            }
            if (actions.any { it is Action.AlarmGroupSwitch } && groups.isEmpty()) {
                Gap(8.dp)
                Text("Create an alarm group in Alarm first.", color = Danger)
            }
        }
        Gap(12.dp)
        SwitchRow("Routine on", null, enabled) { enabled = it }
        Gap(32.dp)
    }

    pickingAppFor?.let { target ->
        AppPickerSheet(
            title = "Choose an app",
            initiallySelected = emptySet(),
            maxSelection = 1,
            onDismiss = { pickingAppFor = null },
            onConfirm = { chosen ->
                pickingAppFor = null
                val pkg = chosen.firstOrNull() ?: return@AppPickerSheet
                if (target == -1) {
                    trigger = Trigger.AppOpened(pkg)
                } else {
                    actions = actions.toMutableList().also { it[target] = Action.OpenApp(pkg) }
                }
            },
        )
    }
}

@Composable
private fun TriggerParams(
    t: Trigger,
    alarms: List<com.exosynk.planr.alarm.Alarm>,
    onChange: (Trigger) -> Unit,
    onPickApp: () -> Unit,
    appLabel: (String) -> String,
) {
    val context = LocalContext.current
    fun pickTime(current: Int, set: (Int) -> Unit) {
        TimePickerDialog(context, { _, h, m -> set(h * 60 + m) }, current / 60, current % 60, DateFormat.is24HourFormat(context)).show()
    }
    when (t) {
        is Trigger.Time -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickTime(t.start) { onChange(t.copy(start = it)) } }) {
                    Text((if (t.isWindow) "From " else "At ") + formatClock(t.start))
                }
                if (t.isWindow) {
                    OutlinedButton(onClick = { pickTime(t.end) { if (it != t.start) onChange(t.copy(end = it)) } }) {
                        Text("Until ${formatClock(t.end)}")
                    }
                }
            }
            SwitchRow("Time window", "Lasts until an end time, and can undo its actions", t.isWindow) { on ->
                onChange(t.copy(end = if (on) (t.start + 8 * 60) % 1440 else Trigger.NO_END))
            }
            DayChips(t.days) { if (it != 0) onChange(t.copy(days = it)) }
        }
        is Trigger.AlarmDismissed -> {
            val options = listOf<Long?>(null) + alarms.map { it.id }
            Chips("Which alarm", options, t.alarmId, { id ->
                id?.let { alarms.firstOrNull { a -> a.id == it }?.let { a -> a.label.ifBlank { "%d:%02d".format(a.hour, a.minute) } } } ?: "Any alarm"
            }) { onChange(Trigger.AlarmDismissed(it)) }
        }
        is Trigger.BatteryLow -> Chips("At or below", listOf(10, 15, 20, 30, 50), t.percent, { "$it%" }) { onChange(Trigger.BatteryLow(it)) }
        is Trigger.AppOpened -> OutlinedButton(onClick = onPickApp) {
            Text(if (t.packageName.isBlank()) "Choose app" else appLabel(t.packageName))
        }
        Trigger.Manual -> Text("Run it from the Routines screen.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        else -> Unit
    }
}

@Composable
private fun ActionParams(
    a: Action,
    groups: List<Pair<Long, String>>,
    appLabel: (String) -> String,
    onChange: (Action) -> Unit,
    onPickApp: () -> Unit,
) {
    when (a) {
        is Action.Dnd -> Chips(null, listOf(true, false), a.on, { if (it) "Turn on" else "Turn off" }) { onChange(Action.Dnd(it)) }
        is Action.Ringer -> Chips(null, RingerMode.entries.toList(), a.mode, { it.title }) { onChange(Action.Ringer(it)) }
        is Action.MediaVolume -> {
            Text("${a.percent}%", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(a.percent.toFloat(), { onChange(Action.MediaVolume((it / 5f).roundToInt() * 5)) }, valueRange = 0f..100f)
        }
        is Action.StartOffGrid -> Chips(null, listOf(30, 60, 120, 240, 480), a.minutes, {
            if (it < 60) "$it min" else "${it / 60} h"
        }) { onChange(Action.StartOffGrid(it)) }
        is Action.StartFocus -> {
            Chips(null, listOf(25, 50, 90), a.minutes, { "$it min" }) { onChange(a.copy(minutes = it)) }
            Chips(null, listOf(1, 2, 4), a.cycles, { "× $it" }) { onChange(a.copy(cycles = it)) }
        }
        is Action.Gateway -> Chips(null, listOf(true, false), a.on, { if (it) "Turn on" else "Turn off" }) { onChange(Action.Gateway(it)) }
        is Action.AlarmGroupSwitch -> if (groups.isNotEmpty()) {
            Chips(null, groups.map { it.first }, a.groupId, { id -> groups.firstOrNull { it.first == id }?.second ?: "?" }) {
                onChange(a.copy(groupId = it))
            }
            Chips(null, listOf(true, false), a.on, { if (it) "Turn on" else "Turn off" }) { onChange(a.copy(on = it)) }
        }
        is Action.Notify -> OutlinedTextField(
            value = a.text,
            onValueChange = { onChange(Action.Notify(it.take(200))) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Action.TodayBrief -> Text(
            "Notification with today's reminders, next alarm and habit progress",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        is Action.OpenApp -> OutlinedButton(onClick = onPickApp) {
            Text(if (a.packageName.isBlank()) "Choose app" else appLabel(a.packageName))
        }
    }
}
