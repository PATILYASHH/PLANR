package com.exosynk.planr.engine

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
        val totals = usageBetween(start, now)
        cache = totals
        cacheAt = now
        return totals
    }

    /** Foreground time for one calendar day (uncached; call off the main thread). */
    fun usageOn(day: LocalDate): Map<String, Long> {
        if (!hasPermission()) return emptyMap()
        val zone = ZoneId.systemDefault()
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = minOf(day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), System.currentTimeMillis())
        return usageBetween(start, end)
    }

    /**
     * Sums foreground time from raw RESUMED/PAUSED events in [start, end). An app
     * already in front at [start] is counted from [start]; one still in front at
     * [end] is counted up to [end].
     */
    private fun usageBetween(start: Long, end: Long): Map<String, Long> {
        val totals = HashMap<String, Long>()
        val resumedAt = HashMap<String, Long>()
        val events = runCatching { usm.queryEvents(start, end) }.getOrNull() ?: return emptyMap()
        val e = UsageEvents.Event()
        val seen = HashSet<String>()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val pkg = e.packageName ?: continue
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> resumedAt[pkg] = e.timeStamp
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    // A pause with no resume in range: the app was already open at [start].
                    val began = resumedAt.remove(pkg)
                        ?: if (pkg !in seen && e.eventType == UsageEvents.Event.ACTIVITY_PAUSED) start
                        else null
                    if (began != null) totals[pkg] = (totals[pkg] ?: 0L) + (e.timeStamp - began)
                }
            }
            seen += pkg
        }
        // Apps still in the foreground at the end of the range
        resumedAt.forEach { (pkg, began) -> totals[pkg] = (totals[pkg] ?: 0L) + (end - began) }
        totals.remove(context.packageName)
        return totals
    }

    fun todayMinutes(packageName: String): Int =
        ((todayUsage()[packageName] ?: 0L) / 60_000L).toInt()
}
