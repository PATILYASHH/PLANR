package com.planr.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager

data class InstalledApp(val packageName: String, val label: String)

/**
 * Knows which apps are installed and which system packages must never be blocked.
 */
class AppsRepository(private val context: Context) {

    private val pm: PackageManager = context.packageManager

    @Volatile private var cachedLaunchable: List<InstalledApp>? = null
    @Volatile private var cachedAt = 0L

    /** Apps with a launcher icon, excluding PLANR itself. Cached for 10 minutes. */
    fun launchableApps(forceRefresh: Boolean = false): List<InstalledApp> {
        val now = System.currentTimeMillis()
        cachedLaunchable?.let { if (!forceRefresh && now - cachedAt < 10 * 60_000L) return it }
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != context.packageName }
            .map { InstalledApp(it, labelOf(it)) }
            .sortedBy { it.label.lowercase() }
        cachedLaunchable = apps
        cachedAt = now
        return apps
    }

    fun labelOf(packageName: String): String = runCatching {
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    fun defaultDialer(): String? =
        context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage

    fun defaultCamera(): String? =
        pm.resolveActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), 0)
            ?.activityInfo?.packageName
            ?.takeIf { it != "android" }

    fun launcherPackages(): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return pm.queryIntentActivities(intent, 0).map { it.activityInfo.packageName }.toSet()
    }

    private fun inputMethodPackages(): Set<String> =
        context.getSystemService(InputMethodManager::class.java)
            ?.inputMethodList?.map { it.packageName }?.toSet() ?: emptySet()

    /**
     * Packages that are never blocked by any rule: PLANR, the system UI (lock screen,
     * notification shade, recents), keyboards, permission dialogs and everything
     * needed to place or receive calls, including emergency calls.
     */
    fun alwaysAllowed(): Set<String> = buildSet {
        add(context.packageName)
        addAll(SYSTEM_SAFE)
        addAll(EMERGENCY)
        defaultDialer()?.let { add(it) }
        addAll(inputMethodPackages())
    }

    fun launchIntent(packageName: String): Intent? = pm.getLaunchIntentForPackage(packageName)

    companion object {
        val SYSTEM_SAFE = setOf(
            "android",
            "com.android.systemui",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.packageinstaller.permission", // rare OEM variant
        )
        val EMERGENCY = setOf(
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.emergency",
            "com.android.dialer",
            "com.android.incallui",
            "com.google.android.dialer",
            "com.samsung.android.dialer",
            "com.samsung.android.incallui",
            "com.android.contacts",
        )
    }
}
