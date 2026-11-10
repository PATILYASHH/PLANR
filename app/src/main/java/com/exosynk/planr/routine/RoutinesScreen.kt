package com.exosynk.planr.routine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.exosynk.planr.PlanrApp
import com.exosynk.planr.alarm.toastOnMain
import com.exosynk.planr.engine.PlanrAccessibilityService
import com.exosynk.planr.ui.components.Gap
import com.exosynk.planr.ui.components.ModuleHeader
import com.exosynk.planr.ui.components.SectionCard
import com.exosynk.planr.ui.theme.Success
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun RoutinesScreen(modifier: Modifier, onEdit: (Long) -> Unit, onBack: (() -> Unit)? = null) {
    if (onBack != null) androidx.activity.compose.BackHandler(onBack = onBack)
    val app = PlanrApp.instance
    val context = LocalContext.current
    val routines by app.db.routineDao().observeAll().collectAsState(initial = emptyList())
    val alarms by app.db.alarmDao().observeAll().collectAsState(initial = emptyList())
    val groups by app.db.alarmGroupDao().observeAll().collectAsState(initial = emptyList())
    var active by remember { mutableStateOf(RoutineEngine.activeIds(app)) }
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    LaunchedEffect(tick) {
        while (true) {
            active = RoutineEngine.activeIds(app)
            delay(3_000)
        }
    }
    val serviceOn = remember(tick) { PlanrAccessibilityService.isEnabled(context) }
    val labels = RoutineLabels(
        app = { app.apps.labelOf(it) },
        alarm = { id -> alarms.firstOrNull { it.id == id }?.let { a -> a.label.ifBlank { "%d:%02d".format(a.hour, a.minute) } } },
        group = { id -> groups.firstOrNull { it.id == id }?.name },
    )
    val templates = Routines.templates().filter { t -> routines.none { it.name == t.name } }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(20.dp, 24.dp, 20.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                if (onBack != null) {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
                ModuleHeader("Routines", "If this happens, then PLANR does that, and can undo it afterwards.")
                if (!serviceOn) {
                    Gap(8.dp)
                    Text(
                        "Charging, battery, headphones and app triggers, and “Open app”, need the PLANR accessibility service (Habit → Home → Finish setup). Time, alarm, Off-Grid and Focus triggers work without it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val running = routines.filter { it.id in active }
                if (running.isNotEmpty()) {
                    Gap(8.dp)
                    Text("Running now: " + running.joinToString(", ") { it.name }, color = Success)
                }
                if (routines.isEmpty()) {
                    Gap(8.dp)
                    Text("No routines yet. Start from a suggestion below or create your own.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(routines, key = { it.id }) { r ->
                SectionCard(onClick = { onEdit(r.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "If " + RoutineText.trigger(r.parsedTrigger, labels),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "Then " + r.parsedActions.joinToString(", ") { RoutineText.action(it, labels) }.ifBlank { "nothing yet" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (r.id in active) Text("Running", color = Success, style = MaterialTheme.typography.labelLarge)
                        }
                        Switch(checked = r.enabled, onCheckedChange = { on ->
                            app.appScope.launch { Routines.save(context.applicationContext, r.copy(enabled = on)) }
                        })
                    }
                    Gap(4.dp)
                    OutlinedButton(onClick = {
                        RoutineEngine.runNow(r.id)
                        toastOnMain(context, "Running “${r.name}”")
                    }) {
                        Icon(Icons.Outlined.PlayArrow, null)
                        Text("Run now")
                    }
                }
            }
            if (templates.isNotEmpty()) {
                item {
                    Gap(8.dp)
                    Text("Suggestions", style = MaterialTheme.typography.titleLarge)
                }
                items(templates, key = { "t-" + it.name }) { t ->
                    SectionCard {
                        Text(t.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "If " + RoutineText.trigger(t.parsedTrigger, labels) + ", then " +
                                t.parsedActions.joinToString(", ") { RoutineText.action(it, labels) },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Gap(8.dp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = {
                                app.appScope.launch { Routines.save(context.applicationContext, t) }
                            }) { Text("Add") }
                            TextButton(onClick = {
                                app.appScope.launch {
                                    val id = Routines.save(context.applicationContext, t.copy(enabled = false))
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onEdit(id) }
                                }
                            }) { Text("Customize") }
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { onEdit(0L) },
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text("New routine") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }
}
