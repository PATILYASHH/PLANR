package com.exosynk.planr.alarm

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A named set of alarms with one master switch (like Samsung Clock's alarm groups). */
@Entity(tableName = "alarm_groups")
data class AlarmGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val enabled: Boolean = true,
)

enum class VibrationPattern(val title: String, val timings: LongArray) {
    BASIC("Basic call", longArrayOf(0, 800, 600)),
    HEARTBEAT("Heartbeat", longArrayOf(0, 120, 120, 120, 700)),
    TICKTOCK("Ticktock", longArrayOf(0, 60, 440)),
    WALTZ("Waltz", longArrayOf(0, 400, 200, 150, 200, 150, 600)),
    ZIGZIG("Zig-zig-zig", longArrayOf(0, 100, 100, 100, 100, 100, 900)),
}

enum class DismissChallenge(val title: String) {
    NONE("None"),
    MATH("Solve 3 math problems"),
}

@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String = "",
    val hour: Int = 7,
    val minute: Int = 0,
    /** Bit 0 = Monday … bit 6 = Sunday. 0 = rings once. */
    val daysMask: Int = 0,
    /** One-time alarms only: ISO date to ring on (null = next time the clock reaches it). */
    val date: String? = null,
    val enabled: Boolean = true,
    val groupId: Long? = null,
    /** Epoch millis of one occurrence the user chose to skip (0 = none). */
    val skippedAt: Long = 0,
    /** Ringtone URI; null = default alarm sound; [SILENT] = vibrate only. */
    val sound: String? = null,
    /** Alarm volume, percent of the alarm stream's maximum. */
    val volume: Int = 70,
    val gradualVolume: Boolean = true,
    val vibrate: Boolean = true,
    val vibrationPattern: String = VibrationPattern.BASIC.name,
    val snoozeEnabled: Boolean = true,
    val snoozeMinutes: Int = 5,
    /** How many times snooze may be used (0 = unlimited). */
    val snoozeRepeat: Int = 3,
    /** Read the time and alarm name aloud before the sound starts. */
    val readAloud: Boolean = false,
    val dismissChallenge: String = DismissChallenge.NONE.name,
    /** Show today's reminders, habits and routines after dismissing. */
    val morningBrief: Boolean = false,
) {
    val isRepeating: Boolean get() = daysMask != 0

    val pattern: VibrationPattern
        get() = runCatching { VibrationPattern.valueOf(vibrationPattern) }.getOrDefault(VibrationPattern.BASIC)

    val challenge: DismissChallenge
        get() = runCatching { DismissChallenge.valueOf(dismissChallenge) }.getOrDefault(DismissChallenge.NONE)

    companion object {
        const val SILENT = "silent"
    }
}

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour, minute")
    fun observeAll(): Flow<List<Alarm>>

    @Query("SELECT * FROM alarms")
    suspend fun getAll(): List<Alarm>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun get(id: Long): Alarm?

    @Upsert
    suspend fun upsert(alarm: Alarm): Long

    @Query("DELETE FROM alarms WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE alarms SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE alarms SET groupId = NULL WHERE groupId = :groupId")
    suspend fun ungroup(groupId: Long)

    @Query("DELETE FROM alarms")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(alarms: List<Alarm>) {
        deleteAll()
        alarms.forEach { upsert(it) }
    }
}

@Dao
interface AlarmGroupDao {
    @Query("SELECT * FROM alarm_groups ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<AlarmGroup>>

    @Query("SELECT * FROM alarm_groups")
    suspend fun getAll(): List<AlarmGroup>

    @Upsert
    suspend fun upsert(group: AlarmGroup): Long

    @Query("UPDATE alarm_groups SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM alarm_groups WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM alarm_groups")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(groups: List<AlarmGroup>) {
        deleteAll()
        groups.forEach { upsert(it) }
    }
}
