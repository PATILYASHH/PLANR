package com.exosynk.planr.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import com.exosynk.planr.PlanrApp
import com.exosynk.planr.ui.MainActivity
import com.exosynk.planr.ui.widget.PlanrWidget
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDateTime

/**
 * Registers every alarm with AlarmManager.setAlarmClock (exact, Doze-proof, and shown
 * in the status bar like any clock app). One PendingIntent per alarm, plus one per snooze.
 */
object AlarmScheduler {

    private const val PREFS = "alarm_runtime"

    // Serializes rescheduling so a stale read (e.g. at process start while an alarm is
    // ringing and switching itself off) can never win over a newer one.
    private val mutex = Mutex()
    private fun snoozeCountKey(id: Long) = "snoozes:$id"

    /** Recomputes and (re)registers all alarms. Call after any alarm or group change. */
    suspend fun rescheduleAll(context: Context) = mutex.withLock {
        val app = PlanrApp.instance
        val groups = app.db.alarmGroupDao().getAll().associateBy { it.id }
        val now = LocalDateTime.now()
        app.db.alarmDao().getAll().forEach { a ->
            val next = if (isArmed(a, groups)) AlarmTime.next(a, now) else null
            if (next == null) {
                cancelRegular(context, a.id)
            } else {
                setClock(context, AlarmTime.toMillis(next), firePendingIntent(context, a.id, snooze = false))
            }
        }
        PlanrWidget.refresh(context)
    }

    /** Alarm is on and, if it belongs to a group, the group is on too. */
    fun isArmed(a: Alarm, groups: Map<Long, AlarmGroup>): Boolean =
        a.enabled && (a.groupId == null || groups[a.groupId]?.enabled != false)

    fun scheduleSnooze(context: Context, id: Long, atMillis: Long) {
        setClock(context, atMillis, firePendingIntent(context, id, snooze = true))
    }

    fun cancelSnooze(context: Context, id: Long) {
        context.getSystemService(AlarmManager::class.java)?.cancel(firePendingIntent(context, id, snooze = true))
    }

    fun cancelAll(context: Context, id: Long) {
        cancelRegular(context, id)
        cancelSnooze(context, id)
        resetSnoozes(context, id)
    }

    private fun cancelRegular(context: Context, id: Long) {
        context.getSystemService(AlarmManager::class.java)?.cancel(firePendingIntent(context, id, snooze = false))
    }

    // Snooze bookkeeping survives process death so "snooze 3 times" holds across restarts.
    fun snoozesUsed(context: Context, id: Long): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(snoozeCountKey(id), 0)

    fun addSnooze(context: Context, id: Long) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.edit(commit = true) { putInt(snoozeCountKey(id), p.getInt(snoozeCountKey(id), 0) + 1) }
    }

    fun resetSnoozes(context: Context, id: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit(commit = true) { remove(snoozeCountKey(id)) }
    }

    private fun firePendingIntent(context: Context, id: Long, snooze: Boolean): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_FIRE)
            .setData(Uri.parse("planr://alarm/$id" + if (snooze) "/snooze" else ""))
            .putExtra(AlarmReceiver.EXTRA_ID, id)
            .putExtra(AlarmReceiver.EXTRA_SNOOZE, snooze)
        return PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** Exact "alarm clock" alarm; falls back to an inexact alarm if exact alarms are denied. */
    internal fun setClock(context: Context, atMillis: Long, operation: PendingIntent) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val show = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_ALARM)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        runCatching {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(atMillis, show), operation)
        }.onFailure {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, operation)
        }
    }

    /** Exact wake-up for non-alarm work (reminders, routines). */
    internal fun setExact(context: Context, atMillis: Long, operation: PendingIntent) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, operation)
        }.onFailure {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, operation)
        }
    }

    /** Android 11 needs no permission for exact alarms; 12+ may revoke it. */
    fun canScheduleExact(context: Context): Boolean =
        android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() != false
}

/** All alarm writes go through here so AlarmManager always matches the database. */
object Alarms {
    private val app get() = PlanrApp.instance

    suspend fun save(context: Context, alarm: Alarm): Long {
        val rowId = app.db.alarmDao().upsert(alarm)
        val id = if (alarm.id != 0L) alarm.id else rowId
        AlarmScheduler.cancelSnooze(context, id)
        AlarmScheduler.resetSnoozes(context, id)
        AlarmScheduler.rescheduleAll(context)
        return id
    }

    suspend fun setEnabled(context: Context, alarm: Alarm, enabled: Boolean) {
        // Turning a one-time alarm back on clears any old date in the past.
        val fixed = if (enabled && !alarm.isRepeating && alarm.date != null &&
            AlarmTime.next(alarm, LocalDateTime.now()) == null
        ) alarm.copy(date = null) else alarm
        app.db.alarmDao().upsert(fixed.copy(enabled = enabled, skippedAt = if (enabled) fixed.skippedAt else 0))
        if (!enabled) AlarmScheduler.cancelAll(context, alarm.id)
        AlarmScheduler.rescheduleAll(context)
    }

    /** Skip only the next ring of a repeating alarm (one-time alarms are simply turned off). */
    suspend fun skipNext(context: Context, alarm: Alarm) {
        if (!alarm.isRepeating) return setEnabled(context, alarm, false)
        val next = AlarmTime.next(alarm.copy(skippedAt = 0), LocalDateTime.now()) ?: return
        app.db.alarmDao().upsert(alarm.copy(skippedAt = AlarmTime.toMillis(next)))
        AlarmScheduler.rescheduleAll(context)
    }

    suspend fun undoSkip(context: Context, alarm: Alarm) {
        app.db.alarmDao().upsert(alarm.copy(skippedAt = 0))
        AlarmScheduler.rescheduleAll(context)
    }

    suspend fun delete(context: Context, id: Long) {
        AlarmScheduler.cancelAll(context, id)
        app.db.alarmDao().delete(id)
        AlarmScheduler.rescheduleAll(context)
    }

    suspend fun saveGroup(context: Context, group: AlarmGroup): Long {
        val rowId = app.db.alarmGroupDao().upsert(group)
        AlarmScheduler.rescheduleAll(context)
        return if (group.id != 0L) group.id else rowId
    }

    suspend fun setGroupEnabled(context: Context, groupId: Long, enabled: Boolean) {
        app.db.alarmGroupDao().setEnabled(groupId, enabled)
        AlarmScheduler.rescheduleAll(context)
    }

    /** Deletes the group; its alarms stay, ungrouped. */
    suspend fun deleteGroup(context: Context, groupId: Long) {
        app.db.alarmDao().ungroup(groupId)
        app.db.alarmGroupDao().delete(groupId)
        AlarmScheduler.rescheduleAll(context)
    }

    /** The next alarm that will ring, across all armed alarms. */
    fun nextOf(alarms: List<Alarm>, groups: List<AlarmGroup>, now: LocalDateTime = LocalDateTime.now()): Pair<Alarm, LocalDateTime>? {
        val byId = groups.associateBy { it.id }
        return alarms.filter { AlarmScheduler.isArmed(it, byId) }
            .mapNotNull { a -> AlarmTime.next(a, now)?.let { a to it } }
            .minByOrNull { it.second }
    }
}
