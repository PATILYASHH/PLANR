package com.exosynk.planr.engine

import android.content.Context
import android.content.Intent
import com.exosynk.planr.PlanrApp
import com.exosynk.planr.data.OffGridState
import com.exosynk.planr.data.Progression
import com.exosynk.planr.routine.RoutineEngine
import com.exosynk.planr.routine.RoutineEvent
import com.exosynk.planr.data.UnlockChallenge
import com.exosynk.planr.data.db.OffGridSession
import com.exosynk.planr.ui.lock.LockActivity
import com.exosynk.planr.ui.widget.PlanrWidget
import kotlinx.coroutines.launch

/** Starts, ends and finalizes Off-Grid sessions. */
object OffGridManager {

    private val app get() = PlanrApp.instance

    suspend fun start(
        context: Context,
        durationMs: Long,
        extraAllowed: Set<String>,
        allowCamera: Boolean,
        challenge: UnlockChallenge,
        scheduleId: Long? = null,
    ) {
        val now = System.currentTimeMillis()
        val id = app.db.offGridDao().insert(
            OffGridSession(startedAt = now, plannedEndAt = now + durationMs, scheduleId = scheduleId)
        )
        app.offGrid.start(id, durationMs, extraAllowed, allowCamera, challenge)
        showLock(context)
        PlanrWidget.refresh(context)
        RoutineEngine.post(RoutineEvent.OffGridStarted)
    }

    /** User passed the unlock challenge. */
    fun endEarly() = finish(early = true)

    /**
     * If the stored session is past its end time, record it as completed and clear it.
     * Returns true when a session was finalized.
     */
    fun completeIfExpired(): Boolean {
        val stored = app.offGrid.state.value
        if (!stored.active || System.currentTimeMillis() < stored.endAt) return false
        finish(early = false)
        return true
    }

    private fun finish(early: Boolean) {
        val s = app.offGrid.state.value
        if (!s.active) return
        app.offGrid.clear()
        val endedAt = if (early) System.currentTimeMillis() else s.endAt
        app.appScope.launch {
            val dao = app.db.offGridDao()
            dao.finish(s.sessionId, endedAt, early)
            val scheduled = dao.recent().firstOrNull { it.id == s.sessionId }?.scheduleId != null
            app.progress.update {
                Progression.offGridFinished(
                    it,
                    completed = !early,
                    minutes = ((s.endAt - s.startAt) / 60_000L).toInt(),
                    scheduled = scheduled,
                )
            }
            PlanrWidget.refresh(app)
            RoutineEngine.post(RoutineEvent.OffGridEnded)
        }
    }

    /** Packages usable during Off-Grid: system essentials, dialer, camera, user extras. */
    fun allowedPackages(state: OffGridState): Set<String> = buildSet {
        addAll(app.apps.alwaysAllowed())
        if (state.allowCamera) app.apps.defaultCamera()?.let { add(it) }
        addAll(state.extraAllowed)
    }

    fun showLock(context: Context) {
        val intent = Intent(context, LockActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        )
        context.startActivity(intent)
    }
}
