package com.exosynk.planr.engine

import android.content.Context
import androidx.core.content.edit
import com.exosynk.planr.PlanrApp
import com.exosynk.planr.data.UnlockChallenge
import com.exosynk.planr.data.db.Schedule
import com.exosynk.planr.data.db.ScheduleMode
import com.exosynk.planr.data.db.isActiveAt
import com.exosynk.planr.data.db.packageSet
import com.exosynk.planr.data.db.windowEnd
import com.exosynk.planr.data.db.windowStartDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Turns OFFGRID schedules (e.g. "Bedtime 11 PM – 7 AM") into real Off-Grid sessions.
 * Each window starts at most once, so an early unlock isn't undone on the next tick.
 */
object OffGridScheduler {

    private const val PREFS = "offgrid_scheduler"
    private const val K_LAST = "last_window"

    @Volatile private var starting = false

    suspend fun tick(context: Context, schedules: List<Schedule>) {
        val app = PlanrApp.instance
        OffGridManager.completeIfExpired()
        if (starting || app.offGrid.isLocked()) return

        val now = LocalDateTime.now()
        val schedule = schedules.firstOrNull { it.mode == ScheduleMode.OFFGRID && it.isActiveAt(now) }
            ?: return

        val key = "${schedule.id}:${schedule.windowStartDate(now)}"
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(K_LAST, null) == key) return

        val endAt = schedule.windowEnd(now).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val duration = endAt - System.currentTimeMillis()
        if (duration < 60_000L) return

        starting = true
        try {
            prefs.edit(commit = true) { putString(K_LAST, key) }
            OffGridManager.start(
                context = context,
                durationMs = duration,
                extraAllowed = schedule.packageSet(),
                allowCamera = true,
                challenge = runCatching { UnlockChallenge.valueOf(schedule.challenge) }
                    .getOrDefault(UnlockChallenge.TYPING),
                scheduleId = schedule.id,
            )
        } finally {
            starting = false
        }
    }
}
