package com.exosynk.planr.engine

import android.app.NotificationManager
import android.content.Context
import android.os.PowerManager
import androidx.core.content.edit
import com.exosynk.planr.data.PlanrSettings
import com.exosynk.planr.data.db.isActiveAt
import com.exosynk.planr.data.db.windowStartDate
import java.time.LocalDateTime

/**
 * Time-based routines that piggyback on the accessibility service's watchdog
 * tick, so PLANR needs no exact-alarm permission:
 *  - Wind Down: one reminder at bedtime, optional Do-Not-Disturb until morning.
 *  - Eye Care: a 20-20-20 reminder after 20 minutes of continuous screen time.
 */
object Routines {

    private const val PREFS = "routines"
    private const val K_WIND_DOWN_FIRED = "wdFired"
    private const val K_DND_BY_US = "dndByUs"

    const val EYE_CARE_INTERVAL_MS = 20 * 60_000L

    /** Gaps longer than this between ticks (Doze, screen off) count as a break. */
    private const val MAX_TICK_GAP_MS = 90_000L

    private var screenOnMs = 0L
    private var lastTickAt = 0L

    fun tick(context: Context, settings: PlanrSettings, now: LocalDateTime = LocalDateTime.now()) {
        windDown(context, settings, now)
        eyeCare(context, settings)
    }

    private fun windDown(context: Context, s: PlanrSettings, now: LocalDateTime) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val window = s.windDownWindow
        val inWindow = s.windDownEnabled && s.windDownStart != s.windDownEnd && window.isActiveAt(now)
        val nm = context.getSystemService(NotificationManager::class.java) ?: return

        if (inWindow) {
            val key = window.windowStartDate(now).toString()
            if (prefs.getString(K_WIND_DOWN_FIRED, null) == key) return
            prefs.edit(commit = true) { putString(K_WIND_DOWN_FIRED, key) }

            var dndOn = false
            if (s.windDownDnd && nm.isNotificationPolicyAccessGranted &&
                nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL
            ) {
                runCatching { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY) }
                    .onSuccess {
                        dndOn = true
                        prefs.edit(commit = true) { putBoolean(K_DND_BY_US, true) }
                    }
            }
            Notifier.windDown(context, dndOn)
        } else if (prefs.getBoolean(K_DND_BY_US, false)) {
            // Morning: undo only the DND we turned on, and only if the user hasn't changed it.
            if (nm.isNotificationPolicyAccessGranted &&
                nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY
            ) {
                runCatching { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL) }
            }
            prefs.edit(commit = true) { putBoolean(K_DND_BY_US, false) }
        }
    }

    private fun eyeCare(context: Context, s: PlanrSettings) {
        val nowMs = System.currentTimeMillis()
        val gap = nowMs - lastTickAt
        lastTickAt = nowMs
        val interactive = context.getSystemService(PowerManager::class.java)?.isInteractive == true
        if (!s.eyeCareEnabled || !interactive || gap > MAX_TICK_GAP_MS) {
            screenOnMs = 0L
            return
        }
        screenOnMs += gap
        if (screenOnMs >= EYE_CARE_INTERVAL_MS) {
            screenOnMs = 0L
            Notifier.eyeCare(context)
        }
    }
}
