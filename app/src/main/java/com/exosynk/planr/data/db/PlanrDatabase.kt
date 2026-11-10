package com.exosynk.planr.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.exosynk.planr.alarm.Alarm
import com.exosynk.planr.alarm.AlarmDao
import com.exosynk.planr.alarm.AlarmGroup
import com.exosynk.planr.alarm.AlarmGroupDao
import com.exosynk.planr.notes.Note
import com.exosynk.planr.notes.NoteDao
import com.exosynk.planr.reminder.Reminder
import com.exosynk.planr.reminder.ReminderCategory
import com.exosynk.planr.reminder.ReminderCategoryDao
import com.exosynk.planr.reminder.ReminderDao
import com.exosynk.planr.routine.Routine
import com.exosynk.planr.routine.RoutineDao
import kotlinx.coroutines.flow.Flow

@Dao
interface ShieldDao {
    @Query("SELECT * FROM shield_rules ORDER BY appLabel COLLATE NOCASE")
    fun observeAll(): Flow<List<ShieldRule>>

    @Query("SELECT * FROM shield_rules")
    suspend fun getAll(): List<ShieldRule>

    @Query("SELECT * FROM shield_rules WHERE packageName = :packageName")
    suspend fun get(packageName: String): ShieldRule?

    @Upsert
    suspend fun upsert(rule: ShieldRule)

    @Query("DELETE FROM shield_rules WHERE packageName = :packageName")
    suspend fun delete(packageName: String)

    @Query("DELETE FROM shield_rules")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(rules: List<ShieldRule>) {
        deleteAll()
        rules.forEach { upsert(it) }
    }
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules ORDER BY startMinute")
    fun observeAll(): Flow<List<Schedule>>

    @Query("SELECT * FROM schedules")
    suspend fun getAll(): List<Schedule>

    @Upsert
    suspend fun upsert(schedule: Schedule)

    @Delete
    suspend fun delete(schedule: Schedule)

    @Query("DELETE FROM schedules")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(schedules: List<Schedule>) {
        deleteAll()
        schedules.forEach { upsert(it) }
    }
}

@Dao
interface OffGridSessionDao {
    @Query("SELECT * FROM offgrid_sessions ORDER BY startedAt DESC LIMIT 50")
    fun observeRecent(): Flow<List<OffGridSession>>

    @Query("SELECT * FROM offgrid_sessions ORDER BY startedAt DESC LIMIT 50")
    suspend fun recent(): List<OffGridSession>

    @Query("SELECT * FROM offgrid_sessions ORDER BY startedAt")
    suspend fun getAll(): List<OffGridSession>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: OffGridSession): Long

    @Query("UPDATE offgrid_sessions SET endedAt = :endedAt, endedEarly = :early WHERE id = :id")
    suspend fun finish(id: Long, endedAt: Long, early: Boolean)

    @Query("DELETE FROM offgrid_sessions")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(sessions: List<OffGridSession>) {
        deleteAll()
        sessions.forEach { insert(it) }
    }
}

@Dao
interface DailyUsageDao {
    @Upsert
    suspend fun upsertAll(rows: List<DailyUsage>)

    @Query("SELECT DISTINCT day FROM daily_usage")
    suspend fun storedDays(): List<String>

    @Query("SELECT * FROM daily_usage WHERE day >= :fromDay")
    suspend fun since(fromDay: String): List<DailyUsage>

    @Query("SELECT * FROM daily_usage WHERE packageName = :packageName AND day >= :fromDay")
    suspend fun forApp(packageName: String, fromDay: String): List<DailyUsage>

    @Query("DELETE FROM daily_usage WHERE day = :day")
    suspend fun deleteDay(day: String)
}

class Converters {
    @TypeConverter
    fun modeToString(mode: ScheduleMode): String = mode.name

    @TypeConverter
    fun stringToMode(value: String): ScheduleMode =
        runCatching { ScheduleMode.valueOf(value) }.getOrDefault(ScheduleMode.BLOCK)
}

@Database(
    entities = [
        ShieldRule::class, Schedule::class, OffGridSession::class, DailyUsage::class,
        AlarmGroup::class, Alarm::class, ReminderCategory::class, Reminder::class, Note::class, Routine::class,
    ],
    version = 5,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class PlanrDatabase : RoomDatabase() {
    abstract fun shieldDao(): ShieldDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun offGridDao(): OffGridSessionDao
    abstract fun dailyUsageDao(): DailyUsageDao
    abstract fun alarmDao(): AlarmDao
    abstract fun alarmGroupDao(): AlarmGroupDao
    abstract fun reminderDao(): ReminderDao
    abstract fun reminderCategoryDao(): ReminderCategoryDao
    abstract fun noteDao(): NoteDao
    abstract fun routineDao(): RoutineDao

    companion object {
        /** v0.2 → v0.3: per-app open limit and daily usage history. Keeps all user data. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE shield_rules ADD COLUMN dailyOpenLimit INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS daily_usage (" +
                        "day TEXT NOT NULL, packageName TEXT NOT NULL, ms INTEGER NOT NULL, " +
                        "PRIMARY KEY(day, packageName))"
                )
            }
        }

        /** v0.3 → v0.4: planner modules (alarms, reminders, notes, routines). SQL matches schemas/4.json. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_3_4_SQL.forEach { db.execSQL(it) }
            }
        }

        /** v0.4 → v0.5: contact linked to a reminder ("Call Parth at 9"). */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN contactName TEXT")
                db.execSQL("ALTER TABLE reminders ADD COLUMN contactNumber TEXT")
                db.execSQL("ALTER TABLE reminders ADD COLUMN contactAction TEXT")
            }
        }

        fun build(context: Context): PlanrDatabase =
            Room.databaseBuilder(context, PlanrDatabase::class.java, "planr.db")
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                // Only the v0.1 schema (never shipped as a release) is dropped.
                .fallbackToDestructiveMigrationFrom(true, 1)
                .build()
    }
}

internal val MIGRATION_3_4_SQL = listOf(
    "CREATE TABLE IF NOT EXISTS `alarm_groups` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `enabled` INTEGER NOT NULL)",
    "CREATE TABLE IF NOT EXISTS `alarms` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `label` TEXT NOT NULL, `hour` INTEGER NOT NULL, `minute` INTEGER NOT NULL, `daysMask` INTEGER NOT NULL, `date` TEXT, `enabled` INTEGER NOT NULL, `groupId` INTEGER, `skippedAt` INTEGER NOT NULL, `sound` TEXT, `volume` INTEGER NOT NULL, `gradualVolume` INTEGER NOT NULL, `vibrate` INTEGER NOT NULL, `vibrationPattern` TEXT NOT NULL, `snoozeEnabled` INTEGER NOT NULL, `snoozeMinutes` INTEGER NOT NULL, `snoozeRepeat` INTEGER NOT NULL, `readAloud` INTEGER NOT NULL, `dismissChallenge` TEXT NOT NULL, `morningBrief` INTEGER NOT NULL)",
    "CREATE TABLE IF NOT EXISTS `reminder_categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `color` INTEGER NOT NULL)",
    "CREATE TABLE IF NOT EXISTS `reminders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `notes` TEXT NOT NULL, `dueAt` INTEGER, `allDay` INTEGER NOT NULL, `repeat` TEXT NOT NULL, `earlyMinutes` INTEGER NOT NULL, `categoryId` INTEGER, `pinned` INTEGER NOT NULL, `alertStyle` TEXT NOT NULL, `completed` INTEGER NOT NULL, `completedAt` INTEGER, `noteId` INTEGER, `snoozedUntil` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)",
    "CREATE TABLE IF NOT EXISTS `notes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `body` TEXT NOT NULL, `checklist` TEXT NOT NULL, `isChecklist` INTEGER NOT NULL, `color` INTEGER NOT NULL, `pinned` INTEGER NOT NULL, `archived` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)",
    "CREATE TABLE IF NOT EXISTS `routines` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `trigger` TEXT NOT NULL, `actions` TEXT NOT NULL, `undoOnEnd` INTEGER NOT NULL, `lastRunAt` INTEGER NOT NULL)",
)
