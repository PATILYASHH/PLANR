package com.exosynk.planr.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * After a reboot, Android reconnects the accessibility service by itself and the
 * service re-shows the lock. Here we only close sessions that expired while the
 * phone was off. Only the two system actions are handled.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
        OffGridManager.completeIfExpired()
    }
}
