package com.exosynk.planr.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.exosynk.planr.PlanrApp
import com.exosynk.planr.data.FocusPhase
import com.exosynk.planr.ui.components.AppIcon
import com.exosynk.planr.ui.components.AppPickerSheet
import com.exosynk.planr.ui.components.Gap
import com.exosynk.planr.ui.components.SectionCard
import com.exosynk.planr.ui.components.formatDuration
import com.exosynk.planr.ui.components.rememberLockdown
import com.exosynk.planr.ui.lock.formatHms
import com.exosynk.planr.ui.theme.Accent
import com.exosynk.planr.ui.theme.AccentSoft
import com.exosynk.planr.ui.theme.Success
import kotlinx.coroutines.delay

@Composable
fun FocusScreen(modifier: Modifier) {
    val focus = PlanrApp.instance.focus
    val state by focus.state.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            focus.advance(now)
            delay(1000)
        }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
    ) {
        Gap(24.dp)
        Text("Focus", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Pomodoro cycles. Chosen apps are blocked during focus blocks and open again on breaks.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Gap(20.dp)
        if (state.active) ActiveFocus(now) else FocusSetup()
        Gap(16.dp)
        SectionCard {
            Text("Focused today", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatDuration(state.minutesToday * 60_000L), style = MaterialTheme.typography.displayLarge)
        }
        Gap(24.dp)
    }
}

@Composable
private fun ActiveFocus(now: Long) {
    val focus = PlanrApp.instance.focus
    val s by focus.state.collectAsState()
    val isFocus = s.phase == FocusPhase.FOCUS
    val phaseMs = (if (isFocus) s.focusMinutes else s.breakMinutes) * 60_000L
    val left = (s.phaseEndAt - now).coerceAtLeast(0)
    val progress = if (phaseMs > 0) 1f - left.toFloat() / phaseMs else 0f
    val ringColor = if (isFocus) Accent else Success

    SectionCard {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val trackColor = AccentSoft
            Canvas(Modifier.size(240.dp)) {
                val stroke = 14.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(trackColor, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                drawArc(
                    ringColor, -90f, 360f * progress, false, Offset(inset, inset), arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (isFocus) "Focus" else "Break", color = ringColor)
                Text(formatHms(left).removePrefix("0:"), style = MaterialTheme.typography.displayLarge)
                Text("Block ${s.cycle} of ${s.totalCycles}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Gap(16.dp)
        if (s.blocked.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                s.blocked.sorted().take(8).forEach { AppIcon(it, 32.dp) }
            }
            Gap(16.dp)
        }
        val lockdown = rememberLockdown()
        OutlinedButton(
            onClick = { focus.stop() },
            enabled = !lockdown,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(if (lockdown) "Can't stop during Lockdown" else "Stop session")
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FocusSetup() {
    val app = PlanrApp.instance
    val shielded by app.db.shieldDao().observeAll().collectAsState(initial = emptyList())
    var focusMin by remember { mutableIntStateOf(25) }
    var breakMin by remember { mutableIntStateOf(5) }
    var cycles by remember { mutableIntStateOf(4) }
    var blocked by remember { mutableStateOf<Set<String>?>(null) }
    var picking by remember { mutableStateOf(false) }

    // Default to the user's shielded apps until they pick their own list.
    val effective = blocked ?: shielded.map { it.packageName }.toSet()

    SectionCard {
        ChipRow("Focus", listOf(25, 50, 90), focusMin, { "${it}m" }) { focusMin = it }
        ChipRow("Break", listOf(5, 10, 15), breakMin, { "${it}m" }) { breakMin = it }
        ChipRow("Blocks", listOf(1, 2, 4, 6), cycles, { "$it" }) { cycles = it }
        Gap(8.dp)
        if (effective.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                effective.sorted().forEach { AppIcon(it, 32.dp) }
            }
            Gap(8.dp)
        }
        OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Apps to block (${effective.size})")
        }
        Gap(12.dp)
        val total = cycles * focusMin + (cycles - 1) * breakMin
        Button(
            onClick = { app.focus.start(focusMin, breakMin, cycles, effective) },
            enabled = effective.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("Start · ${formatDuration(total * 60_000L)} total") }
    }

    if (picking) {
        AppPickerSheet(
            title = "Block during focus",
            initiallySelected = effective,
            exclude = app.apps.alwaysAllowed(),
            onDismiss = { picking = false },
            onConfirm = { blocked = it; picking = false },
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(
    label: String,
    options: List<Int>,
    selected: Int,
    text: (Int) -> String,
    onSelect: (Int) -> Unit,
) {
    Text(label, style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(text(o)) })
        }
    }
    Gap(8.dp)
}
