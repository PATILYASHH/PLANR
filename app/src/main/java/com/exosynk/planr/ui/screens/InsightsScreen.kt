package com.exosynk.planr.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.exosynk.planr.PlanrApp
import com.exosynk.planr.data.Achievement
import com.exosynk.planr.data.Progression
import com.exosynk.planr.ui.components.AppRow
import com.exosynk.planr.ui.components.Gap
import com.exosynk.planr.ui.components.SectionCard
import com.exosynk.planr.ui.components.formatDuration
import com.exosynk.planr.ui.theme.Accent
import com.exosynk.planr.ui.theme.AccentSoft
import com.exosynk.planr.ui.theme.Success
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

private const val HEATMAP_WEEKS = 5

@Composable
fun InsightsScreen(modifier: Modifier, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val app = PlanrApp.instance
    val progress by app.progress.state.collectAsState()
    var synced by remember { mutableStateOf(false) }
    var selectedApp by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { runCatching { app.stats.sync() } }
        synced = true
    }
    val today = remember { LocalDate.now() }
    val daily by produceState(emptyMap<LocalDate, Long>(), synced) {
        value = withContext(Dispatchers.IO) { app.stats.dailyTotals(HEATMAP_WEEKS * 7 + 6, today) }
    }
    val top by produceState(emptyList<Pair<String, Long>>(), synced) {
        value = withContext(Dispatchers.IO) { app.stats.topApps(7, today).take(8) }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
    ) {
        Gap(12.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Insights", style = MaterialTheme.typography.headlineMedium)
        }
        Gap(12.dp)

        // Level
        val st = progress.stats
        SectionCard {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("Level ${st.level}", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.weight(1f))
                Text("${st.xp} XP", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Gap(8.dp)
            LinearProgressIndicator(
                progress = { Progression.levelProgress(st.xp) },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                trackColor = AccentSoft,
            )
            Gap(4.dp)
            Text(
                "${Progression.xpForLevel(st.level + 1) - st.xp} XP to level ${st.level + 1}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Gap(16.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat(formatDuration(st.offGridMinutes * 60_000L), "Off-Grid")
                Stat(formatDuration(st.focusMinutes * 60_000L), "Focus")
                Stat("${st.resists}", "Resisted")
                Stat("${st.bestStreak}", "Best streak")
            }
            Gap(8.dp)
            Text(
                "Earn XP for every Off-Grid and focus minute, and for closing a shielded app.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Gap(16.dp)

        // Heatmap
        SectionCard {
            Text("Screen time, last $HEATMAP_WEEKS weeks", style = MaterialTheme.typography.titleLarge)
            Gap(4.dp)
            val thisWeek = weekTotal(daily, today, 0)
            val lastWeek = weekTotal(daily, today, 1)
            Text(
                "Avg ${formatDuration(thisWeek / 7)}/day this week · ${formatDuration(lastWeek / 7)}/day last week",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Gap(12.dp)
            Heatmap(daily, today)
        }
        Gap(16.dp)

        // Top apps
        SectionCard {
            Text("Top apps, last 7 days", style = MaterialTheme.typography.titleLarge)
            if (top.isEmpty()) {
                Gap(4.dp)
                Text(
                    if (app.usage.hasPermission()) "No usage recorded yet." else "Grant usage access to see stats.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            top.forEach { (pkg, ms) ->
                Box(Modifier.clickable { selectedApp = pkg }) {
                    AppRow(pkg, app.apps.labelOf(pkg), "${formatDuration(ms / 7)}/day avg") {
                        Text(formatDuration(ms), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        Gap(16.dp)

        // Achievements
        SectionCard {
            Text(
                "Achievements · ${progress.unlocked.size}/${Achievement.entries.size}",
                style = MaterialTheme.typography.titleLarge,
            )
            Gap(8.dp)
            Achievement.entries.forEach { a ->
                val at = progress.unlocked[a.name]
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (at != null) Icons.Outlined.EmojiEvents else Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = if (at != null) Success else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            a.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (at != null) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            a.description + (at?.takeIf { it > 0 }?.let {
                                " · " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))
                            } ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Gap(32.dp)
    }

    selectedApp?.let { pkg -> AppHistoryDialog(pkg, today) { selectedApp = null } }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Monday of the week [weeksAgo] weeks before the week containing [today]. */
private fun weekStart(today: LocalDate, weeksAgo: Int): LocalDate =
    today.minusDays((today.dayOfWeek.value - 1).toLong()).minusWeeks(weeksAgo.toLong())

private fun weekTotal(daily: Map<LocalDate, Long>, today: LocalDate, weeksAgo: Int): Long {
    val start = weekStart(today, weeksAgo)
    return (0..6).sumOf { daily[start.plusDays(it.toLong())] ?: 0L }
}

/** Calendar heatmap: one row per week (oldest first), one column per weekday. */
@Composable
private fun Heatmap(daily: Map<LocalDate, Long>, today: LocalDate) {
    val max = daily.values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val empty = MaterialTheme.colorScheme.surfaceContainerHigh
    Row(Modifier.fillMaxWidth()) {
        DayOfWeek.entries.forEach { d ->
            Text(
                d.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Gap(4.dp)
    for (w in HEATMAP_WEEKS - 1 downTo 0) {
        val start = weekStart(today, w)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (i in 0..6) {
                val day = start.plusDays(i.toLong())
                val ms = daily[day]
                val color = when {
                    day.isAfter(today) -> Color.Transparent
                    ms == null || ms == 0L -> empty
                    else -> lerp(AccentSoft, Accent, (ms.toFloat() / max).coerceIn(0.1f, 1f))
                }
                Box(
                    Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(6.dp)).background(color),
                    contentAlignment = Alignment.Center,
                ) {
                    if (day == today) {
                        Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White))
                    }
                }
            }
        }
        Gap(4.dp)
    }
    Gap(4.dp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Less", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        listOf(0.1f, 0.4f, 0.7f, 1f).forEach { f ->
            Box(Modifier.padding(2.dp).size(12.dp).clip(RoundedCornerShape(3.dp)).background(lerp(AccentSoft, Accent, f)))
        }
        Spacer(Modifier.width(6.dp))
        Text("More", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(
            "Peak ${formatDuration(max)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AppHistoryDialog(pkg: String, today: LocalDate, onDismiss: () -> Unit) {
    val app = PlanrApp.instance
    val history by produceState(emptyList<Pair<LocalDate, Long>>(), pkg) {
        value = withContext(Dispatchers.IO) { app.stats.appHistory(pkg, 14, today) }
    }
    val total = history.sumOf { it.second }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(app.apps.labelOf(pkg)) },
        text = {
            Column {
                Text(
                    "${formatDuration(total)} in 14 days · ${formatDuration(total / 14)}/day avg",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Gap(16.dp)
                BarChart(history.map { it.second }, Modifier.fillMaxWidth().height(140.dp))
                Gap(4.dp)
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        history.firstOrNull()?.first?.let { "${it.dayOfMonth}/${it.monthValue}" } ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Today",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun BarChart(values: List<Long>, modifier: Modifier) {
    val max = values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val gap = 4.dp.toPx()
        val barW = (size.width - gap * (values.size - 1)) / values.size
        values.forEachIndexed { i, v ->
            val x = i * (barW + gap)
            drawRoundRect(track, Offset(x, 0f), Size(barW, size.height), CornerRadius(4.dp.toPx()))
            val h = size.height * (v.toFloat() / max)
            if (h > 0f) {
                drawRoundRect(
                    if (i == values.lastIndex) Success else Accent,
                    Offset(x, size.height - h),
                    Size(barW, h),
                    CornerRadius(4.dp.toPx()),
                )
            }
        }
    }
}
