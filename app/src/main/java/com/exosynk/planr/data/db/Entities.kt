package com.exosynk.planr.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.DayOfWeek
import java.time.LocalDateTime

/**
 * A shield on one app: mindful pause, optional forced delay, optional daily limit.
 */
@Entity(tableName = "shield_rules")
data class ShieldRule(
    @PrimaryKey val packageName: String,
    val appLabel: String,
    /** Breathing pause shown before Continue (seconds). */
    val pauseSeconds: Int = 5,
    /** Extra forced wait before Continue is enabled (seconds, 0 = none). */
    val delaySeconds: Int = 0,
    /** Hard block once today's usage reaches this (minutes, 0 = no limit). */
    val dailyLimitMinutes: Int = 0,
    /** After Continue, how long the app stays open without re-prompting. */
    val passMinutes: Int = 5,
    val enabled: Boolean = true,
    /** Max times per day the user may continue past the pause (0 = unlimited). */
    @ColumnInfo(defaultValue = "0") val dailyOpenLimit: Int = 0,
)

/** A limit of 0 means "none", so any positive limit is stricter than 0. */
private fun limitAtLeastAsStrict(old: Int, new: Int): Boolean =
    if (old <= 0) true else new in 1..old

/**
 * True if replacing this rule with [next] would block less. Lockdown only lets
 * users tighten shields, never loosen them.
 */
fun ShieldRule.wouldLoosen(next: ShieldRule): Boolean = !(
    (next.enabled || !enabled) &&
        next.pauseSeconds >= pauseSeconds &&
        next.delaySeconds >= delaySeconds &&
        next.passMinutes <= passMinutes &&
        limitAtLeastAsStrict(dailyLimitMinutes, next.dailyLimitMinutes) &&
        limitAtLeastAsStrict(dailyOpenLimit, next.dailyOpenLimit)
    )

enum class ScheduleMode { BLOCK, ALLOW, OFFGRID }

/**
 * Time-based rule. BLOCK = listed apps are blocked; ALLOW = only listed apps work;
 * OFFGRID = a full Off-Grid session runs for the window, listed apps stay usable.
 * [daysMask]: bit 0 = Monday … bit 6 = Sunday. Overnight ranges (start > end) are supported.
 */
@Entity(tableName = "schedules")
data class Schedule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startMinute: Int,
    val endMinute: Int,
    val daysMask: Int = 0b1111111,
    val mode: ScheduleMode = ScheduleMode.BLOCK,
    /** Comma-separated package names. */
    val packages: String = "",
    val enabled: Boolean = true,
    /** OFFGRID only: early-unlock step (UnlockChallenge name). */
    val challenge: String = "TYPING",
)

fun Schedule.packageSet(): Set<String> =
    packages.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

fun Set<String>.toPackageString(): String = sorted().joinToString(",")

fun dayBit(day: DayOfWeek): Int = 1 shl (day.value - 1)

/** True if the schedule covers [now]. */
fun Schedule.isActiveAt(now: LocalDateTime): Boolean {
    if (!enabled) return false
    val minute = now.hour * 60 + now.minute
    val today = now.dayOfWeek
    val yesterday = today.minus(1)
    return if (startMinute == endMinute) {
        daysMask and dayBit(today) != 0 // whole day
    } else if (startMinute < endMinute) {
        daysMask and dayBit(today) != 0 && minute >= startMinute && minute < endMinute
    } else {
        // Overnight: e.g. 23:00 → 07:00
        (daysMask and dayBit(today) != 0 && minute >= startMinute) ||
            (daysMask and dayBit(yesterday) != 0 && minute < endMinute)
    }
}

/** Date the current window started on (yesterday for the after-midnight part of an overnight window). */
fun Schedule.windowStartDate(now: LocalDateTime): java.time.LocalDate {
    val minute = now.hour * 60 + now.minute
    return if (startMinute > endMinute && minute < endMinute) now.toLocalDate().minusDays(1)
    else now.toLocalDate()
}

/** End of the window that is active at [now]. Call only when [isActiveAt] is true. */
fun Schedule.windowEnd(now: LocalDateTime): LocalDateTime {
    val start = windowStartDate(now)
    return when {
        startMinute == endMinute -> start.plusDays(1).atStartOfDay()
        startMinute < endMinute -> start.atStartOfDay().plusMinutes(endMinute.toLong())
        else -> start.plusDays(1).atStartOfDay().plusMinutes(endMinute.toLong())
    }
}

@Entity(tableName = "offgrid_sessions")
data class OffGridSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val plannedEndAt: Long,
    val endedAt: Long? = null,
    val endedEarly: Boolean = false,
    /** Set when the session was started by an Off-Grid schedule. */
    val scheduleId: Long? = null,
)

/** One app's foreground time on one day, kept so history outlives Android's usage-event retention. */
@Entity(tableName = "daily_usage", primaryKeys = ["day", "packageName"])
data class DailyUsage(
    /** ISO date, e.g. 2026-11-10. */
    val day: String,
    val packageName: String,
    val ms: Long,
)
