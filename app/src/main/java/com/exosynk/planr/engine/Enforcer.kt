package com.exosynk.planr.engine

import com.exosynk.planr.PlanrApp
import com.exosynk.planr.data.PassStore
import com.exosynk.planr.data.db.Schedule
import com.exosynk.planr.data.db.ScheduleMode
import com.exosynk.planr.data.db.ShieldRule
import com.exosynk.planr.data.db.isActiveAt
import com.exosynk.planr.data.db.packageSet
import java.time.LocalDateTime

enum class InterceptMode { SHIELD, LIMIT, SCHEDULE }

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
    @Volatile private var cacheAt = 0L

    private fun refreshCaches() {
        val now = System.currentTimeMillis()
        if (now - cacheAt < 5 * 60_000L && safeCache.isNotEmpty()) return
        safeCache = app.apps.alwaysAllowed()
        launcherCache = app.apps.launcherPackages()
        launchableCache = app.apps.launchableApps().map { it.packageName }.toSet()
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
            if (offGrid.allowCamera && pkg == app.apps.defaultCamera()) return Decision.Allow
            if (pkg in offGrid.extraAllowed) return Decision.Allow
            return Decision.OffGridLock
        }

        if (pkg in safeCache || pkg in launcherCache) return Decision.Allow

        // 2. Pomodoro focus block
        if (app.focus.blocks(pkg)) {
            return Decision.Intercept(pkg, InterceptMode.SCHEDULE, scheduleName = "Focus session")
        }

        // 3. Schedules
        val now = LocalDateTime.now()
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

        // 4. Shields
        val rule = shields[pkg]?.takeIf { it.enabled } ?: return Decision.Allow
        if (rule.dailyLimitMinutes > 0 &&
            app.usage.todayMinutes(pkg) >= rule.dailyLimitMinutes
        ) {
            return Decision.Intercept(pkg, InterceptMode.LIMIT, rule)
        }
        if (PassStore.hasPass(pkg)) return Decision.Allow
        return Decision.Intercept(pkg, InterceptMode.SHIELD, rule)
    }
}
