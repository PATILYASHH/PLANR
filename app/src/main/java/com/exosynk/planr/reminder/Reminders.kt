package com.exosynk.planr.reminder

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.exosynk.planr.PlanrApp
import com.exosynk.planr.alarm.AlarmScheduler
import com.exosynk.planr.alarm.AlarmService
import com.exosynk.planr.data.Progression
import com.exosynk.planr.engine.Notifier
import com.exosynk.planr.ui.widget.PlanrWidget
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** All reminder writes go through here so AlarmManager always matches the database. */
object Reminders {

    const val SNOOZE_MINUTES = 10
    private val app get() = PlanrApp.instance

    suspend fun save(context: Context, reminder: Reminder): Long {
        val rowId = app.db.reminderDao().upsert(reminder.copy(snoozedUntil = 0))
        val id = if (reminder.id != 0L) reminder.id else rowId
        schedule(context, id)
        PlanrWidget.refresh(context)
        return id
    }

    /**
     * Completing a repeating reminder moves it to its next date instead (like Samsung
     * Reminder); a one-off one goes to Completed. Both earn XP.
     */
    suspend fun complete(context: Context, id: Long) {
        val r = app.db.reminderDao().get(id) ?: return
        if (r.completed) return
        val now = System.currentTimeMillis()
        val next = r.dueAt?.let {
            ReminderTime.nextOccurrence(ReminderTime.toLocal(it), r.repeatRule, LocalDateTime.now())
        }
        if (next != null) {
            app.db.reminderDao().upsert(r.copy(dueAt = ReminderTime.toMillis(next), snoozedUntil = 0))
        } else {
            app.db.reminderDao().upsert(r.copy(completed = true, completedAt = now, snoozedUntil = 0))
        }
        context.getSystemService(NotificationManager::class.java)?.cancel(Notifier.reminderId(id))
        app.progress.update { Progression.reminderCompleted(it) }
        schedule(context, id)
        PlanrWidget.refresh(context)
    }

    suspend fun reopen(context: Context, r: Reminder) {
        app.db.reminderDao().upsert(r.copy(completed = false, completedAt = null))
        schedule(context, r.id)
    }

    suspend fun snooze(context: Context, id: Long, minutes: Int = SNOOZE_MINUTES) {
        val r = app.db.reminderDao().get(id) ?: return
        app.db.reminderDao().upsert(r.copy(snoozedUntil = System.currentTimeMillis() + minutes * 60_000L))
        context.getSystemService(NotificationManager::class.java)?.cancel(Notifier.reminderId(id))
        schedule(context, id)
    }

    suspend fun delete(context: Context, id: Long) {
        cancel(context, id)
        app.db.reminderDao().delete(id)
        context.getSystemService(NotificationManager::class.java)?.cancel(Notifier.reminderId(id))
        PlanrWidget.refresh(context)
    }

    suspend fun rescheduleAll(context: Context) {
        app.db.reminderDao().getAll().forEach { scheduleOne(context, it) }
    }

    suspend fun schedule(context: Context, id: Long) {
        val r = app.db.reminderDao().get(id) ?: return cancel(context, id)
        scheduleOne(context, r)
    }

    private fun scheduleOne(context: Context, r: Reminder) {
        val next = ReminderTime.nextAlert(r, System.currentTimeMillis())
        if (next == null) {
            cancel(context, r.id)
            return
        }
        val (at, stage) = next
        val pi = pending(context, r.id, stage)
        // The due alert of an alarm-style reminder rings like an alarm clock.
        if (stage == ReminderTime.Stage.DUE && r.style == AlertStyle.ALARM) {
            AlarmScheduler.setClock(context, at, pi)
        } else {
            AlarmScheduler.setExact(context, at, pi)
        }
    }

    fun cancel(context: Context, id: Long) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pending(context, id, ReminderTime.Stage.DUE))
    }

    // Both stages share one PendingIntent identity, so scheduling one replaces the other.
    private fun pending(context: Context, id: Long, stage: ReminderTime.Stage): PendingIntent =
        PendingIntent.getBroadcast(
            context, 0,
            Intent(context, ReminderReceiver::class.java)
                .setAction(ReminderReceiver.ACTION_FIRE)
                .setData(Uri.parse("planr://reminder/$id"))
                .putExtra(ReminderReceiver.EXTRA_ID, id)
                .putExtra(ReminderReceiver.EXTRA_STAGE, stage.name),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** Called when an alert fires. */
    internal suspend fun fire(context: Context, id: Long, stage: ReminderTime.Stage) {
        val r = app.db.reminderDao().get(id) ?: return
        if (r.completed) return
        if (r.snoozedUntil != 0L) app.db.reminderDao().upsert(r.copy(snoozedUntil = 0))
        when {
            stage == ReminderTime.Stage.EARLY -> Notifier.reminder(context, r, early = true)
            r.style == AlertStyle.ALARM -> AlarmService.start(context, AlarmService.Kind.REMINDER, id)
            else -> Notifier.reminder(context, r, early = false)
        }
        // Schedule whatever comes next (the due alert after an early one).
        schedule(context, id)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1)
        if (id < 0) return
        val pending = goAsync()
        PlanrApp.instance.appScope.launch {
            try {
                when (intent.action) {
                    ACTION_FIRE -> {
                        val stage = runCatching {
                            ReminderTime.Stage.valueOf(intent.getStringExtra(EXTRA_STAGE) ?: "")
                        }.getOrDefault(ReminderTime.Stage.DUE)
                        Reminders.fire(context, id, stage)
                    }
                    ACTION_COMPLETE -> Reminders.complete(context, id)
                    ACTION_SNOOZE -> Reminders.snooze(context, id)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.exosynk.planr.reminder.FIRE"
        const val ACTION_COMPLETE = "com.exosynk.planr.reminder.COMPLETE"
        const val ACTION_SNOOZE = "com.exosynk.planr.reminder.SNOOZE"
        const val EXTRA_ID = "id"
        const val EXTRA_STAGE = "stage"

        fun action(context: Context, action: String, id: Long): PendingIntent =
            PendingIntent.getBroadcast(
                context, 0,
                Intent(context, ReminderReceiver::class.java)
                    .setAction(action)
                    .setData(Uri.parse("planr://reminder/$id/$action"))
                    .putExtra(EXTRA_ID, id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
