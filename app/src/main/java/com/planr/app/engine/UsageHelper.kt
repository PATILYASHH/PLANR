package com.planr.app.engine

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.time.LocalDate
import java.time.ZoneId

/**
 * Today's foreground time per app, computed from raw usage events
 * (more accurate than the aggregated daily buckets).
 */
class UsageHelper(private val context: Context) {

    private val usm = context.getSystemService(UsageStatsManager::class.java)

    @Volatile private var cache: Map<String, Long> = emptyMap()
    @Volatile private var cacheAt = 0L

    fun hasPermission(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Milliseconds in the foreground today, per package. Cached for [maxAgeMs]. */
    fun todayUsage(maxAgeMs: Long = 30_000L): Map<String, Long> {
        val now = System.currentTimeMillis()
        if (now - cacheAt < maxAgeMs) return cache
        if (!hasPermission()) return emptyMap()

        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val totals = HashMap<String, Long>()
        val resumedAt = HashMap<String, Long>()
        val events = usm.queryEvents(start, now)
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val pkg = e.packageName ?: continue
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> resumedAt[pkg] = e.timeStamp
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val began = resumedAt.remove(pkg) ?: continue
                    totals[pkg] = (totals[pkg] ?: 0L) + (e.timeStamp - began)
                }
            }
        }
        // Apps still in the foreground right now
        resumedAt.forEach { (pkg, began) -> totals[pkg] = (totals[pkg] ?: 0L) + (now - began) }
        totals.remove(context.packageName)

        cache = totals
        cacheAt = now
        return totals
    }

    fun todayMinutes(packageName: String): Int =
        ((todayUsage()[packageName] ?: 0L) / 60_000L).toInt()
}
