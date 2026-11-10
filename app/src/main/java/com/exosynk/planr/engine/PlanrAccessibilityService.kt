package com.exosynk.planr.engine

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import com.exosynk.planr.PlanrApp
import com.exosynk.planr.ui.intercept.InterceptActivity
import com.exosynk.planr.ui.widget.PlanrWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Watches only window-state changes (which app is in front). It does not read
 * screen content or typed text — see accessibility_service_config.xml.
 */
class PlanrAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var enforcer: Enforcer

    private var lastActionPkg: String? = null
    private var lastActionAt = 0L
    private var lastTickAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        running = true
        val app = application as PlanrApp
        enforcer = Enforcer(app)

        scope.launch {
            app.db.shieldDao().observeAll().collect { list ->
                enforcer.shields = list.associateBy { it.packageName }
            }
        }
        scope.launch {
            app.db.scheduleDao().observeAll().collect { enforcer.schedules = it }
        }
        // Watchdog: starts scheduled Off-Grid windows and advances Pomodoro
        // phases even when no window changes are happening.
        scope.launch(Dispatchers.Main) {
            var ticks = 0L
            while (true) {
                val ctx = this@PlanrAccessibilityService
                OffGridScheduler.tick(ctx, enforcer.schedules)
                app.focus.advance()
                Routines.tick(ctx, app.settings.current)
                if (ticks % WIDGET_EVERY_TICKS == 0L) PlanrWidget.refresh(ctx)
                if (ticks % STATS_EVERY_TICKS == 0L) app.appScope.launch { runCatching { app.stats.sync() } }
                ticks++
                delay(TICK_MS)
            }
        }
        // If the phone rebooted (or the service restarted) mid Off-Grid, re-lock now.
        scope.launch(Dispatchers.Main) {
            app.offGrid.state.collect {
                enforcer.invalidate()
                if (!OffGridManager.completeIfExpired() && app.offGrid.isLocked()) {
                    OffGridManager.showLock(this@PlanrAccessibilityService)
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        if (!::enforcer.isInitialized) return

        OffGridManager.completeIfExpired()
        val now = System.currentTimeMillis()
        if (now - lastTickAt > 5_000L) {
            // Catch scheduled windows right after the screen wakes from Doze.
            lastTickAt = now
            scope.launch(Dispatchers.Main) {
                OffGridScheduler.tick(this@PlanrAccessibilityService, enforcer.schedules)
            }
        }

        when (val decision = enforcer.decide(pkg)) {
            Decision.Allow -> Unit
            Decision.OffGridLock -> {
                if (debounced(pkg, 300)) OffGridManager.showLock(this)
            }
            is Decision.Intercept -> {
                if (debounced(pkg, 1500)) openIntercept(decision)
            }
        }
    }

    /** Avoid firing the same action repeatedly for a burst of events from one app. */
    private fun debounced(pkg: String, windowMs: Long): Boolean {
        val now = System.currentTimeMillis()
        if (pkg == lastActionPkg && now - lastActionAt < windowMs) return false
        lastActionPkg = pkg
        lastActionAt = now
        return true
    }

    private fun openIntercept(d: Decision.Intercept) {
        val intent = Intent(this, InterceptActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(InterceptActivity.EXTRA_PACKAGE, d.packageName)
            putExtra(InterceptActivity.EXTRA_MODE, d.mode.name)
            putExtra(InterceptActivity.EXTRA_SCHEDULE, d.scheduleName)
        }
        startActivity(intent)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        running = false
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TICK_MS = 20_000L
        private const val WIDGET_EVERY_TICKS = 15L // ~5 min
        private const val STATS_EVERY_TICKS = 180L // ~1 h

        @Volatile var running = false
            private set

        /** True if the user has enabled PLANR in Accessibility settings. */
        fun isEnabled(context: Context): Boolean {
            val expected = ComponentName(context, PlanrAccessibilityService::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.split(':').any {
                ComponentName.unflattenFromString(it) == expected
            }
        }
    }
}
