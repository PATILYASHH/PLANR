package com.exosynk.planr

import android.app.Application
import com.exosynk.planr.data.AppsRepository
import com.exosynk.planr.data.FocusStore
import com.exosynk.planr.data.OffGridStore
import com.exosynk.planr.data.db.PlanrDatabase
import com.exosynk.planr.engine.UsageHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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
    }

    companion object {
        lateinit var instance: PlanrApp
            private set
    }
}
