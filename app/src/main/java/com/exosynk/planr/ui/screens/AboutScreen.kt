package com.exosynk.planr.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.exosynk.planr.ui.components.AppIcon
import com.exosynk.planr.ui.components.Gap
import com.exosynk.planr.ui.components.SectionCard
import com.exosynk.planr.ui.theme.Accent
import com.exosynk.planr.ui.theme.AccentSoft

/** Links shown in About. PLANR has no internet permission; these open in your browser. */
object ExoSynkLinks {
    const val LABS = "https://exosynk.com/exosynk-labs"
    const val SOURCE = "https://github.com/PATILYASHH/PLANR"
    const val ISSUES = "https://github.com/PATILYASHH/PLANR/issues"
    const val LICENSE = "https://github.com/PATILYASHH/PLANR/blob/main/LICENSE"
}

/** Other ExoSynk Labs projects. Each opens the ExoSynk Labs page. */
private val MORE_FROM_US = listOf("FITNS", "XPENC", "CONECTR")

private data class Credit(val name: String, val by: String)

private val CREDITS = listOf(
    Credit("Kotlin & kotlinx.coroutines", "JetBrains and contributors"),
    Credit("Jetpack Compose & Material 3", "The Android Open Source Project"),
    Credit("Material Icons", "Google"),
    Credit("AndroidX Core, Activity, Lifecycle", "The Android Open Source Project"),
    Credit("Room", "The Android Open Source Project"),
)

@Composable
fun AboutScreen(modifier: Modifier, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }
    fun open(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { Toast.makeText(context, "No browser found to open $url", Toast.LENGTH_LONG).show() }
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Gap(12.dp)
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }

        // Hero
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(Brush.verticalGradient(listOf(AccentSoft, MaterialTheme.colorScheme.surfaceContainer)))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppIcon(context.packageName, 72.dp)
            Gap(12.dp)
            Text("PLANR", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Version $version", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Gap(4.dp)
            Text("Plan your day. Guard your attention.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Gap(12.dp)
            Text("An ExoSynk Labs project", style = MaterialTheme.typography.titleMedium, color = Accent)
            Gap(4.dp)
            Text(
                "Official release: 5 December 2026 · Google Play & F-Droid",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Gap(16.dp)

        SectionCard {
            Text("Free & open source", style = MaterialTheme.typography.titleLarge)
            Text(
                "PLANR is open source under the Apache License 2.0. Read the code, build it yourself, report bugs or contribute.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Gap(8.dp)
            LinkRow(Icons.Outlined.Code, "Source code", "github.com/PATILYASHH/PLANR") { open(ExoSynkLinks.SOURCE) }
            LinkRow(Icons.Outlined.BugReport, "Report a bug or idea", "GitHub Issues") { open(ExoSynkLinks.ISSUES) }
            LinkRow(Icons.Outlined.Gavel, "License", "Apache License 2.0") { open(ExoSynkLinks.LICENSE) }
        }
        Gap(16.dp)

        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Lock, null, tint = Accent)
                Spacer(Modifier.width(10.dp))
                Text("Private by design", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                "No internet permission, no accounts, no ads, no tracking. Your alarms, reminders, notes, routines and " +
                    "habits never leave your phone. Links on this page open in your browser.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Gap(16.dp)

        SectionCard {
            Text("Credits", style = MaterialTheme.typography.titleLarge)
            Text("Designed and built by ExoSynk Labs.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Gap(8.dp)
            Text("Made with these open-source projects (Apache License 2.0):", style = MaterialTheme.typography.labelLarge)
            CREDITS.forEach { c ->
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text(c.name, style = MaterialTheme.typography.titleMedium)
                    Text(c.by, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Gap(16.dp)

        SectionCard {
            Text("More from us", style = MaterialTheme.typography.titleLarge)
            Text(
                "Other open-source ExoSynk Labs projects.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Gap(8.dp)
            MORE_FROM_US.forEach { name ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { open(ExoSynkLinks.LABS) }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(AccentSoft), contentAlignment = Alignment.Center) {
                        Text(name.take(1), color = Accent, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Gap(8.dp)
            Button(onClick = { open(ExoSynkLinks.LABS) }, modifier = Modifier.fillMaxWidth()) {
                Text("All projects at exosynk.com/exosynk-labs")
            }
        }
        Gap(24.dp)
        Text(
            "© 2026 ExoSynk Labs · Apache License 2.0",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Gap(32.dp)
    }
}

@Composable
private fun LinkRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(icon, null, tint = Accent)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
