package com.exosynk.planr

import android.app.Application
import androidx.core.content.edit
import com.exosynk.planr.data.AppsRepository
import com.exosynk.planr.data.DailyCounters
import com.exosynk.planr.data.FocusStore
import com.exosynk.planr.data.OffGridStore
import com.exosynk.planr.data.Progression
import com.exosynk.planr.data.ProgressStore
import com.exosynk.planr.data.SettingsStore
import com.exosynk.planr.data.StatsRepository
import com.exosynk.planr.data.db.PlanrDatabase
import com.exosynk.planr.engine.Notifier
import com.exosynk.planr.engine.UsageHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Application + tiny service locator (no DI framework needed at this size). */
class PlanrApp : Application() {

    lateinit var db: PlanrDatabase
        private set
    lateinit var offGrid: OffGridStore
        private set
    lateinit var apps: AppsRepository
        private set
    lateinit var usage: UsageHelper
        private set
    lateinit var focus: FocusStore
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var counters: DailyCounters
        private set
    lateinit var progress: ProgressStore
        private set
    lateinit var stats: StatsRepository
        private set

    /** Long-lived scope for writes that must outlive a screen. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        db = PlanrDatabase.build(this)
        offGrid = OffGridStore(this)
        apps = AppsRepository(this)
        usage = UsageHelper(this)
        focus = FocusStore(this)
        settings = SettingsStore(this)
        counters = DailyCounters(this)
        progress = ProgressStore(this)
        stats = StatsRepository(db, usage)

        Notifier.createChannels(this)
        progress.onUnlocked = { Notifier.achievements(this, it) }
        focus.onFocusBlockDone = { minutes -> progress.update { Progression.focusBlockDone(it, minutes) } }
        seedProgressFromHistory()
    }

    /** Upgrading from v0.2: give credit for Off-Grid sessions completed before XP existed. */
    private fun seedProgressFromHistory() {
        val prefs = getSharedPreferences("progress", MODE_PRIVATE)
        if (prefs.getBoolean("seeded", false)) return
        appScope.launch {
            val sessions = db.offGridDao().getAll().filter { it.endedAt != null }
            if (sessions.isNotEmpty()) {
                progress.update { start ->
                    sessions.fold(start) { s, session ->
                        Progression.offGridFinished(
                            s,
                            completed = !session.endedEarly,
                            minutes = ((session.plannedEndAt - session.startedAt) / 60_000L).toInt(),
                            scheduled = session.scheduleId != null,
                        )
                    }
                }
            }
            prefs.edit { putBoolean("seeded", true) }
        }
    }

    companion object {
        lateinit var instance: PlanrApp
            private set
    }
}
