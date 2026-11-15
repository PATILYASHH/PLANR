package com.exosynk.planr.smart

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.exosynk.planr.PlanrApp
import com.exosynk.planr.ui.MainActivity
import com.exosynk.planr.ui.components.Gap
import com.exosynk.planr.ui.theme.PlanrTheme

/**
 * "Share → PLANR" and the text-selection menu's "PLANR": the shared text goes
 * through the smart parser ("Dentist Tue 4pm", "call Parth at 9") and is saved
 * as a reminder, alarm or note in one tap.
 */
class ShareActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Off-Grid: the main app stays locked, so don't offer a way around it.
        if (PlanrApp.instance.offGrid.isLocked()) {
            finish()
            return
        }
        val shared = sharedText(intent)
        setContent {
            PlanrTheme {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
                        .clickable(remember { MutableInteractionSource() }, indication = null) { finish() },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                            .background(MaterialTheme.colorScheme.background)
                            .clickable(remember { MutableInteractionSource() }, indication = null) { }
                            .navigationBarsPadding().imePadding().padding(16.dp),
                    ) {
                        Text("Add to PLANR", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp))
                        Gap(8.dp)
                        SmartAddBar(
                            initialText = shared,
                            placeholder = "What should PLANR remember?",
                            onAdded = { finish() },
                            onMoreOptions = { kind ->
                                startActivity(
                                    Intent(this@ShareActivity, MainActivity::class.java)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                        .putExtra(
                                            MainActivity.EXTRA_OPEN,
                                            when (kind) {
                                                SmartKind.REMINDER -> MainActivity.OPEN_NEW_REMINDER
                                                SmartKind.ALARM -> MainActivity.OPEN_NEW_ALARM
                                                SmartKind.NOTE -> MainActivity.OPEN_NEW_NOTE
                                            },
                                        ),
                                )
                                finish()
                            },
                        )
                    }
                }
            }
        }
    }

    private fun sharedText(intent: Intent?): String {
        if (intent == null) return ""
        val text = when (intent.action) {
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
            else -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
        }?.toString().orEmpty()
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()
        // Long shares (articles, links) go in as a note title-sized snippet.
        return listOf(subject, text).filter { it.isNotBlank() }.joinToString(" — ").replace(Regex("\\s+"), " ").trim()
    }
}
