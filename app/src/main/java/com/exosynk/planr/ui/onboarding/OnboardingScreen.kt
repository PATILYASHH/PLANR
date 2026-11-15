package com.exosynk.planr.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AutoMode
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.exosynk.planr.PlanrApp
import com.exosynk.planr.alarm.AlarmScheduler
import com.exosynk.planr.data.ThemeMode
import com.exosynk.planr.engine.Notifier
import com.exosynk.planr.engine.PlanrAccessibilityService
import com.exosynk.planr.smart.Contacts
import com.exosynk.planr.smart.SmartAddBar
import com.exosynk.planr.today.DeviceCalendar
import com.exosynk.planr.ui.components.AppIcon
import com.exosynk.planr.ui.components.Gap
import com.exosynk.planr.ui.theme.Success

/** First-run walkthrough, shown once on a fresh install (never on updates). */
object Onboarding {
    private const val PREFS = "onboarding"
    private const val K_DONE = "done"

    fun shouldShow(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(K_DONE, false)) return false
        val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull() ?: return false
        // Updates from an older PLANR skip it: those people already set things up.
        val freshInstall = info.firstInstallTime == info.lastUpdateTime
        if (!freshInstall) markDone(context)
        return freshInstall
    }

    fun markDone(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(K_DONE, true) }
}

private const val STEPS = 4

@Composable
fun OnboardingScreen(modifier: Modifier, onFinish: () -> Unit) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = step > 0) { step-- }
    fun finish() {
        Onboarding.markDone(context)
        onFinish()
    }

    Column(modifier.fillMaxSize().imePadding().padding(horizontal = 24.dp)) {
        // Progress dots + skip
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(STEPS) { i ->
                    Box(
                        Modifier.height(6.dp).width(if (i == step) 24.dp else 6.dp).clip(CircleShape)
                            .background(if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh),
                    )
                }
            }
            if (step < STEPS - 1) TextButton(onClick = { finish() }) { Text("Skip") }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Gap(24.dp)
            when (step) {
                0 -> WelcomeStep()
                1 -> ThemeStep()
                2 -> PermissionsStep()
                else -> TryItStep(onDone = { finish() })
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (step > 0) TextButton(onClick = { step-- }) { Text("Back") }
            Spacer(Modifier.weight(1f))
            Button(onClick = { if (step < STEPS - 1) step++ else finish() }, modifier = Modifier.height(52.dp)) {
                Text(if (step < STEPS - 1) "Next" else "Start using PLANR")
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        AppIcon(context.packageName, 88.dp)
        Gap(16.dp)
        Text("Welcome to PLANR", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(
            "Plan your day. Guard your attention.\nEverything stays on this phone, with no internet at all.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
    Gap(28.dp)
    Feature(Icons.Outlined.CalendarMonth, "Today", "Your calendar, reminders, notes and alarms in one place")
    Feature(Icons.Outlined.Alarm, "Alarm", "Groups, snooze rules, math-to-dismiss and a morning brief")
    Feature(Icons.Outlined.TaskAlt, "Reminders", "Type “call Parth at 9 am” and PLANR sets it up")
    Feature(Icons.AutoMirrored.Outlined.StickyNote2, "Notes", "Notes and checklists, linked to reminders")
    Feature(Icons.Outlined.SelfImprovement, "Habit", "Mindful pauses, Focus and Off-Grid Mode")
    Feature(Icons.Outlined.AutoMode, "Routines", "If this, then that, and undo when it ends")
}

@Composable
private fun Feature(icon: ImageVector, title: String, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ThemeStep() {
    val app = PlanrApp.instance
    val s by app.settings.state.collectAsState()
    Text("Make it yours", style = MaterialTheme.typography.headlineMedium)
    Text("You can change this any time in Settings.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Gap(20.dp)
    Text("Theme", style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemeMode.entries.forEach { m ->
            FilterChip(
                selected = s.themeMode == m,
                onClick = { app.settings.update(app.settings.current.copy(themeMode = m), force = true) },
                label = { Text(m.title) },
            )
        }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Gap(12.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Material You colours", style = MaterialTheme.typography.titleMedium)
                Text("Match your wallpaper", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.material3.Switch(
                checked = s.dynamicColor,
                onCheckedChange = { app.settings.update(app.settings.current.copy(dynamicColor = it), force = true) },
            )
        }
    }
}

private data class Perm(val title: String, val why: String, val ok: Boolean, val optional: Boolean, val ask: () -> Unit)

@Composable
private fun PermissionsStep() {
    val context = LocalContext.current
    val app = PlanrApp.instance
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    var disclosure by remember { mutableStateOf(false) }
    val runtime = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val calendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) app.settings.update(app.settings.current.copy(deviceCalendar = true), force = true)
        tick++
    }
    fun open(i: Intent) = runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    val pkg = Uri.parse("package:${context.packageName}")
    val perms = remember(tick) {
        buildList {
            add(Perm("Notifications", "Reminders, alarms and routine messages", Notifier.canPost(context), false) {
                if (Build.VERSION.SDK_INT >= 33) runtime.launch(Manifest.permission.POST_NOTIFICATIONS)
                else open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            })
            if (Build.VERSION.SDK_INT >= 34) {
                val ok = context.getSystemService(android.app.NotificationManager::class.java)?.canUseFullScreenIntent() != false
                add(Perm("Full-screen alarms", "Alarms wake the screen and fill it", ok, false) {
                    open(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg))
                })
            }
            if (Build.VERSION.SDK_INT >= 31) {
                add(Perm("Exact alarms", "Alarms and reminders ring on the minute", AlarmScheduler.canScheduleExact(context), false) {
                    open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg))
                })
            }
            add(Perm("Accessibility service", "Shields, Focus and Off-Grid Mode (sees only which app is open)", PlanrAccessibilityService.isEnabled(context), false) {
                disclosure = true
            })
            add(Perm("Usage access", "Screen time and daily limits", app.usage.hasPermission(), false) {
                open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).setData(pkg))
            })
            add(Perm("Contacts", "Turn “call Parth” into a one-tap Call button", Contacts.hasPermission(context), true) {
                runtime.launch(Manifest.permission.READ_CONTACTS)
            })
            add(Perm("Calendar", "Show your phone's calendar events on Today", DeviceCalendar.hasPermission(context), true) {
                calendar.launch(Manifest.permission.READ_CALENDAR)
            })
        }
    }
    Text("Allow what you want to use", style = MaterialTheme.typography.headlineMedium)
    Text(
        "Each one has a reason. Optional ones can wait. You can find them all later in Habit → Home and Alarm → Alarm setup.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Gap(12.dp)
    perms.forEach { p ->
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (p.ok) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                null,
                tint = if (p.ok) Success else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.title + if (p.optional) " · optional" else "", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(p.why, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!p.ok) FilledTonalButton(onClick = p.ask) { Text("Allow") }
        }
    }
    if (disclosure) {
        // Prominent disclosure, required by Google Play before opening accessibility settings.
        AlertDialog(
            onDismissRequest = { disclosure = false },
            title = { Text("Accessibility permission") },
            text = {
                Text(
                    "PLANR uses the Accessibility API only to detect which app is in front, so it can show mindful " +
                        "pauses, enforce your schedules and keep Off-Grid Mode locked.\n\nPLANR does not read your screen " +
                        "content or what you type, and it has no internet access. Nothing leaves your phone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    disclosure = false
                    open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }) { Text("Agree & open settings") }
            },
            dismissButton = { TextButton(onClick = { disclosure = false }) { Text("Not now") } },
        )
    }
}

@Composable
private fun TryItStep(onDone: () -> Unit) {
    Text("Try it", style = MaterialTheme.typography.headlineMedium)
    Text(
        "Type a reminder the way you'd say it. PLANR understands times, dates, repeats and people.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Gap(16.dp)
    SmartAddBar(
        initialText = "call Parth tomorrow at 9 am",
        onAdded = onDone,
        onMoreOptions = { onDone() },
    )
    Gap(12.dp)
    Text(
        "More to try: “wake me at 6:30 weekdays”, “vitamins every day at 9 pm”, “note: gift ideas”.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
