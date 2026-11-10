package com.exosynk.planr.engine

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.exosynk.planr.R
import com.exosynk.planr.data.Achievement
import com.exosynk.planr.ui.MainActivity

/** Local notifications only — PLANR has no internet permission. */
object Notifier {

    private const val CH_REMINDERS = "reminders"
    private const val CH_PROGRESS = "progress"
    const val CH_ALARMS = "alarms"
    private const val CH_TASKS = "tasks"
    private const val CH_ROUTINES = "routines"

    const val RINGING_ID = 900
    private const val ID_MISSED = 901
    private const val ID_TODAY = 902
    fun snoozedId(alarmId: Long) = 10_000 + (alarmId % 100_000).toInt()
    fun reminderId(id: Long) = 200_000 + (id % 100_000).toInt()
    private fun routineMsgId() = 300_000 + (System.currentTimeMillis() % 10_000).toInt()

    private const val ID_WIND_DOWN = 1001
    private const val ID_EYE_CARE = 1002
    private const val ID_ACHIEVEMENT_BASE = 2000

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_REMINDERS, "Reminders", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Wind Down and Eye Care reminders" },
                NotificationChannel(CH_PROGRESS, "Achievements", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "Levels and achievements" },
                // PLANR plays alarm sound and vibration itself, so the channel stays silent.
                NotificationChannel(CH_ALARMS, "Alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Ringing alarms and alarm-style reminders"
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                },
                NotificationChannel(CH_TASKS, "Reminders", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "Reminders you set in PLANR" },
                NotificationChannel(CH_ROUTINES, "Routines", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Messages and summaries from your routines" },
            )
        )
    }

    /** True if PLANR may post notifications (runtime permission on Android 13+, app setting below). */
    fun canPost(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun windDown(context: Context, dndOn: Boolean) = post(
        context, ID_WIND_DOWN, CH_REMINDERS,
        "Time to wind down",
        if (dndOn) "Do Not Disturb is on until morning. Put the phone away and get ready for bed."
        else "Put the phone away and get ready for bed.",
    )

    fun eyeCare(context: Context) = post(
        context, ID_EYE_CARE, CH_REMINDERS,
        "20-20-20 eye break",
        "You've been looking at your screen for 20 minutes. Look at something 20 feet away for 20 seconds.",
        timeoutMs = 60_000L,
    )

    fun achievements(context: Context, list: List<Achievement>) {
        list.forEach { a ->
            post(context, ID_ACHIEVEMENT_BASE + a.ordinal, CH_PROGRESS, "Achievement unlocked: ${a.title}", a.description)
        }
    }

    fun alarmSnoozed(context: Context, alarmId: Long, label: String, untilMs: Long) {
        val time = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(untilMs))
        post(
            context, snoozedId(alarmId), CH_ALARMS, "$label snoozed", "Rings again at $time",
            open = MainActivity.OPEN_ALARM,
            ongoing = true,
            actions = listOf(
                "Dismiss" to android.app.PendingIntent.getBroadcast(
                    context, 0,
                    Intent(context, com.exosynk.planr.alarm.AlarmReceiver::class.java)
                        .setAction(com.exosynk.planr.alarm.AlarmReceiver.ACTION_CANCEL_SNOOZE)
                        .setData(android.net.Uri.parse("planr://alarm/$alarmId/cancel-snooze"))
                        .putExtra(com.exosynk.planr.alarm.AlarmReceiver.EXTRA_ID, alarmId),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            ),
        )
    }

    fun missedAlarm(context: Context, label: String) =
        post(context, ID_MISSED, CH_ALARMS, "Missed alarm", label, open = MainActivity.OPEN_ALARM)

    fun reminder(context: Context, r: com.exosynk.planr.reminder.Reminder, early: Boolean) {
        val rr = com.exosynk.planr.reminder.ReminderReceiver
        val title = if (early) "Coming up: ${r.title}" else r.title
        val text = if (early) {
            "Due " + java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(r.dueAt ?: 0L))
        } else {
            r.notes.ifBlank { "Reminder" }
        }
        post(
            context, reminderId(r.id), CH_TASKS, title, text,
            open = MainActivity.OPEN_REMINDERS,
            actions = listOfNotNull(
                r.contact?.let { c ->
                    com.exosynk.planr.reminder.ContactActions.intent(context, c)?.let { intent ->
                        "${c.action.verb} ${c.name}" to PendingIntent.getActivity(
                            context, reminderId(r.id), intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                        )
                    }
                },
                "Complete" to rr.action(context, rr.ACTION_COMPLETE, r.id),
                "Snooze ${com.exosynk.planr.reminder.Reminders.SNOOZE_MINUTES} min" to rr.action(context, rr.ACTION_SNOOZE, r.id),
            ),
        )
    }

    fun routine(context: Context, text: String) =
        post(context, routineMsgId(), CH_ROUTINES, "PLANR routine", text, open = MainActivity.OPEN_ROUTINES)

    fun today(context: Context, text: String) =
        post(context, ID_TODAY, CH_ROUTINES, "Your day", text, open = MainActivity.OPEN_TODAY)

    /** Background apps can't open other apps, so a routine posts a tap-to-open notification instead. */
    fun openApp(context: Context, packageName: String, launch: Intent) {
        if (!canPost(context)) return
        val label = runCatching {
            context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName)
        val pi = PendingIntent.getActivity(
            context, packageName.hashCode(), launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CH_ROUTINES)
            .setSmallIcon(R.drawable.ic_stat_planr)
            .setContentTitle("Open $label")
            .setContentText("Tap to open, from your routine")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        runCatching { context.getSystemService(NotificationManager::class.java)?.notify(routineMsgId(), n) }
    }

    private fun post(
        context: Context,
        id: Int,
        channel: String,
        title: String,
        text: String,
        timeoutMs: Long = 0L,
        open: String? = null,
        ongoing: Boolean = false,
        actions: List<Pair<String, PendingIntent>> = emptyList(),
    ) {
        if (!canPost(context)) return
        val openPi = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java)
                .apply { if (open != null) putExtra(MainActivity.EXTRA_OPEN, open) }
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_planr)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openPi)
            .setAutoCancel(!ongoing)
            .setOngoing(ongoing)
            .apply {
                if (timeoutMs > 0) setTimeoutAfter(timeoutMs)
                actions.forEach { (label, pi) -> addAction(0, label, pi) }
            }
            .build()
        runCatching { context.getSystemService(NotificationManager::class.java)?.notify(id, n) }
    }
}
