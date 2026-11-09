package com.planr.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.planr.app.PlanrApp
import com.planr.app.data.db.OffGridSession
import com.planr.app.engine.PlanrAccessibilityService
import com.planr.app.ui.components.AppRow
import com.planr.app.ui.components.Gap
import com.planr.app.ui.components.SectionCard
import com.planr.app.ui.components.formatDuration
import com.planr.app.ui.theme.Danger
import com.planr.app.ui.theme.Success
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalTime

@Composable
fun HomeScreen(modifier: Modifier, onGoOffGrid: () -> Unit) {
    val app = PlanrApp.instance
    val context = LocalContext.current

    // Re-check permissions and usage whenever the screen comes back into view.
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }

    val accessibilityOn = remember(tick) { PlanrAccessibilityService.isEnabled(context) }
    val usageOn = remember(tick) { app.usage.hasPermission() }
    val usage by produceState(emptyMap<String, Long>(), tick, usageOn) {
        value = withContext(Dispatchers.IO) { if (usageOn) app.usage.todayUsage(0) else emptyMap() }
    }
    val sessions by app.db.offGridDao().observeRecent().collectAsState(initial = emptyList())

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
    ) {
        Gap(24.dp)
        Text(greeting(), style = MaterialTheme.typography.headlineMedium)
        Text(
            "Plan your attention.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Gap(20.dp)

        if (!accessibilityOn || !usageOn) {
            SetupCard(accessibilityOn, usageOn)
            Gap(16.dp)
        }

        // Screen time today
        SectionCard {
            Text("Screen time today", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                formatDuration(usage.values.sum()),
                style = MaterialTheme.typography.displayLarge,
            )
            val top = usage.entries.sortedByDescending { it.value }.take(5)
            if (top.isEmpty()) {
                Text(
                    if (usageOn) "No usage recorded yet today." else "Grant usage access to see stats.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            top.forEach { (pkg, ms) ->
                AppRow(pkg, app.apps.labelOf(pkg)) {
                    Text(formatDuration(ms), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Gap(16.dp)

        // Off-Grid summary
        SectionCard {
            Text("Off-Grid", style = MaterialTheme.typography.titleLarge)
            Gap(4.dp)
            val done = sessions.count { it.endedAt != null && !it.endedEarly }
            Text(
                "Streak: ${streak(sessions)} · Completed: $done · Ended early: ${sessions.count { it.endedEarly }}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Gap(12.dp)
            Button(onClick = onGoOffGrid, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Go Off-Grid")
            }
        }
        Gap(24.dp)
    }
}

/** Sessions completed in a row, most recent first; an early exit breaks it. */
private fun streak(sessions: List<OffGridSession>): Int =
    sessions.filter { it.endedAt != null }.takeWhile { !it.endedEarly }.size

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

@Composable
private fun SetupCard(accessibilityOn: Boolean, usageOn: Boolean) {
    val context = LocalContext.current
    var showDisclosure by remember { mutableStateOf(false) }

    SectionCard {
        Text("Finish setup", style = MaterialTheme.typography.titleLarge)
        Gap(8.dp)
        PermissionLine(
            "Accessibility service",
            "Needed to show pauses and keep Off-Grid locked",
            accessibilityOn,
        ) { showDisclosure = true }
        PermissionLine("Usage access", "Needed for screen time and daily limits", usageOn) {
            context.startActivity(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    .setData(Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    // Prominent disclosure (required by Google Play before accessibility settings)
    if (showDisclosure) {
        AlertDialog(
            onDismissRequest = { showDisclosure = false },
            title = { Text("Accessibility permission") },
            text = {
                Text(
                    "PLANR uses the Accessibility API only to detect which app is " +
                        "in front, so it can show mindful pauses, enforce your schedules " +
                        "and keep Off-Grid Mode locked.\n\nPLANR does not read your " +
                        "screen content or what you type, and it has no internet access. " +
                        "Nothing leaves your phone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDisclosure = false
                    context.startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }) { Text("Agree & open settings") }
            },
            dismissButton = {
                TextButton(onClick = { showDisclosure = false }) { Text("Not now") }
            },
        )
    }
}

@Composable
private fun PermissionLine(title: String, why: String, granted: Boolean, onGrant: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (granted) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = if (granted) Success else Danger,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(why, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!granted) FilledTonalButton(onClick = onGrant) { Text("Grant") }
    }
}
