package com.planr.app.data.db

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
)

enum class ScheduleMode { BLOCK, ALLOW }

/**
 * Time-based rule. BLOCK = listed apps are blocked; ALLOW = only listed apps work.
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

@Entity(tableName = "offgrid_sessions")
data class OffGridSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val plannedEndAt: Long,
    val endedAt: Long? = null,
    val endedEarly: Boolean = false,
)
