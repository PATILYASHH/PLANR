package com.exosynk.planr.data

import com.exosynk.planr.data.db.DailyUsage
import com.exosynk.planr.data.db.PlanrDatabase
import com.exosynk.planr.engine.UsageHelper
import java.time.LocalDate

/**
 * Android only keeps raw usage events for about a week, so finished days are
 * copied into Room. Today is always computed live.
 */
class StatsRepository(
    private val db: PlanrDatabase,
    private val usage: UsageHelper,
) {

    /** Copies the last [SYNC_DAYS] finished days into Room. Call off the main thread. */
    suspend fun sync(today: LocalDate = LocalDate.now()) {
        if (!usage.hasPermission()) return
        val dao = db.dailyUsageDao()
        val stored = dao.storedDays().toSet()
        for (i in 1..SYNC_DAYS) {
            val day = today.minusDays(i.toLong())
            val key = day.toString()
            // Yesterday is re-read once in case it was stored before midnight finished.
            if (key in stored && i > 1) continue
            val totals = usage.usageOn(day).filterValues { it >= 1_000L }
            if (totals.isEmpty()) continue // events already expired; keep what we had
            dao.deleteDay(key)
            dao.upsertAll(totals.map { (pkg, ms) -> DailyUsage(key, pkg, ms) })
        }
    }

    /** Total screen time per day for the last [days] days, including today (live). */
    suspend fun dailyTotals(days: Int, today: LocalDate = LocalDate.now()): Map<LocalDate, Long> {
        val from = today.minusDays(days - 1L)
        val totals = db.dailyUsageDao().since(from.toString())
            .groupBy { LocalDate.parse(it.day) }
            .mapValues { (_, rows) -> rows.sumOf { it.ms } }
            .toMutableMap()
        totals[today] = usage.todayUsage().values.sum()
        return totals
    }

    /** Per-app totals over the last [days] days, including today, biggest first. */
    suspend fun topApps(days: Int, today: LocalDate = LocalDate.now()): List<Pair<String, Long>> {
        val from = today.minusDays(days - 1L)
        val sums = HashMap<String, Long>()
        db.dailyUsageDao().since(from.toString())
            .filter { it.day != today.toString() }
            .forEach { sums[it.packageName] = (sums[it.packageName] ?: 0L) + it.ms }
        usage.todayUsage().forEach { (pkg, ms) -> sums[pkg] = (sums[pkg] ?: 0L) + ms }
        return sums.entries.sortedByDescending { it.value }.map { it.key to it.value }
    }

    /** One app's daily time for the last [days] days, oldest first (missing days are 0). */
    suspend fun appHistory(pkg: String, days: Int, today: LocalDate = LocalDate.now()): List<Pair<LocalDate, Long>> {
        val from = today.minusDays(days - 1L)
        val byDay = db.dailyUsageDao().forApp(pkg, from.toString()).associate { LocalDate.parse(it.day) to it.ms }
        return (0 until days).map { i ->
            val d = from.plusDays(i.toLong())
            d to if (d == today) usage.todayUsage()[pkg] ?: 0L else byDay[d] ?: 0L
        }
    }

    companion object {
        const val SYNC_DAYS = 7
    }
}
