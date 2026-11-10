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

    private fun post(
        context: Context,
        id: Int,
        channel: String,
        title: String,
        text: String,
        timeoutMs: Long = 0L,
    ) {
        if (!canPost(context)) return
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_planr)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .apply { if (timeoutMs > 0) setTimeoutAfter(timeoutMs) }
            .build()
        runCatching { context.getSystemService(NotificationManager::class.java)?.notify(id, n) }
    }
}
