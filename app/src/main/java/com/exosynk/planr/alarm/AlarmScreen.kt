package com.exosynk.planr.alarm

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import com.exosynk.planr.ui.theme.Success
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.exosynk.planr.PlanrApp
import com.exosynk.planr.engine.Notifier
import com.exosynk.planr.ui.components.Gap
import com.exosynk.planr.ui.components.SectionCard
import com.exosynk.planr.ui.components.daysText
import com.exosynk.planr.ui.theme.Danger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date

@Composable
fun AlarmScreen(modifier: Modifier, onEdit: (Long) -> Unit) {
    val app = PlanrApp.instance
    val context = LocalContext.current
    val alarms by app.db.alarmDao().observeAll().collectAsState(initial = emptyList())
    val groups by app.db.alarmGroupDao().observeAll().collectAsState(initial = emptyList())
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            now = LocalDateTime.now()
        }
    }
    var groupDialog by remember { mutableStateOf<AlarmGroup?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++; now = LocalDateTime.now() }

    val next = Alarms.nextOf(alarms, groups, now)
    val grouped = alarms.groupBy { a -> a.groupId?.takeIf { id -> groups.any { it.id == id } } }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(20.dp, 24.dp, 20.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (next != null) AlarmTime.untilText(now, next.second) else "All alarms are off",
                            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp, lineHeight = 36.sp),
                        )
                        if (next != null) {
                            Text(
                                next.second.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = { groupDialog = AlarmGroup(name = "") }) {
                        Icon(Icons.Outlined.CreateNewFolder, contentDescription = "New alarm group")
                    }
                }
                Gap(4.dp)
                AlarmPermissionCard(tick)
            }
            if (alarms.isEmpty()) {
                item {
                    Text(
                        "No alarms yet. Tap “Add alarm”. Group alarms (e.g. Weekday mornings) to switch them all at once.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(grouped[null].orEmpty(), key = { "a${it.id}" }) { a ->
                AlarmCard(a, groupOn = true, now = now, groups = groups, onEdit = onEdit)
            }
            groups.forEach { g ->
                val members = grouped[g.id].orEmpty()
                item(key = "g${g.id}") {
                    GroupHeader(g, members.size, onRename = { groupDialog = g })
                }
                items(members, key = { "a${it.id}" }) { a ->
                    AlarmCard(a, groupOn = g.enabled, now = now, groups = groups, onEdit = onEdit)
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { onEdit(0L) },
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text("Add alarm") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }

    groupDialog?.let { g ->
        GroupDialog(
            group = g,
            onDismiss = { groupDialog = null },
            onSave = { name ->
                groupDialog = null
                app.appScope.launch { Alarms.saveGroup(context.applicationContext, g.copy(name = name)) }
            },
            onDelete = if (g.id == 0L) null else ({
                groupDialog = null
                app.appScope.launch { Alarms.deleteGroup(context.applicationContext, g.id) }
            }),
        )
    }
}

@Composable
private fun GroupHeader(g: AlarmGroup, count: Int, onRename: () -> Unit) {
    val app = PlanrApp.instance
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(g.name, style = MaterialTheme.typography.titleLarge)
            Text(
                "$count ${if (count == 1) "alarm" else "alarms"} · ${if (g.enabled) "group on" else "group off"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onRename) { Text("Edit") }
        Switch(checked = g.enabled, onCheckedChange = { on ->
            app.appScope.launch { Alarms.setGroupEnabled(context.applicationContext, g.id, on) }
        })
    }
}

@Composable
private fun AlarmCard(a: Alarm, groupOn: Boolean, now: LocalDateTime, groups: List<AlarmGroup>, onEdit: (Long) -> Unit) {
    val app = PlanrApp.instance
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    val active = a.enabled && groupOn
    val dim = if (active) 1f else 0.45f
    val time = LocalTime.of(a.hour, a.minute)

    SectionCard(onClick = { onEdit(a.id) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (a.label.isNotBlank()) {
                    Text(a.label, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dim))
                }
                Text(
                    time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)),
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = dim),
                )
                if (a.isRepeating) {
                    // Samsung-style weekday letters, active days highlighted
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        com.exosynk.planr.ui.components.DAY_LETTERS.forEachIndexed { i, l ->
                            val on = a.daysMask and (1 shl i) != 0
                            Text(
                                l,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                                color = if (on) MaterialTheme.colorScheme.primary.copy(alpha = dim)
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f * dim),
                            )
                        }
                    }
                } else {
                    Text(
                        scheduleText(a, now),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dim),
                    )
                }
                if (a.skippedAt != 0L && a.skippedAt > System.currentTimeMillis()) {
                    Text(
                        "Skipping " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(a.skippedAt)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Danger,
                    )
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (a.isRepeating && a.enabled) {
                        if (a.skippedAt > System.currentTimeMillis()) {
                            DropdownMenuItem(text = { Text("Don't skip") }, onClick = {
                                menu = false
                                app.appScope.launch { Alarms.undoSkip(context.applicationContext, a) }
                            })
                        } else {
                            DropdownMenuItem(text = { Text("Skip next alarm") }, onClick = {
                                menu = false
                                app.appScope.launch { Alarms.skipNext(context.applicationContext, a) }
                            })
                        }
                    }
                    if (groups.isNotEmpty()) {
                        DropdownMenuItem(text = { Text("Move to group") }, onClick = { menu = false; moving = true })
                    }
                    DropdownMenuItem(text = { Text("Duplicate") }, onClick = {
                        menu = false
                        app.appScope.launch { Alarms.save(context.applicationContext, a.copy(id = 0, skippedAt = 0)) }
                    })
                    DropdownMenuItem(text = { Text("Delete", color = Danger) }, onClick = {
                        menu = false
                        app.appScope.launch { Alarms.delete(context.applicationContext, a.id) }
                    })
                }
            }
            Switch(checked = a.enabled, onCheckedChange = { on ->
                app.appScope.launch {
                    Alarms.setEnabled(context.applicationContext, a, on)
                    if (on) {
                        val fresh = app.db.alarmDao().get(a.id)
                        val at = fresh?.let { AlarmTime.next(it, LocalDateTime.now()) }
                        if (at != null) toastOnMain(context, AlarmTime.untilText(LocalDateTime.now(), at))
                    }
                }
            })
        }
    }

    if (moving) {
        AlertDialog(
            onDismissRequest = { moving = false },
            title = { Text("Move to group") },
            text = {
                Column {
                    (listOf<AlarmGroup?>(null) + groups).forEach { g ->
                        TextButton(onClick = {
                            moving = false
                            app.appScope.launch { Alarms.save(context.applicationContext, a.copy(groupId = g?.id)) }
                        }) { Text(g?.name ?: "No group") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { moving = false }) { Text("Cancel") } },
        )
    }
}

/** "Every day", "Mon, Wed", "Tomorrow", "Today" or a date. */
internal fun scheduleText(a: Alarm, now: LocalDateTime): String {
    if (a.isRepeating) return daysText(a.daysMask)
    val next = AlarmTime.next(a.copy(enabled = true), now) ?: return a.date ?: "Once"
    val d = next.toLocalDate()
    val today = now.toLocalDate()
    return when (d) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> d.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
}

@Composable
private fun GroupDialog(group: AlarmGroup, onDismiss: () -> Unit, onSave: (String) -> Unit, onDelete: (() -> Unit)?) {
    var name by remember { mutableStateOf(group.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (group.id == 0L) "New alarm group" else "Edit group") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(30) },
                    label = { Text("Group name") },
                    placeholder = { Text("Weekday mornings") },
                    singleLine = true,
                )
                if (onDelete != null) {
                    Gap(8.dp)
                    TextButton(onClick = onDelete) { Text("Delete group (keeps its alarms)", color = Danger) }
                }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private data class SetupItem(val title: String, val why: String, val ok: Boolean, val fix: () -> Unit)

/**
 * Everything Android needs for an alarm to wake the phone and fill the screen, as a
 * checklist with one-tap fixes, plus a test alarm to prove it works.
 */
@Composable
private fun AlarmPermissionCard(tick: Int) {
    val context = LocalContext.current
    val notifLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { }
    fun open(intent: Intent) = runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    val pkgUri = Uri.parse("package:${context.packageName}")
    val items = remember(tick) {
        buildList {
            add(SetupItem("Notifications", "Shows the alarm and its Snooze/Dismiss buttons", Notifier.canPost(context)) {
                if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                else open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            })
            if (Build.VERSION.SDK_INT >= 34) {
                val ok = context.getSystemService(android.app.NotificationManager::class.java)?.canUseFullScreenIntent() != false
                add(SetupItem("Full-screen alarms", "Turns the screen on and fills it when the phone is locked", ok) {
                    open(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkgUri))
                })
            }
            if (Build.VERSION.SDK_INT >= 31) {
                add(SetupItem("Exact alarms", "Rings on the minute, even in battery saver", AlarmScheduler.canScheduleExact(context)) {
                    open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkgUri))
                })
            }
            add(SetupItem("Display over other apps", "Opens the full alarm screen instantly, even while you're using another app", Settings.canDrawOverlays(context)) {
                open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri))
            })
            val pm = context.getSystemService(android.os.PowerManager::class.java)
            add(SetupItem("No battery restriction", "Stops the phone from putting PLANR to sleep (important on Samsung)", pm?.isIgnoringBatteryOptimizations(context.packageName) == true) {
                @android.annotation.SuppressLint("BatteryLife")
                val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri)
                if (open(i).isFailure) open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            })
        }
    }
    val done = items.count { it.ok }
    var expanded by remember(done == items.size) { mutableStateOf(done < items.size) }
    SectionCard(onClick = { expanded = !expanded }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (done == items.size) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = if (done == items.size) Success else Danger,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.padding(start = 10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (done == items.size) "Alarms are ready" else "Alarm setup · $done of ${items.size}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    if (done == items.size) "They'll wake the screen and fill it" else "Finish these so alarms always ring full screen",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (expanded) {
            Gap(8.dp)
            items.forEach { item ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (item.ok) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (item.ok) Success else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(start = 10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        Text(item.why, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!item.ok) FilledTonalButton(onClick = item.fix) { Text("Allow") }
                }
            }
        }
        Gap(4.dp)
        TextButton(onClick = {
            AlarmService.scheduleTest(context)
            toastOnMain(context, "Test alarm in 10 seconds. Lock your phone now.")
        }) { Text("Test alarm (rings in 10 s)") }
    }
}

internal fun toastOnMain(context: android.content.Context, text: String) {
    android.os.Handler(android.os.Looper.getMainLooper()).post {
        android.widget.Toast.makeText(context.applicationContext, text, android.widget.Toast.LENGTH_SHORT).show()
    }
}
