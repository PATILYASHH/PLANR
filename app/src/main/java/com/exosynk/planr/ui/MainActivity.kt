package com.exosynk.planr.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.lifecycleScope
import com.exosynk.planr.PlanrApp
import com.exosynk.planr.alarm.AlarmEditor
import com.exosynk.planr.alarm.AlarmScreen
import com.exosynk.planr.engine.OffGridManager
import com.exosynk.planr.notes.NoteEditor
import com.exosynk.planr.notes.NotesScreen
import com.exosynk.planr.reminder.ReminderEditor
import com.exosynk.planr.reminder.RemindersScreen
import com.exosynk.planr.routine.RoutineEditor
import com.exosynk.planr.routine.RoutinesScreen
import com.exosynk.planr.ui.habit.HABIT_FOCUS
import com.exosynk.planr.ui.habit.HABIT_OFFGRID
import com.exosynk.planr.ui.habit.HabitModule
import com.exosynk.planr.today.TodayNav
import com.exosynk.planr.today.TodayScreen
import com.exosynk.planr.ui.screens.InsightsScreen
import com.exosynk.planr.ui.screens.SettingsScreen
import com.exosynk.planr.ui.theme.PlanrTheme
import com.exosynk.planr.ui.widget.PlanrWidget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

const val MODULE_TODAY = 0
const val MODULE_HABIT = 1
const val MODULE_ALARM = 2
const val MODULE_REMINDERS = 3
const val MODULE_NOTES = 4

private data class Module(val label: String, val icon: ImageVector)

private val modules = listOf(
    Module("Today", Icons.Outlined.CalendarMonth),
    Module("Habit", Icons.Outlined.SelfImprovement),
    Module("Alarm", Icons.Outlined.Alarm),
    Module("Reminders", Icons.Outlined.TaskAlt),
    Module("Notes", Icons.AutoMirrored.Outlined.StickyNote2),
)

/**
 * One app, five modules. Editors and Settings/Insights are full-screen "pages" on
 * top of the module tabs, addressed by a short string (e.g. "alarm:12", "reminder:0:5").
 */
class MainActivity : ComponentActivity() {

    private val openRequest = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) openRequest.value = intent?.getStringExtra(EXTRA_OPEN)
        setContent {
            PlanrTheme {
                var module by rememberSaveable { mutableIntStateOf(MODULE_TODAY) }
                var habitTab by rememberSaveable { mutableIntStateOf(0) }
                var page by rememberSaveable { mutableStateOf<String?>(null) }

                val request by openRequest.collectAsState()
                LaunchedEffect(request) {
                    when (request) {
                        null -> return@LaunchedEffect
                        OPEN_ALARM -> module = MODULE_ALARM
                        OPEN_REMINDERS -> module = MODULE_REMINDERS
                        OPEN_NOTES -> module = MODULE_NOTES
                        OPEN_HABIT -> module = MODULE_HABIT
                        OPEN_TODAY -> module = MODULE_TODAY
                    }
                    page = if (request == OPEN_ROUTINES) PAGE_ROUTINES else null
                    openRequest.value = null
                }

                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        if (page == null) {
                            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                                modules.forEachIndexed { i, t ->
                                    NavigationBarItem(
                                        selected = module == i,
                                        onClick = { module = i },
                                        icon = { Icon(t.icon, contentDescription = t.label) },
                                        label = { Text(t.label, maxLines = 1) },
                                    )
                                }
                            }
                        }
                    },
                ) { padding ->
                    val m = Modifier.padding(padding)
                    val p = page
                    val back = { page = null }
                    val parts = p?.split(':').orEmpty()
                    val id = parts.getOrNull(1)?.toLongOrNull() ?: 0L
                    when (parts.firstOrNull()) {
                        PAGE_SETTINGS -> SettingsScreen(m, onBack = back)
                        PAGE_INSIGHTS -> InsightsScreen(m, onBack = back)
                        "alarm" -> AlarmEditor(m, id, onDone = back)
                        "reminder" -> ReminderEditor(
                            m, id,
                            fromNoteId = parts.getOrNull(2)?.toLongOrNull(),
                            onOpenNote = { page = "note:$it" },
                            onDone = back,
                        )
                        "note" -> NoteEditor(
                            m, id,
                            onRemind = { noteId -> page = "reminder:0:$noteId" },
                            onOpenReminder = { page = "reminder:$it" },
                            onDone = back,
                        )
                        "routine" -> RoutineEditor(m, id, onDone = { page = PAGE_ROUTINES })
                        PAGE_ROUTINES -> RoutinesScreen(m, onEdit = { page = "routine:$it" }, onBack = back)
                        else -> when (module) {
                            MODULE_TODAY -> TodayScreen(
                                m,
                                TodayNav(
                                    newAlarm = { page = "alarm:0" },
                                    newReminder = { page = "reminder:0" },
                                    newNote = { page = "note:0" },
                                    openAlarm = { page = "alarm:$it" },
                                    openReminder = { page = "reminder:$it" },
                                    openNote = { page = "note:$it" },
                                    focus = { module = MODULE_HABIT; habitTab = HABIT_FOCUS },
                                    offGrid = { module = MODULE_HABIT; habitTab = HABIT_OFFGRID },
                                    routines = { page = PAGE_ROUTINES },
                                    habit = { module = MODULE_HABIT; habitTab = 0 },
                                    settings = { page = PAGE_SETTINGS },
                                ),
                            )
                            MODULE_HABIT -> HabitModule(
                                m,
                                tab = habitTab,
                                onTab = { habitTab = it },
                                onOpenInsights = { page = PAGE_INSIGHTS },
                                onOpenSettings = { page = PAGE_SETTINGS },
                            )
                            MODULE_ALARM -> AlarmScreen(m, onEdit = { page = "alarm:$it" })
                            MODULE_REMINDERS -> RemindersScreen(m, onEdit = { page = "reminder:$it" })
                            else -> NotesScreen(m, onOpen = { page = "note:$it" })
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_OPEN)?.let { openRequest.value = it }
    }

    override fun onResume() {
        super.onResume()
        // Main UI must never be usable during Off-Grid.
        lifecycleScope.launch {
            OffGridManager.completeIfExpired()
            if (PlanrApp.instance.offGrid.isLocked()) OffGridManager.showLock(this@MainActivity)
        }
        PlanrWidget.refresh(this)
    }

    companion object {
        const val EXTRA_OPEN = "open"
        const val OPEN_HABIT = "habit"
        const val OPEN_TODAY = "today"
        const val OPEN_ALARM = "alarm"
        const val OPEN_REMINDERS = "reminders"
        const val OPEN_NOTES = "notes"
        const val OPEN_ROUTINES = "routines"

        private const val PAGE_SETTINGS = "settings"
        private const val PAGE_INSIGHTS = "insights"
        private const val PAGE_ROUTINES = "routines"
    }
}
