package com.exosynk.planr.ui.components

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.exosynk.planr.PlanrApp
import com.exosynk.planr.data.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val iconCache = android.util.LruCache<String, ImageBitmap>(120)

private fun loadIcon(context: Context, pkg: String): ImageBitmap? {
    iconCache.get(pkg)?.let { return it }
    return runCatching {
        context.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap()
    }.getOrNull()?.also { iconCache.put(pkg, it) }
}

@Composable
fun AppIcon(packageName: String, size: Dp = 40.dp) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(iconCache.get(packageName), packageName) {
        value = withContext(Dispatchers.IO) { loadIcon(context, packageName) }
    }
    val b = bitmap
    if (b != null) {
        Image(bitmap = b, contentDescription = null, modifier = Modifier.size(size).clip(CircleShape))
    } else {
        Box(Modifier.size(size).clip(CircleShape))
    }
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val m = modifier.fillMaxWidth()
    Card(
        modifier = if (onClick != null) m.clickable(onClick = onClick) else m,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(20.dp), content = content)
    }
}

@Composable
fun AppRow(packageName: String, label: String, subtitle: String? = null, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(packageName)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing()
    }
}

@Composable
fun rememberInstalledApps(): List<InstalledApp> {
    val apps by produceState(emptyList<InstalledApp>()) {
        value = withContext(Dispatchers.IO) { PlanrApp.instance.apps.launchableApps(forceRefresh = true) }
    }
    return apps
}

/** Multi-select app picker in a bottom sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerSheet(
    title: String,
    initiallySelected: Set<String>,
    maxSelection: Int = Int.MAX_VALUE,
    exclude: Set<String> = emptySet(),
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val apps = rememberInstalledApps().filter { it.packageName !in exclude }
    val selected = remember { mutableStateListOf<String>().apply { addAll(initiallySelected) } }
    var query by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (maxSelection != Int.MAX_VALUE) {
                Text(
                    "${selected.size} of $maxSelection selected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = { Text("Search apps") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            val filtered = apps.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
            LazyColumn(Modifier.height(420.dp)) {
                items(filtered, key = { it.packageName }) { app ->
                    val checked = app.packageName in selected
                    val toggle: () -> Unit = {
                        if (checked) {
                            selected.remove(app.packageName)
                        } else if (selected.size < maxSelection) {
                            selected.add(app.packageName)
                        }
                    }
                    Box(Modifier.clickable { toggle() }) {
                        AppRow(app.packageName, app.label) {
                            Checkbox(checked = checked, onCheckedChange = { toggle() })
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onConfirm(selected.toSet()) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Done") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

fun formatDuration(ms: Long): String {
    val totalMin = (ms / 60_000L).coerceAtLeast(0)
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}

fun formatClock(minuteOfDay: Int): String {
    val h = minuteOfDay / 60
    val m = minuteOfDay % 60
    val suffix = if (h < 12) "AM" else "PM"
    val h12 = when (val x = h % 12) { 0 -> 12; else -> x }
    return "%d:%02d %s".format(h12, m, suffix)
}

@Composable
fun Gap(h: Dp) = Spacer(Modifier.height(h))

/** Lockdown state, re-checked every 30 s and whenever settings change. */
@Composable
fun rememberLockdown(): Boolean {
    val settings by PlanrApp.instance.settings.state.collectAsState()
    var now by remember { mutableStateOf(java.time.LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = java.time.LocalDateTime.now()
        }
    }
    return settings.isLockdown(now)
}

@Composable
fun LockdownBanner(what: String = "Existing rules can't be edited, paused or removed") {
    val settings by PlanrApp.instance.settings.state.collectAsState()
    val end = settings.lockdownEndsAt()
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(
                "Lockdown is on" + (end?.let { " until ${formatClock(it.hour * 60 + it.minute)}" } ?: "") +
                    ". $what until it ends.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
