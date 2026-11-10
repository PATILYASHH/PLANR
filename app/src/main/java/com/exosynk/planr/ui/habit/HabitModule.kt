package com.exosynk.planr.ui.habit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.exosynk.planr.ui.screens.FocusScreen
import com.exosynk.planr.ui.screens.HomeScreen
import com.exosynk.planr.ui.screens.OffGridScreen
import com.exosynk.planr.ui.screens.SchedulesScreen
import com.exosynk.planr.ui.screens.ShieldsScreen

val HABIT_TABS = listOf("Home", "Shields", "Focus", "Schedules", "Off-Grid")
const val HABIT_FOCUS = 2
const val HABIT_OFFGRID = 4

/**
 * The Habit module: everything PLANR did before the planner modules (digital
 * wellbeing, focus, schedules, Off-Grid), behind its own tab row.
 */
@Composable
fun HabitModule(
    modifier: Modifier,
    tab: Int,
    onTab: (Int) -> Unit,
    onOpenInsights: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = tab,
            edgePadding = 12.dp,
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            HABIT_TABS.forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { onTab(i) }, text = { Text(t) })
            }
        }
        val m = Modifier
        when (tab) {
            0 -> HomeScreen(
                m,
                onGoOffGrid = { onTab(HABIT_OFFGRID) },
                onOpenInsights = onOpenInsights,
                onOpenSettings = onOpenSettings,
                onOpenAbout = onOpenAbout,
            )
            1 -> ShieldsScreen(m)
            2 -> FocusScreen(m)
            3 -> SchedulesScreen(m)
            else -> OffGridScreen(m)
        }
    }
}
