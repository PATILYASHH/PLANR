package com.exosynk.planr.ui.screens

import android.Manifest
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.exosynk.planr.PlanrApp
import com.exosynk.planr.data.BackupManager
import com.exosynk.planr.data.PlanrSettings
import com.exosynk.planr.engine.Notifier
import com.exosynk.planr.ui.components.AppIcon
import com.exosynk.planr.ui.components.AppPickerSheet
import com.exosynk.planr.ui.components.Gap
import com.exosynk.planr.ui.components.LockdownBanner
import com.exosynk.planr.ui.components.SectionCard
import com.exosynk.planr.ui.components.formatClock
import com.exosynk.planr.ui.components.rememberLockdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private val dayLetters = listOf("M", "T", "W", "T", "F", "S", "S")

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(modifier: Modifier, onBack: () -> Unit, onOpenAbout: () -> Unit = {}) {
    BackHandler(onBack = onBack)
    val app = PlanrApp.instance
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by app.settings.state.collectAsState()
    val lockdown = rememberLockdown()

    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val notificationsOn = remember(tick) { Notifier.canPost(context) }
    val dndAccess = remember(tick) {
        context.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true
    }

    var pickingWhitelist by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf<String?>(null) }

    fun change(transform: (PlanrSettings) -> PlanrSettings) {
        if (!app.settings.edit(transform)) {
            Toast.makeText(context, "Lockdown is on: settings can only get stricter.", Toast.LENGTH_SHORT).show()
        }
    }

    fun pickTime(current: Int, set: (Int) -> Unit) {
        TimePickerDialog(context, { _, h, m -> set(h * 60 + m) }, current / 60, current % 60, false).show()
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val calendarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        app.settings.update(app.settings.current.copy(deviceCalendar = granted), force = true)
        tick++
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val json = BackupManager.export(app)
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray()) }
                        ?: error("Couldn't open the file")
                }
            }
            Toast.makeText(
                context,
                if (result.isSuccess) "Backup saved" else "Backup failed: ${result.exceptionOrNull()?.message}",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        app.settings.update(app.settings.current.copy(autoBackupFolder = uri.toString()), force = true)
        scope.launch {
            val err = withContext(Dispatchers.IO) { com.exosynk.planr.data.AutoBackup.runIfDue(context, force = true) }
            Toast.makeText(context, err?.let { "Backup failed: $it" } ?: "Backed up. PLANR will do this every day.", Toast.LENGTH_LONG).show()
            tick++
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input -> readBounded(input) }
                }.getOrNull()
            }
            if (text == null) {
                Toast.makeText(context, "Couldn't read that file", Toast.LENGTH_SHORT).show()
            } else {
                confirmRestore = text
            }
        }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
    ) {
        Gap(12.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
        }
        Gap(12.dp)
        if (lockdown) {
            LockdownBanner("Settings can only be made stricter")
            Gap(16.dp)
        }

        // Appearance
        SectionCard {
            Text("Appearance", style = MaterialTheme.typography.titleLarge)
            Gap(8.dp)
            ChipChoice("Theme", com.exosynk.planr.data.ThemeMode.entries.map { it.ordinal }, s.themeMode.ordinal, {
                com.exosynk.planr.data.ThemeMode.entries[it].title
            }) { v ->
                app.settings.update(app.settings.current.copy(themeMode = com.exosynk.planr.data.ThemeMode.entries[v]), force = true)
            }
            ToggleRow(
                "Phone calendar on Today",
                "Show events from your Google/Samsung calendar (read-only, on this phone)",
                s.deviceCalendar && com.exosynk.planr.today.DeviceCalendar.hasPermission(context),
            ) { on ->
                if (on && !com.exosynk.planr.today.DeviceCalendar.hasPermission(context)) {
                    calendarLauncher.launch(Manifest.permission.READ_CALENDAR)
                } else {
                    app.settings.update(app.settings.current.copy(deviceCalendar = on), force = true)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ToggleRow("Material You colours", "Match your wallpaper's colours", s.dynamicColor) { on ->
                    app.settings.update(app.settings.current.copy(dynamicColor = on), force = true)
                }
            }
        }
        Gap(16.dp)

        if (!notificationsOn) {
            SectionCard {
                Text("Notifications", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Allow notifications for Wind Down, Eye Care and achievements.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Gap(8.dp)
                FilledTonalButton(onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }) {
                    Text("Allow notifications")
                }
            }
            Gap(16.dp)
        }

        // Mindful Gateway
        SectionCard {
            ToggleRow(
                "Mindful Gateway",
                "A short pause before every app you open, not just shielded ones.",
                s.gatewayEnabled,
            ) { on -> change { it.copy(gatewayEnabled = on) } }
            if (s.gatewayEnabled) {
                Gap(8.dp)
                ChipChoice("Pause", listOf(3, 5, 10, 15), s.gatewayPauseSeconds, { "${it}s" }) { v ->
                    change { it.copy(gatewayPauseSeconds = v) }
                }
                ChipChoice("Then open for", listOf(5, 10, 20, 30), s.gatewayPassMinutes, { "${it}m" }) { v ->
                    change { it.copy(gatewayPassMinutes = v) }
                }
                Text(
                    "Shielded apps keep their own settings. Settings, camera, calls and keyboards are never paused.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Gap(8.dp)
                if (s.gatewayWhitelist.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        s.gatewayWhitelist.sorted().forEach { AppIcon(it, 32.dp) }
                    }
                    Gap(8.dp)
                }
                OutlinedButton(onClick = { pickingWhitelist = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Apps that skip the pause (${s.gatewayWhitelist.size})")
                }
            }
        }
        Gap(16.dp)

        // Emergency passes
        SectionCard {
            Text("Emergency passes", style = MaterialTheme.typography.titleLarge)
            Text(
                "Open an app that's over its limit or blocked by a schedule, a few times a day. Never works during Off-Grid.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Gap(8.dp)
            ChipChoice("Per day", (0..PlanrSettings.MAX_EMERGENCY_QUOTA).toList(), s.emergencyQuota, {
                if (it == 0) "Off" else "$it"
            }) { v -> change { it.copy(emergencyQuota = v) } }
            if (s.emergencyQuota > 0) {
                ChipChoice("Each lasts", listOf(3, 5, 10), s.emergencyMinutes, { "${it}m" }) { v ->
                    change { it.copy(emergencyMinutes = v) }
                }
            }
        }
        Gap(16.dp)

        // Lockdown
        SectionCard {
            ToggleRow(
                "Lockdown",
                "During these hours, rules can only get stricter: no pausing shields, deleting schedules or stopping focus.",
                s.lockdownEnabled,
                enabled = !lockdown,
            ) { on -> change { it.copy(lockdownEnabled = on) } }
            if (s.lockdownEnabled) {
                Gap(8.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = !lockdown, onClick = {
                        pickTime(s.lockdownStart) { v ->
                            if (v == s.lockdownEnd) sameTimeToast(context) else change { it.copy(lockdownStart = v) }
                        }
                    }) { Text("From ${formatClock(s.lockdownStart)}") }
                    OutlinedButton(enabled = !lockdown, onClick = {
                        pickTime(s.lockdownEnd) { v ->
                            if (v == s.lockdownStart) sameTimeToast(context) else change { it.copy(lockdownEnd = v) }
                        }
                    }) { Text("To ${formatClock(s.lockdownEnd)}") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    dayLetters.forEachIndexed { i, label ->
                        val bit = 1 shl i
                        FilterChip(
                            selected = s.lockdownDays and bit != 0,
                            enabled = !lockdown,
                            onClick = {
                                val next = s.lockdownDays xor bit
                                if (next != 0) change { it.copy(lockdownDays = next) }
                            },
                            label = { Text(label) },
                        )
                    }
                }
                ToggleRow(
                    "Also block Android Settings",
                    "Stops PLANR from being switched off in Settings during Lockdown. Quick settings still work.",
                    s.lockdownBlocksSettings,
                ) { on -> change { it.copy(lockdownBlocksSettings = on) } }
            }
        }
        Gap(16.dp)

        // Wind Down
        SectionCard {
            ToggleRow(
                "Wind Down",
                "A bedtime reminder, and optional Do Not Disturb until morning.",
                s.windDownEnabled,
            ) { on -> change { it.copy(windDownEnabled = on) } }
            if (s.windDownEnabled) {
                Gap(8.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        pickTime(s.windDownStart) { v ->
                            if (v == s.windDownEnd) sameTimeToast(context) else change { it.copy(windDownStart = v) }
                        }
                    }) { Text("Bedtime ${formatClock(s.windDownStart)}") }
                    OutlinedButton(onClick = {
                        pickTime(s.windDownEnd) { v ->
                            if (v == s.windDownStart) sameTimeToast(context) else change { it.copy(windDownEnd = v) }
                        }
                    }) { Text("Wake ${formatClock(s.windDownEnd)}") }
                }
                ToggleRow(
                    "Do Not Disturb",
                    if (dndAccess) "Turns on at bedtime and off at wake time."
                    else "Needs Do Not Disturb access.",
                    s.windDownDnd,
                ) { on -> change { it.copy(windDownDnd = on) } }
                if (s.windDownDnd && !dndAccess) {
                    FilledTonalButton(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }) { Text("Grant access") }
                }
            }
        }
        Gap(16.dp)

        // Eye Care
        SectionCard {
            ToggleRow(
                "Eye Care 20-20-20",
                "Every 20 minutes of screen time, look at something 20 feet away for 20 seconds.",
                s.eyeCareEnabled,
            ) { on -> change { it.copy(eyeCareEnabled = on) } }
        }
        Gap(8.dp)
        Text(
            "Reminders run inside the PLANR accessibility service, so it needs to be on.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Gap(16.dp)

        // Backup
        SectionCard {
            Text("Backup & restore", style = MaterialTheme.typography.titleLarge)
            Text(
                "Save alarms, reminders, notes, routines, shields, schedules, settings, XP and Off-Grid history to a file you control. Nothing is uploaded.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Gap(12.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { exportLauncher.launch("planr-backup-${LocalDate.now()}.json") }) {
                    Text("Export")
                }
                OutlinedButton(
                    enabled = !lockdown,
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                ) { Text("Restore") }
            }
            Gap(12.dp)
            val folder = s.autoBackupFolder
            val lastDay = remember(tick, folder) { com.exosynk.planr.data.AutoBackup.lastBackupDay(context) }
            val lastError = remember(tick, folder) { com.exosynk.planr.data.AutoBackup.lastError(context) }
            ToggleRow(
                "Daily auto-backup",
                when {
                    folder == null -> "Save a dated backup to a folder you choose, every day (keeps the last ${com.exosynk.planr.data.AutoBackup.KEEP})"
                    lastError != null -> "Last attempt failed: $lastError"
                    lastDay != null -> "On · last backup $lastDay"
                    else -> "On"
                },
                folder != null,
            ) { on ->
                if (on) folderLauncher.launch(null)
                else app.settings.update(app.settings.current.copy(autoBackupFolder = null), force = true)
            }
            if (folder != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        scope.launch {
                            val err = withContext(Dispatchers.IO) { com.exosynk.planr.data.AutoBackup.runIfDue(context, force = true) }
                            Toast.makeText(context, err?.let { "Backup failed: $it" } ?: "Backed up", Toast.LENGTH_SHORT).show()
                            tick++
                        }
                    }) { Text("Back up now") }
                    TextButton(onClick = { folderLauncher.launch(null) }) { Text("Change folder") }
                }
            }
        }
        Gap(16.dp)
        SectionCard(onClick = onOpenAbout) {
            Text("About PLANR", style = MaterialTheme.typography.titleLarge)
            Text(
                "Version, open-source license, credits and more from ExoSynk Labs",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Gap(32.dp)
    }

    if (pickingWhitelist) {
        AppPickerSheet(
            title = "Skip the Gateway pause",
            initiallySelected = s.gatewayWhitelist,
            exclude = app.apps.alwaysAllowed(),
            onDismiss = { pickingWhitelist = false },
            onConfirm = { chosen ->
                pickingWhitelist = false
                change { it.copy(gatewayWhitelist = chosen) }
            },
        )
    }

    confirmRestore?.let { json ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text("Restore this backup?") },
            text = {
                Text("Everything in PLANR (alarms, reminders, notes, routines, shields, schedules, settings, XP and Off-Grid history) will be replaced by the backup.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { runCatching { BackupManager.restore(app, json) } }
                        val msg = result.fold(
                            onSuccess = { "Restored ${it.shields.size} shields and ${it.schedules.size} schedules" },
                            onFailure = { it.message ?: "Restore failed" },
                        )
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                }) { Text("Replace") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text("Cancel") } },
        )
    }
}

private const val MAX_BACKUP_BYTES = 2 * 1024 * 1024

/** Backups are small; refuse anything absurd instead of loading it into memory. */
private fun readBounded(input: java.io.InputStream): String {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(8192)
    while (true) {
        val n = input.read(buf)
        if (n < 0) break
        out.write(buf, 0, n)
        if (out.size() > MAX_BACKUP_BYTES) error("File is too large")
    }
    return out.toString(Charsets.UTF_8.name())
}

private fun sameTimeToast(context: android.content.Context) =
    Toast.makeText(context, "Start and end can't be the same time", Toast.LENGTH_SHORT).show()

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = onChange)
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ChipChoice(
    label: String,
    options: List<Int>,
    selected: Int,
    text: (Int) -> String,
    onSelect: (Int) -> Unit,
) {
    Text(label, style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(text(o)) })
        }
    }
    Gap(4.dp)
}
