package com.planr.app.ui.intercept

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
import androidx.compose.material3.Button
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
import com.planr.app.PlanrApp
import com.planr.app.data.PassStore
import com.planr.app.data.db.ShieldRule
import com.planr.app.engine.InterceptMode
import com.planr.app.ui.components.AppIcon
import com.planr.app.ui.components.Gap
import com.planr.app.ui.components.formatDuration
import com.planr.app.ui.theme.Accent
import com.planr.app.ui.theme.AccentSoft
import com.planr.app.ui.theme.PlanrTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
            PlanrTheme {
                BackHandler { goHome() }
                target?.let { t ->
                    InterceptScreen(
                        pkg = t.pkg,
                        mode = t.mode,
                        scheduleName = t.scheduleName,
                        onClose = { goHome() },
                        onContinue = { minutes ->
                            PassStore.grant(t.pkg, minutes)
                            finish()
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
) {
    val app = PlanrApp.instance
    val label = remember(pkg) { app.apps.labelOf(pkg) }
    val rule by produceState<ShieldRule?>(null, pkg) {
        value = app.db.shieldDao().observeAll().first().firstOrNull { it.packageName == pkg }
    }
    val usedMs by produceState(0L, pkg) {
        value = withContext(Dispatchers.IO) { app.usage.todayUsage(0)[pkg] ?: 0L }
    }

    Column(
        Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AppIcon(pkg, 64.dp)
        Gap(16.dp)
        when (mode) {
            InterceptMode.SCHEDULE -> {
                Title("$label is blocked right now")
                Sub("“${scheduleName ?: "Schedule"}” is active.")
            }
            InterceptMode.LIMIT -> {
                Title("Daily limit reached")
                Sub("You've used $label for ${formatDuration(usedMs)} today. It unlocks again tomorrow.")
            }
            InterceptMode.SHIELD -> {
                Title("Take a breath")
                Sub("Why are you opening $label right now? ${formatDuration(usedMs)} used today.")
                Gap(32.dp)
                rule?.let { r -> BreathingGate(r, onContinue) }
            }
        }
        Gap(32.dp)
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Close $label")
        }
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
