package com.exosynk.planr.ui.lock

import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.exosynk.planr.PlanrApp
import com.exosynk.planr.engine.OffGridManager
import com.exosynk.planr.ui.MainActivity
import com.exosynk.planr.ui.components.AppIcon
import com.exosynk.planr.ui.components.Gap
import com.exosynk.planr.ui.theme.Accent
import com.exosynk.planr.ui.theme.PlanrTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Full-screen Off-Grid lock. Back does nothing; Home is bounced back here by the service. */
class LockActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (!PlanrApp.instance.offGrid.isLocked()) {
            OffGridManager.completeIfExpired()
            finish()
            return
        }
        hideSystemBars()
        setContent {
            PlanrTheme(forceDark = true) {
                BackHandler { /* swallow */ }
                LockScreen(
                    onFinished = { early -> exitLock(early) },
                    launch = { safeStart(it) },
                )
            }
        }
    }

    private var exited = false

    /**
     * Hides the status and navigation bars. A swipe only peeks them briefly, and while
     * the lock screen is up the accessibility service also shields that area, so
     * Back / Home / Recents can't take you out of Off-Grid.
     */
    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun exitLock(early: Boolean) {
        if (exited) return
        exited = true
        if (early) OffGridManager.endEarly() else OffGridManager.completeIfExpired()
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    private fun safeStart(intent: Intent?) {
        if (intent == null) return
        runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun LockScreen(onFinished: (early: Boolean) -> Unit, launch: (Intent?) -> Unit) {
    val state by PlanrApp.instance.offGrid.state.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var unlocking by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val remaining = state.endAt - now
    LaunchedEffect(state.active, remaining <= 0) {
        if (!state.active || remaining <= 0) onFinished(false)
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            // The bars are hidden and their area is shielded (see OffGridShield), so keep
            // every button clear of it: pad by the bars and gesture areas even while hidden.
            .windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.systemGestures))
            .windowInsetsPadding(WindowInsets.displayCutout)
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        if (unlocking) {
            ChallengeHost(
                state = state,
                now = now,
                onCancel = { unlocking = false },
                onPassed = { onFinished(true) },
            )
        } else {
            LockContent(state, remaining, launch, onUnlockClick = { unlocking = true })
        }
    }
}

@Composable
private fun LockContent(
    state: com.exosynk.planr.data.OffGridState,
    remaining: Long,
    launch: (Intent?) -> Unit,
    onUnlockClick: () -> Unit,
) {
    val app = PlanrApp.instance
    var noting by remember { mutableStateOf(false) }
    if (noting) QuickNoteDialog { noting = false }
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("You're Off-Grid", style = MaterialTheme.typography.titleLarge, color = Accent)
            Gap(16.dp)
            Text(
                formatHms(remaining),
                style = MaterialTheme.typography.displayLarge,
                color = Color.White,
            )
            Text(
                "until ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(state.endAt))}",
                color = Color.White.copy(alpha = 0.6f),
            )
            Gap(48.dp)
            Text(
                "Put the phone down. Go outside, read, talk to someone.",
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
            NextAlarmLine()
            Gap(48.dp)

            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                RoundAction(Icons.Default.Call, "Phone") {
                    launch(Intent(Intent.ACTION_DIAL))
                }
                if (state.allowCamera) {
                    RoundAction(Icons.Default.PhotoCamera, "Camera") {
                        launch(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
                    }
                }
                RoundAction(Icons.Outlined.EditNote, "Note") { noting = true }
            }
            if (state.extraAllowed.isNotEmpty()) {
                Gap(24.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    state.extraAllowed.sorted().forEach { pkg ->
                        Box(Modifier.clip(CircleShape).clickable { launch(app.apps.launchIntent(pkg)) }) {
                            AppIcon(pkg, 52.dp)
                        }
                    }
                }
            }
        }

        TextButton(
            onClick = onUnlockClick,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        ) { Text("Emergency unlock", color = Color.White.copy(alpha = 0.5f)) }
    }
}

@Composable
private fun RoundAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(Color(0xFF1D2029)).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = label, tint = Color.White) }
        Gap(6.dp)
        Text(label, color = Color.White.copy(alpha = 0.7f))
    }
}

internal fun formatHms(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}

/** Shows the next alarm so you know Off-Grid won't make you oversleep. */
@Composable
private fun NextAlarmLine() {
    val app = PlanrApp.instance
    val alarms by app.db.alarmDao().observeAll().collectAsState(initial = emptyList())
    val groups by app.db.alarmGroupDao().observeAll().collectAsState(initial = emptyList())
    val next = com.exosynk.planr.alarm.Alarms.nextOf(alarms, groups) ?: return
    Gap(16.dp)
    Text(
        "⏰ " + next.second.toLocalTime().format(
            java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT)
        ) + (if (next.first.label.isNotBlank()) " · ${next.first.label}" else ""),
        color = Color.White.copy(alpha = 0.6f),
    )
}

/**
 * Off-Grid idea capture: write the thought down instead of reaching for an app.
 * Saved to Notes (pinned), so it's waiting when you're back.
 */
@Composable
private fun QuickNoteDialog(onDismiss: () -> Unit) {
    val app = PlanrApp.instance
    var text by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Jot it down") },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(2000) },
                placeholder = { Text("A thought, a to-do, something to look up later…") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = {
                val body = text.trim()
                app.appScope.launch {
                    app.db.noteDao().upsert(
                        com.exosynk.planr.notes.Note(title = "Off-Grid thought", body = body, pinned = true)
                    )
                }
                onDismiss()
            }) { Text("Save to Notes") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
