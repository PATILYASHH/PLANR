package com.planr.app.ui.lock

import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.PhotoCamera
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
import com.planr.app.PlanrApp
import com.planr.app.engine.OffGridManager
import com.planr.app.ui.MainActivity
import com.planr.app.ui.components.AppIcon
import com.planr.app.ui.components.Gap
import com.planr.app.ui.theme.Accent
import com.planr.app.ui.theme.PlanrTheme
import kotlinx.coroutines.delay
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
        setContent {
            PlanrTheme {
                BackHandler { /* swallow */ }
                LockScreen(
                    onFinished = { early -> exitLock(early) },
                    launch = { safeStart(it) },
                )
            }
        }
    }

    private var exited = false

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
        Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding().padding(24.dp),
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
    state: com.planr.app.data.OffGridState,
    remaining: Long,
    launch: (Intent?) -> Unit,
    onUnlockClick: () -> Unit,
) {
    val app = PlanrApp.instance
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
