package com.exosynk.planr.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.exosynk.planr.PlanrApp
import com.exosynk.planr.data.db.ShieldRule
import com.exosynk.planr.data.db.wouldLoosen
import com.exosynk.planr.ui.components.LockdownBanner
import com.exosynk.planr.ui.components.rememberLockdown
import com.exosynk.planr.ui.components.AppPickerSheet
import com.exosynk.planr.ui.components.AppRow
import com.exosynk.planr.ui.components.Gap
import com.exosynk.planr.ui.theme.Danger
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun ShieldsScreen(modifier: Modifier) {
    val app = PlanrApp.instance
    val rules by app.db.shieldDao().observeAll().collectAsState(initial = emptyList())
    var picking by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ShieldRule?>(null) }
    val lockdown = rememberLockdown()

    Box(modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(20.dp, 24.dp, 20.dp, 96.dp)) {
            item {
                Text("Shields", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "A mindful pause before distracting apps, with optional delays and daily limits.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Gap(16.dp)
                if (lockdown) {
                    LockdownBanner("Shields can be added or tightened, not loosened or removed,")
                    Gap(16.dp)
                }
                if (rules.isEmpty()) {
                    Text("No shields yet. Tap “Add shields” to protect your first app.")
                }
            }
            items(rules, key = { it.packageName }) { rule ->
                Box(Modifier.clickable { editing = rule }) {
                    AppRow(rule.packageName, rule.appLabel, describe(rule)) {
                        Switch(
                            checked = rule.enabled,
                            // Lockdown: a paused shield may be switched on, not off.
                            enabled = !lockdown || !rule.enabled,
                            onCheckedChange = { on ->
                                app.appScope.launch { app.db.shieldDao().upsert(rule.copy(enabled = on)) }
                            },
                        )
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { picking = true },
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text("Add shields") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }

    if (picking) {
        AppPickerSheet(
            title = "Shield apps",
            initiallySelected = emptySet(),
            exclude = rules.map { it.packageName }.toSet(),
            onDismiss = { picking = false },
            onConfirm = { chosen ->
                picking = false
                app.appScope.launch {
                    chosen.forEach { pkg ->
                        app.db.shieldDao().upsert(ShieldRule(pkg, app.apps.labelOf(pkg)))
                    }
                }
            },
        )
    }

    editing?.let { rule ->
        ShieldEditDialog(
            rule = rule,
            lockdown = lockdown,
            onDismiss = { editing = null },
            onSave = { updated ->
                editing = null
                app.appScope.launch { app.db.shieldDao().upsert(updated) }
            },
            onDelete = if (lockdown) null else ({
                editing = null
                app.appScope.launch { app.db.shieldDao().delete(rule.packageName) }
            }),
        )
    }
}

private fun describe(r: ShieldRule): String = buildList {
    add("${r.pauseSeconds}s pause")
    if (r.delaySeconds > 0) add("${r.delaySeconds}s delay")
    if (r.dailyLimitMinutes > 0) add("${r.dailyLimitMinutes}m/day limit")
    if (r.dailyOpenLimit > 0) add("${r.dailyOpenLimit} opens/day")
    add("${r.passMinutes}m pass")
}.joinToString(" · ")

@Composable
private fun ShieldEditDialog(
    rule: ShieldRule,
    lockdown: Boolean,
    onDismiss: () -> Unit,
    onSave: (ShieldRule) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var pause by remember { mutableFloatStateOf(rule.pauseSeconds.toFloat()) }
    var delay by remember { mutableFloatStateOf(rule.delaySeconds.toFloat()) }
    var limit by remember { mutableFloatStateOf(rule.dailyLimitMinutes.toFloat()) }
    var pass by remember { mutableFloatStateOf(rule.passMinutes.toFloat()) }
    var opens by remember { mutableFloatStateOf(rule.dailyOpenLimit.toFloat()) }

    val updated = rule.copy(
        pauseSeconds = pause.roundToInt(),
        delaySeconds = delay.roundToInt(),
        dailyLimitMinutes = limit.roundToInt(),
        passMinutes = pass.roundToInt(),
        dailyOpenLimit = opens.roundToInt(),
    )
    val blockedByLockdown = lockdown && rule.wouldLoosen(updated)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(rule.appLabel) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                LabeledSlider("Breathing pause: ${pause.roundToInt()}s", pause, 3f..30f, 26) { pause = it }
                LabeledSlider(
                    "Forced delay: ${if (delay < 1) "off" else "${delay.roundToInt()}s"}",
                    delay, 0f..60f, 11,
                ) { delay = it }
                LabeledSlider(
                    "Daily limit: ${if (limit < 1) "none" else "${limit.roundToInt()} min"}",
                    limit, 0f..180f, 35,
                ) { limit = it }
                LabeledSlider(
                    "Opens per day: ${if (opens < 1) "unlimited" else "${opens.roundToInt()}"}",
                    opens, 0f..20f, 19,
                ) { opens = it }
                LabeledSlider("Pass after continuing: ${pass.roundToInt()} min", pass, 1f..30f, 28) { pass = it }
                if (blockedByLockdown) {
                    Text(
                        "Lockdown is on: you can only make this shield stricter.",
                        color = Danger,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (onDelete != null) {
                    Gap(8.dp)
                    TextButton(onClick = onDelete) { Text("Remove shield", color = Danger) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !blockedByLockdown, onClick = { onSave(updated) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
) {
    Text(label, style = MaterialTheme.typography.labelLarge)
    Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
}
