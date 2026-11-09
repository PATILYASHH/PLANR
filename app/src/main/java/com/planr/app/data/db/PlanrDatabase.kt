package com.planr.app.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ShieldDao {
    @Query("SELECT * FROM shield_rules ORDER BY appLabel COLLATE NOCASE")
    fun observeAll(): Flow<List<ShieldRule>>

    @Upsert
    suspend fun upsert(rule: ShieldRule)

    @Query("DELETE FROM shield_rules WHERE packageName = :packageName")
    suspend fun delete(packageName: String)
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules ORDER BY startMinute")
    fun observeAll(): Flow<List<Schedule>>

    @Upsert
    suspend fun upsert(schedule: Schedule)

    @Delete
    suspend fun delete(schedule: Schedule)
}

@Dao
interface OffGridSessionDao {
    @Query("SELECT * FROM offgrid_sessions ORDER BY startedAt DESC LIMIT 50")
    fun observeRecent(): Flow<List<OffGridSession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: OffGridSession): Long

    @Query("UPDATE offgrid_sessions SET endedAt = :endedAt, endedEarly = :early WHERE id = :id")
    suspend fun finish(id: Long, endedAt: Long, early: Boolean)
}

class Converters {
    @TypeConverter
    fun modeToString(mode: ScheduleMode): String = mode.name

    @TypeConverter
    fun stringToMode(value: String): ScheduleMode =
        runCatching { ScheduleMode.valueOf(value) }.getOrDefault(ScheduleMode.BLOCK)
}

@Database(
    entities = [ShieldRule::class, Schedule::class, OffGridSession::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class PlanrDatabase : RoomDatabase() {
    abstract fun shieldDao(): ShieldDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun offGridDao(): OffGridSessionDao

    companion object {
        fun build(context: Context): PlanrDatabase =
            Room.databaseBuilder(context, PlanrDatabase::class.java, "planr.db").build()
    }
}
