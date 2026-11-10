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
    entities = [ShieldRule::class, Schedule::class, OffGridSession::class, DailyUsage::class],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class PlanrDatabase : RoomDatabase() {
    abstract fun shieldDao(): ShieldDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun offGridDao(): OffGridSessionDao
    abstract fun dailyUsageDao(): DailyUsageDao

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

        fun build(context: Context): PlanrDatabase =
            Room.databaseBuilder(context, PlanrDatabase::class.java, "planr.db")
                .addMigrations(MIGRATION_2_3)
                // Only the v0.1 schema (never shipped as a release) is dropped.
                .fallbackToDestructiveMigrationFrom(true, 1)
                .build()
    }
}
