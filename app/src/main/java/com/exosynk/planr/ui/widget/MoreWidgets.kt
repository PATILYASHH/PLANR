package com.exosynk.planr.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.exosynk.planr.PlanrApp
import com.exosynk.planr.R
import com.exosynk.planr.alarm.Alarms
import com.exosynk.planr.reminder.ReminderTime
import com.exosynk.planr.smart.ShareActivity
import com.exosynk.planr.ui.MainActivity
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private fun openApp(context: Context, open: String?, code: Int): PendingIntent =
    PendingIntent.getActivity(
        context, code,
        Intent(context, MainActivity::class.java)
            .apply { if (open != null) putExtra(MainActivity.EXTRA_OPEN, open) }
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

private fun ids(context: Context, cls: Class<*>): IntArray? =
    runCatching { AppWidgetManager.getInstance(context)?.getAppWidgetIds(ComponentName(context, cls)) }.getOrNull()
        ?.takeIf { it.isNotEmpty() }

/** Today at a glance: date, next alarm and up to four reminders due today or overdue. */
class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)

    companion object {
        private val ROWS = intArrayOf(R.id.today_row1, R.id.today_row2, R.id.today_row3, R.id.today_row4)

        fun refresh(context: Context) {
            val ids = ids(context, TodayWidget::class.java) ?: return
            val app = PlanrApp.instance
            app.appScope.launch {
                val today = LocalDate.now()
                val views = RemoteViews(context.packageName, R.layout.widget_today)
                views.setTextViewText(R.id.today_date, today.format(DateTimeFormatter.ofPattern("EEEE, d MMM")))
                val next = Alarms.nextOf(app.db.alarmDao().getAll(), app.db.alarmGroupDao().getAll())
                views.setTextViewText(
                    R.id.today_alarm,
                    next?.let { (a, at) ->
                        "⏰ " + at.toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)) +
                            (if (at.toLocalDate() != today) " tomorrow" else "") +
                            (if (a.label.isNotBlank()) " · ${a.label}" else "")
                    } ?: "⏰ No alarms set",
                )
                val due = app.db.reminderDao().getAll()
                    .filter { !it.completed && it.dueAt != null && !ReminderTime.toLocal(it.dueAt).toLocalDate().isAfter(today) }
                    .sortedBy { it.dueAt }
                ROWS.forEachIndexed { i, id ->
                    val r = due.getOrNull(i)
                    if (r == null) {
                        views.setViewVisibility(id, View.GONE)
                    } else {
                        val t = ReminderTime.toLocal(r.dueAt!!)
                        val whenText = when {
                            t.toLocalDate().isBefore(today) -> "Overdue"
                            r.allDay -> "Today"
                            else -> t.toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
                        }
                        views.setViewVisibility(id, View.VISIBLE)
                        views.setTextViewText(id, "○  ${r.title}  ·  $whenText")
                    }
                }
                views.setViewVisibility(R.id.today_empty, if (due.isEmpty()) View.VISIBLE else View.GONE)
                views.setTextViewText(R.id.today_more, if (due.size > ROWS.size) "+${due.size - ROWS.size} more" else "")
                views.setOnClickPendingIntent(R.id.today_root, openApp(context, MainActivity.OPEN_TODAY, 40))
                views.setOnClickPendingIntent(R.id.today_add, PendingIntent.getActivity(
                    context, 41, Intent(context, ShareActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ))
                runCatching { AppWidgetManager.getInstance(context).updateAppWidget(ids, views) }
            }
        }
    }
}

/** One-tap buttons: Reminder (smart sheet), Alarm, Note, Focus. */
class QuickAddWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)

    companion object {
        fun refresh(context: Context) {
            val ids = ids(context, QuickAddWidget::class.java) ?: return
            val views = RemoteViews(context.packageName, R.layout.widget_quick_add)
            views.setOnClickPendingIntent(R.id.qa_reminder, PendingIntent.getActivity(
                context, 50, Intent(context, ShareActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ))
            views.setOnClickPendingIntent(R.id.qa_alarm, openApp(context, MainActivity.OPEN_NEW_ALARM, 51))
            views.setOnClickPendingIntent(R.id.qa_note, openApp(context, MainActivity.OPEN_NEW_NOTE, 52))
            views.setOnClickPendingIntent(R.id.qa_focus, openApp(context, MainActivity.OPEN_FOCUS, 53))
            runCatching { AppWidgetManager.getInstance(context).updateAppWidget(ids, views) }
        }
    }
}
