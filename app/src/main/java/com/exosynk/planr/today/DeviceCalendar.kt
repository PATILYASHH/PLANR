package com.exosynk.planr.today

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** An event from the phone's own calendars (Google, Samsung, Outlook… whatever is synced). */
data class CalendarEvent(
    val eventId: Long,
    val title: String,
    val begin: LocalDateTime,
    val end: LocalDateTime,
    val allDay: Boolean,
    val color: Int,
    val calendar: String,
    val location: String?,
) {
    /** Days the event covers (multi-day events appear on each). */
    fun days(): List<LocalDate> {
        val last = if (allDay) end.toLocalDate().minusDays(1) else end.minusNanos(1).toLocalDate()
        val out = mutableListOf<LocalDate>()
        var d = begin.toLocalDate()
        while (!d.isAfter(maxOf(last, begin.toLocalDate())) && out.size < 62) { out += d; d = d.plusDays(1) }
        return out
    }
}

/**
 * Reads calendar events on the device (READ_CALENDAR, asked only when the user
 * turns this on). PLANR never writes to the calendar and nothing leaves the phone.
 */
object DeviceCalendar {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun events(context: Context, from: LocalDate, to: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<CalendarEvent> {
        if (!hasPermission(context)) return emptyList()
        // All-day events are stored in UTC; widen the window by a day on each side.
        val start = from.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = to.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, start)
            ContentUris.appendId(it, end)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            CalendarContract.Instances.EVENT_LOCATION,
        )
        val out = mutableListOf<CalendarEvent>()
        runCatching {
            context.contentResolver.query(
                uri, projection, "${CalendarContract.Instances.VISIBLE} = 1", null,
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { c ->
                while (c.moveToNext() && out.size < 500) {
                    val allDay = c.getInt(4) == 1
                    val tz: ZoneId = if (allDay) ZoneOffset.UTC else zone
                    val b = Instant.ofEpochMilli(c.getLong(2)).atZone(tz).toLocalDateTime()
                    val e = Instant.ofEpochMilli(c.getLong(3)).atZone(tz).toLocalDateTime().let { if (it.isBefore(b)) b else it }
                    out += CalendarEvent(
                        eventId = c.getLong(0),
                        title = c.getString(1)?.ifBlank { null } ?: "(No title)",
                        begin = b,
                        end = e,
                        allDay = allDay,
                        color = c.getInt(5),
                        calendar = c.getString(6) ?: "",
                        location = c.getString(7)?.ifBlank { null },
                    )
                }
            }
        }
        return out.filter { ev -> ev.days().any { !it.isBefore(from) && !it.isAfter(to) } }
    }

    fun byDay(events: List<CalendarEvent>): Map<LocalDate, List<CalendarEvent>> {
        val out = HashMap<LocalDate, MutableList<CalendarEvent>>()
        events.forEach { ev -> ev.days().forEach { out.getOrPut(it) { mutableListOf() } += ev } }
        return out.mapValues { (_, l) -> l.sortedWith(compareBy({ !it.allDay }, { it.begin })) }
    }

    fun openIntent(eventId: Long): Intent =
        Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
