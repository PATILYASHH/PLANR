package com.exosynk.planr.engine

import com.exosynk.planr.PlanrApp
import com.exosynk.planr.data.PassStore
import com.exosynk.planr.data.db.Schedule
import com.exosynk.planr.data.db.ScheduleMode
import com.exosynk.planr.data.db.ShieldRule
import com.exosynk.planr.data.db.isActiveAt
import com.exosynk.planr.data.db.packageSet
import java.time.LocalDateTime

enum class InterceptMode {
    /** Shield pause before an app. */
    SHIELD,
    /** Daily time limit reached. */
    LIMIT,
    /** Daily open limit reached. */
    OPEN_LIMIT,
    /** Blocked by a schedule, focus block or Lockdown. */
    SCHEDULE,
    /** Mindful Gateway pause before an unshielded app. */
    GATEWAY,
}

sealed interface Decision {
    data object Allow : Decision
    data object OffGridLock : Decision
    data class Intercept(
        val packageName: String,
        val mode: InterceptMode,
        val rule: ShieldRule? = null,
        val scheduleName: String? = null,
    ) : Decision
}

/**
 * Pure decision logic: given the foreground package, what should PLANR do?
 * Rules are pushed in by the service from Room flows so lookups stay in memory.
 */
class Enforcer(private val app: PlanrApp) {

    @Volatile var shields: Map<String, ShieldRule> = emptyMap()
    @Volatile var schedules: List<Schedule> = emptyList()

    @Volatile private var safeCache: Set<String> = emptySet()
    @Volatile private var launcherCache: Set<String> = emptySet()
    @Volatile private var launchableCache: Set<String> = emptySet()
    @Volatile private var cameraCache: String? = null
    @Volatile private var cacheAt = 0L

    private fun refreshCaches() {
        val now = System.currentTimeMillis()
        if (now - cacheAt < 5 * 60_000L && safeCache.isNotEmpty()) return
        safeCache = app.apps.alwaysAllowed()
        launcherCache = app.apps.launcherPackages()
        launchableCache = app.apps.launchableApps().map { it.packageName }.toSet()
        cameraCache = app.apps.defaultCamera()
        cacheAt = now
    }

    fun invalidate() {
        cacheAt = 0L
    }

    fun decide(pkg: String): Decision {
        refreshCaches()

        // 1. Off-Grid beats everything. The launcher is NOT allowed, so pressing
        //    Home bounces straight back to the lock screen.
        val offGrid = app.offGrid.current()
        if (offGrid.active) {
            if (pkg in safeCache) return Decision.Allow
            if (offGrid.allowCamera && pkg == cameraCache) return Decision.Allow
            if (pkg in offGrid.extraAllowed) return Decision.Allow
            return Decision.OffGridLock
        }

        if (pkg in safeCache || pkg in launcherCache) return Decision.Allow

        val settings = app.settings.current
        val now = LocalDateTime.now()

        // 2. Lockdown can also close Android Settings, so rules (and the
        //    accessibility service itself) can't be switched off from there.
        //    Emergency passes deliberately don't apply here.
        if (settings.lockdownBlocksSettings && pkg in SETTINGS_PACKAGES && settings.isLockdown(now)) {
            return Decision.Intercept(pkg, InterceptMode.SCHEDULE, scheduleName = LOCKDOWN)
        }

        // 3. An emergency pass overrides everything below.
        if (PassStore.hasEmergencyPass(pkg)) return Decision.Allow

        // 4. Pomodoro focus block
        if (app.focus.blocks(pkg)) {
            return Decision.Intercept(pkg, InterceptMode.SCHEDULE, scheduleName = "Focus session")
        }

        // 5. Schedules
        for (s in schedules) {
            if (!s.isActiveAt(now)) continue
            val listed = pkg in s.packageSet()
            val blocked = when (s.mode) {
                ScheduleMode.BLOCK -> listed
                ScheduleMode.ALLOW -> !listed && pkg in launchableCache
                ScheduleMode.OFFGRID -> false // handled by OffGridScheduler
            }
            if (blocked) {
                return Decision.Intercept(pkg, InterceptMode.SCHEDULE, scheduleName = s.name)
            }
        }

        // 6. Shields
        val rule = shields[pkg]?.takeIf { it.enabled }
        if (rule != null) {
            if (rule.dailyLimitMinutes > 0 &&
                app.usage.todayMinutes(pkg) >= rule.dailyLimitMinutes
            ) {
                return Decision.Intercept(pkg, InterceptMode.LIMIT, rule)
            }
            if (PassStore.hasPass(pkg)) return Decision.Allow
            if (rule.dailyOpenLimit > 0 && app.counters.opensToday(pkg) >= rule.dailyOpenLimit) {
                return Decision.Intercept(pkg, InterceptMode.OPEN_LIMIT, rule)
            }
            return Decision.Intercept(pkg, InterceptMode.SHIELD, rule)
        }

        // 7. Mindful Gateway: a short pause before any other app you launch.
        if (settings.gatewayEnabled &&
            pkg in launchableCache &&
            pkg !in settings.gatewayWhitelist &&
            pkg !in SETTINGS_PACKAGES &&
            pkg != cameraCache &&
            shields[pkg] == null // a disabled shield means "leave this app alone"
        ) {
            if (PassStore.hasPass(pkg)) return Decision.Allow
            return Decision.Intercept(pkg, InterceptMode.GATEWAY)
        }
        return Decision.Allow
    }

    companion object {
        const val LOCKDOWN = "Lockdown"
        val SETTINGS_PACKAGES = setOf(
            "com.android.settings",
            "com.samsung.android.settings",
            "com.miui.securitycenter",
        )
    }
}
