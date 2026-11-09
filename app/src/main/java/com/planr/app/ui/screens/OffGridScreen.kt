package com.planr.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.planr.app.PlanrApp
import com.planr.app.data.OffGridStore
import com.planr.app.data.UnlockChallenge
import com.planr.app.engine.OffGridManager
import com.planr.app.engine.PlanrAccessibilityService
import com.planr.app.ui.components.AppIcon
import com.planr.app.ui.components.AppPickerSheet
import com.planr.app.ui.components.Gap
import com.planr.app.ui.components.SectionCard
import com.planr.app.ui.components.formatDuration
import com.planr.app.ui.theme.Danger
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val presets = listOf(15, 30, 60, 120, 240, 480)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun OffGridScreen(modifier: Modifier) {
    val app = PlanrApp.instance
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var minutes by remember { mutableFloatStateOf(60f) }
    var allowCamera by remember { mutableStateOf(true) }
    var extras by remember { mutableStateOf(emptySet<String>()) }
    var challenge by remember { mutableStateOf(UnlockChallenge.TYPING) }
    var picking by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }

    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val serviceOn = remember(tick) { PlanrAccessibilityService.isEnabled(context) }

    val durationMs = minutes.roundToInt() * 60_000L

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
    ) {
        Gap(24.dp)
        Text("Off-Grid Mode", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Lock your whole phone to the essentials. Only calls, camera and the apps you pick will work until the timer ends.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Gap(20.dp)

        SectionCard {
            Text("Duration", style = MaterialTheme.typography.titleMedium)
            Text(formatDuration(durationMs), style = MaterialTheme.typography.displayLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { p ->
                    FilterChip(
                        selected = minutes.roundToInt() == p,
                        onClick = { minutes = p.toFloat() },
                        label = { Text(formatDuration(p * 60_000L)) },
                    )
                }
            }
            // 15 min … 24 h in 15-minute steps
            Slider(
                value = minutes,
                onValueChange = { minutes = (it / 15f).roundToInt() * 15f },
                valueRange = 15f..1440f,
                steps = 94,
            )
        }
        Gap(16.dp)

        SectionCard {
            Text("What stays usable", style = MaterialTheme.typography.titleMedium)
            Gap(8.dp)
            Text("Phone & emergency calls — always on", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Camera", modifier = Modifier.weight(1f))
                Switch(checked = allowCamera, onCheckedChange = { allowCamera = it })
            }
            Gap(8.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                extras.forEach { AppIcon(it, 36.dp) }
            }
            if (extras.isNotEmpty()) Gap(8.dp)
            OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (extras.isEmpty()) "Add extra apps (up to ${OffGridStore.MAX_EXTRA_APPS})"
                    else "Edit extra apps (${extras.size}/${OffGridStore.MAX_EXTRA_APPS})"
                )
            }
        }
        Gap(16.dp)

        SectionCard {
            Text("To unlock early", style = MaterialTheme.typography.titleMedium)
            Gap(4.dp)
            UnlockChallenge.entries.forEach { c ->
                Row(
                    Modifier.fillMaxWidth()
                        .selectable(selected = challenge == c, onClick = { challenge = c })
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = challenge == c, onClick = { challenge = c })
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(c.title, style = MaterialTheme.typography.titleMedium)
                        Text(c.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Gap(20.dp)

        if (!serviceOn) {
            Text(
                "Turn on the PLANR accessibility service (Home → Finish setup) to use Off-Grid.",
                color = Danger,
            )
            Gap(8.dp)
        }
        Button(
            onClick = { confirming = true },
            enabled = serviceOn,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("Start Off-Grid for ${formatDuration(durationMs)}") }
        Gap(32.dp)
    }

    if (picking) {
        AppPickerSheet(
            title = "Keep these apps",
            initiallySelected = extras,
            maxSelection = OffGridStore.MAX_EXTRA_APPS,
            exclude = app.apps.alwaysAllowed(),
            onDismiss = { picking = false },
            onConfirm = { extras = it; picking = false },
        )
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Go Off-Grid?") },
            text = {
                Text(
                    "Your phone will be locked for ${formatDuration(durationMs)}. " +
                        "Settings, the launcher and all other apps will be blocked. " +
                        "Ending early requires the ${challenge.title.lowercase()} and breaks your streak."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    scope.launch {
                        OffGridManager.start(context, durationMs, extras, allowCamera, challenge)
                    }
                }) { Text("Lock my phone") }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel") } },
        )
    }
}
