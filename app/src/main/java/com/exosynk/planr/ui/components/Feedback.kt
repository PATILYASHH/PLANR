package com.exosynk.planr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableSharedFlow

/** A short message at the bottom of the screen, optionally with one action (e.g. Undo). */
data class SnackMessage(val text: String, val action: String? = null, val onAction: (() -> Unit)? = null)

/** App-wide snackbar: call [show] from anywhere; MainActivity displays it. */
object Snack {
    val messages = MutableSharedFlow<SnackMessage>(extraBufferCapacity = 8)

    fun show(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
        messages.tryEmit(SnackMessage(text, action, onAction))
    }
}

/** Friendly placeholder for an empty list: icon, what goes here, and how to start. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    hint: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp)) }
        Gap(12.dp)
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        if (action != null && onAction != null) {
            Gap(12.dp)
            FilledTonalButton(onClick = onAction) { Text(action) }
        }
    }
}
