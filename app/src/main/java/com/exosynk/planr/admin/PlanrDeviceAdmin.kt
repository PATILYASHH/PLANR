package com.exosynk.planr.admin

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Optional uninstall protection. PLANR asks for no device policies at all; being
 * an active admin only means Android won't let it be uninstalled until the user
 * turns admin off in Settings, and Settings is blocked during Off-Grid.
 */
class PlanrDeviceAdmin : DeviceAdminReceiver() {

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "Turning this off removes PLANR's uninstall protection."

    companion object {
        fun component(context: Context) = ComponentName(context, PlanrDeviceAdmin::class.java)

        fun isActive(context: Context): Boolean =
            context.getSystemService(DevicePolicyManager::class.java)
                ?.isAdminActive(component(context)) == true

        fun requestIntent(context: Context): Intent =
            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(context))
                .putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Stops PLANR from being uninstalled on impulse. PLANR uses no admin " +
                        "powers: it can't lock, wipe or read anything on your phone.",
                )
    }
}
