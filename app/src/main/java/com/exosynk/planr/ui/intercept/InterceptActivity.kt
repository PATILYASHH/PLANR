package com.exosynk.planr.ui.intercept

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.exosynk.planr.PlanrApp
import com.exosynk.planr.data.PassStore
import com.exosynk.planr.data.Progression
import com.exosynk.planr.data.db.ShieldRule
import com.exosynk.planr.engine.Enforcer
import com.exosynk.planr.engine.InterceptMode
import com.exosynk.planr.ui.components.AppIcon
import com.exosynk.planr.ui.components.Gap
import com.exosynk.planr.ui.components.formatDuration
import com.exosynk.planr.ui.theme.Accent
import com.exosynk.planr.ui.theme.AccentSoft
import com.exosynk.planr.ui.theme.PlanrTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Shield pause, daily-limit block, or schedule block for one app. */
class InterceptActivity : ComponentActivity() {

    private var target by mutableStateOf<Target?>(null)

    private data class Target(val pkg: String, val mode: InterceptMode, val scheduleName: String?)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readIntent(intent)
        setContent {
            PlanrTheme(forceDark = true) {
                BackHandler { goHome() }
                target?.let { t ->
                    InterceptScreen(
                        pkg = t.pkg,
                        mode = t.mode,
                        scheduleName = t.scheduleName,
                        onClose = {
                            // Walking away from a pause earns XP.
                            if (t.mode == InterceptMode.SHIELD || t.mode == InterceptMode.GATEWAY) {
                                PlanrApp.instance.progress.update { Progression.resisted(it) }
                            }
                            goHome()
                        },
                        onContinue = { minutes ->
                            PassStore.grant(t.pkg, minutes)
                            if (t.mode == InterceptMode.SHIELD) PlanrApp.instance.counters.recordOpen(t.pkg)
                            finish()
                        },
                        onEmergency = {
                            val app = PlanrApp.instance
                            val s = app.settings.current
                            if (app.counters.useEmergency(s.emergencyQuota)) {
                                PassStore.grantEmergency(t.pkg, s.emergencyMinutes)
                                finish()
                            }
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readIntent(intent)
    }

    private fun readIntent(intent: Intent) {
        val pkg = intent.getStringExtra(EXTRA_PACKAGE)
        if (pkg == null) {
            finish()
            return
        }
        val mode = runCatching {
            InterceptMode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: "")
        }.getOrDefault(InterceptMode.SHIELD)
        target = Target(pkg, mode, intent.getStringExtra(EXTRA_SCHEDULE))
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    companion object {
        const val EXTRA_PACKAGE = "pkg"
        const val EXTRA_MODE = "mode"
        const val EXTRA_SCHEDULE = "schedule"
    }
}

@Composable
private fun InterceptScreen(
    pkg: String,
    mode: InterceptMode,
    scheduleName: String?,
    onClose: () -> Unit,
    onContinue: (minutes: Int) -> Unit,
    onEmergency: () -> Unit,
) {
    val app = PlanrApp.instance
    val label = remember(pkg) { app.apps.labelOf(pkg) }
    val rule by produceState<ShieldRule?>(null, pkg, mode) {
        value = if (mode == InterceptMode.GATEWAY) {
            // Mindful Gateway uses one shared pause for every unshielded app.
            val s = app.settings.current
            ShieldRule(pkg, label, pauseSeconds = s.gatewayPauseSeconds, passMinutes = s.gatewayPassMinutes)
        } else {
            app.db.shieldDao().get(pkg)
        }
    }
    val usedMs by produceState(0L, pkg) {
        value = withContext(Dispatchers.IO) { app.usage.todayUsage(0)[pkg] ?: 0L }
    }
    val settings = app.settings.current
    val emergencyLeft = remember(pkg, mode) {
        (settings.emergencyQuota - app.counters.emergencyUsedToday()).coerceAtLeast(0)
    }
    // Emergency passes cover hard blocks, never the Lockdown block on Android Settings.
    val canUseEmergency = settings.emergencyQuota > 0 &&
        (mode == InterceptMode.LIMIT || mode == InterceptMode.OPEN_LIMIT ||
            (mode == InterceptMode.SCHEDULE && scheduleName != Enforcer.LOCKDOWN))
    var confirmEmergency by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding().padding(28.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AppIcon(pkg, 64.dp)
        Gap(16.dp)
        when (mode) {
            InterceptMode.SCHEDULE -> {
                Title("$label is blocked right now")
                if (scheduleName == Enforcer.LOCKDOWN) {
                    Sub("Lockdown is active. Android Settings opens again when it ends.")
                } else {
                    Sub("“${scheduleName ?: "Schedule"}” is active.")
                }
            }
            InterceptMode.LIMIT -> {
                Title("Daily limit reached")
                Sub("You've used $label for ${formatDuration(usedMs)} today. It unlocks again tomorrow.")
            }
            InterceptMode.OPEN_LIMIT -> {
                Title("No opens left today")
                val limit = rule?.dailyOpenLimit ?: 0
                Sub("You've opened $label $limit ${if (limit == 1) "time" else "times"} today. It unlocks again tomorrow.")
            }
            InterceptMode.SHIELD, InterceptMode.GATEWAY -> {
                Title("Take a breath")
                val opens = rule?.dailyOpenLimit?.takeIf { it > 0 }?.let { limit ->
                    val left = (limit - app.counters.opensToday(pkg)).coerceAtLeast(0)
                    " $left of $limit opens left today."
                } ?: ""
                Sub("Why are you opening $label right now? ${formatDuration(usedMs)} used today.$opens")
                Gap(32.dp)
                rule?.let { r -> BreathingGate(r, onContinue) }
            }
        }
        Gap(32.dp)
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Close $label")
        }
        if (canUseEmergency) {
            Gap(8.dp)
            TextButton(
                onClick = { confirmEmergency = true },
                enabled = emergencyLeft > 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (emergencyLeft > 0) "Use an emergency pass ($emergencyLeft left today)"
                    else "No emergency passes left today",
                    color = Color.White.copy(alpha = 0.5f),
                )
            }
        }
    }

    if (confirmEmergency) {
        AlertDialog(
            onDismissRequest = { confirmEmergency = false },
            title = { Text("Use an emergency pass?") },
            text = {
                Text(
                    "$label opens for ${settings.emergencyMinutes} minutes. You have $emergencyLeft " +
                        "left today. Only use it if you really need this app right now."
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmEmergency = false; onEmergency() }) { Text("Use pass") }
            },
            dismissButton = { TextButton(onClick = { confirmEmergency = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BreathingGate(rule: ShieldRule, onContinue: (Int) -> Unit) {
    val total = rule.pauseSeconds + rule.delaySeconds
    var left by remember(rule.packageName) { mutableIntStateOf(total) }
    LaunchedEffect(rule.packageName) {
        while (left > 0) {
            delay(1000)
            left--
        }
    }

    val transition = rememberInfiniteTransition(label = "breath")
    val scale by transition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4000), RepeatMode.Reverse),
        label = "breathScale",
    )
    Box(contentAlignment = Alignment.Center) {
        Box(Modifier.size(160.dp).scale(scale).clip(CircleShape).background(AccentSoft))
        Text(
            if (left > 0) "$left" else "✓",
            style = MaterialTheme.typography.displayLarge,
            color = Accent,
        )
    }
    Gap(24.dp)
    OutlinedButton(
        onClick = { onContinue(rule.passMinutes) },
        enabled = left == 0,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        Text(if (left > 0) "Continue in ${left}s" else "Continue for ${rule.passMinutes} min")
    }
}

@Composable
private fun Title(text: String) =
    Text(text, style = MaterialTheme.typography.headlineMedium, color = Color.White, textAlign = TextAlign.Center)

@Composable
private fun Sub(text: String) =
    Text(text, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
