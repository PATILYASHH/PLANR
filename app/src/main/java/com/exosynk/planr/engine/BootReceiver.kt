package com.exosynk.planr.engine

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.exosynk.planr.PlanrApp
import kotlinx.coroutines.launch

/**
 * After a reboot, an app update, or a clock/time-zone change: close Off-Grid sessions
 * that expired, and re-register every alarm, reminder and routine timer (AlarmManager
 * forgets them on reboot, and wall-clock times move with the time zone).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED) return
        OffGridManager.completeIfExpired()
        val pending = goAsync()
        PlanrApp.instance.appScope.launch {
            try {
                PlanrApp.rescheduleEverything(context)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}
