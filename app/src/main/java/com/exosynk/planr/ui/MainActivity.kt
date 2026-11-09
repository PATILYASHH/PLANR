package com.exosynk.planr.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.lifecycleScope
import com.exosynk.planr.PlanrApp
import com.exosynk.planr.engine.OffGridManager
import com.exosynk.planr.ui.screens.FocusScreen
import com.exosynk.planr.ui.screens.HomeScreen
import com.exosynk.planr.ui.screens.OffGridScreen
import com.exosynk.planr.ui.screens.SchedulesScreen
import com.exosynk.planr.ui.screens.ShieldsScreen
import com.exosynk.planr.ui.theme.PlanrTheme
import kotlinx.coroutines.launch

private data class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Home", Icons.Outlined.Home),
    Tab("Shields", Icons.Outlined.Shield),
    Tab("Focus", Icons.Outlined.Timer),
    Tab("Schedules", Icons.Outlined.Schedule),
    Tab("Off-Grid", Icons.Outlined.DarkMode),
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlanrTheme {
                var tab by rememberSaveable { mutableIntStateOf(0) }
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                            tabs.forEachIndexed { i, t ->
                                NavigationBarItem(
                                    selected = tab == i,
                                    onClick = { tab = i },
                                    icon = { Icon(t.icon, contentDescription = t.label) },
                                    label = { Text(t.label) },
                                )
                            }
                        }
                    },
                ) { padding ->
                    val m = Modifier.padding(padding)
                    when (tab) {
                        0 -> HomeScreen(m, onGoOffGrid = { tab = 4 })
                        1 -> ShieldsScreen(m)
                        2 -> FocusScreen(m)
                        3 -> SchedulesScreen(m)
                        else -> OffGridScreen(m)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Main UI must never be usable during Off-Grid.
        lifecycleScope.launch {
            OffGridManager.completeIfExpired()
            if (PlanrApp.instance.offGrid.isLocked()) OffGridManager.showLock(this@MainActivity)
        }
    }
}
