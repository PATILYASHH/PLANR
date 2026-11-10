package com.exosynk.planr.data

import android.content.Context
import androidx.core.content.edit
import java.time.LocalDate

/**
 * Per-day counters the accessibility service reads synchronously: how often each
 * app was opened past its shield today, and how many emergency passes were used.
 * Everything resets when the date changes.
 */
class DailyCounters(context: Context) {

    private val prefs = context.getSharedPreferences("daily_counters", Context.MODE_PRIVATE)

    @Synchronized
    private fun rollover() {
        val today = LocalDate.now().toString()
        if (prefs.getString(K_DAY, null) != today) {
            prefs.edit(commit = true) {
                clear()
                putString(K_DAY, today)
            }
        }
    }

    fun opensToday(pkg: String): Int {
        rollover()
        return prefs.getInt(K_OPEN + pkg, 0)
    }

    @Synchronized
    fun recordOpen(pkg: String) {
        rollover()
        prefs.edit(commit = true) { putInt(K_OPEN + pkg, prefs.getInt(K_OPEN + pkg, 0) + 1) }
    }

    fun emergencyUsedToday(): Int {
        rollover()
        return prefs.getInt(K_EMERGENCY, 0)
    }

    /** Uses one emergency pass if any are left under [quota]. Returns true on success. */
    @Synchronized
    fun useEmergency(quota: Int): Boolean {
        rollover()
        val used = prefs.getInt(K_EMERGENCY, 0)
        if (used >= quota) return false
        prefs.edit(commit = true) { putInt(K_EMERGENCY, used + 1) }
        return true
    }

    private companion object {
        const val K_DAY = "day"
        const val K_OPEN = "open:"
        const val K_EMERGENCY = "emergency"
    }
}
