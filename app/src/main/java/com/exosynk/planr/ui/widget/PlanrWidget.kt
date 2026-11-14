package com.exosynk.planr.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.exosynk.planr.PlanrApp
import com.exosynk.planr.R
import com.exosynk.planr.alarm.Alarms
import com.exosynk.planr.reminder.ReminderTime
import com.exosynk.planr.ui.MainActivity
import com.exosynk.planr.ui.components.formatDuration
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date

/**
 * Home-screen widget: today's screen time (or the Off-Grid end time), level,
 * streak and focus minutes. Refreshed by the service watchdog and on key events.
 */
class PlanrWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        refresh(context)
    }

    companion object {
        fun refresh(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext) ?: return
            // The Today and Quick add widgets update alongside this one.
            TodayWidget.refresh(appContext)
            QuickAddWidget.refresh(appContext)
            val ids = runCatching {
                manager.getAppWidgetIds(ComponentName(appContext, PlanrWidget::class.java))
            }.getOrNull()
            if (ids == null || ids.isEmpty()) return
            val app = PlanrApp.instance
            app.appScope.launch {
                val views = build(appContext, app)
                runCatching { manager.updateAppWidget(ids, views) }
            }
        }

        private suspend fun build(context: Context, app: PlanrApp): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_planr)
            val offGrid = app.offGrid.current()
            val progress = app.progress.state.value.stats
            val focus = app.focus.state.value

            if (offGrid.active) {
                views.setTextViewText(R.id.widget_label, "Off-Grid until")
                views.setTextViewText(
                    R.id.widget_value,
                    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(offGrid.endAt)),
                )
            } else {
                views.setTextViewText(R.id.widget_label, "Screen time today")
                val text = if (app.usage.hasPermission()) {
                    formatDuration(app.usage.todayUsage().values.sum())
                } else {
                    "—"
                }
                views.setTextViewText(R.id.widget_value, text)
            }
            val next = Alarms.nextOf(app.db.alarmDao().getAll(), app.db.alarmGroupDao().getAll())
            val due = app.db.reminderDao().getAll().count { r ->
                !r.completed && r.dueAt != null && !ReminderTime.toLocal(r.dueAt).toLocalDate().isAfter(LocalDate.now())
            }
            views.setTextViewText(
                R.id.widget_footer,
                listOfNotNull(
                    next?.let { "⏰ " + it.second.toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)) },
                    if (due > 0) "$due due" else null,
                    "Lv ${progress.level}",
                    "Streak ${progress.currentStreak}",
                    if (focus.minutesToday > 0) "Focus ${formatDuration(focus.minutesToday * 60_000L)}" else null,
                ).joinToString(" · "),
            )
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)
            return views
        }
    }
}
