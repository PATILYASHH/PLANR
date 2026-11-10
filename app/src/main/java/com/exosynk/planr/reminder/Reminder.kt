package com.exosynk.planr.reminder

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "reminder_categories")
data class ReminderCategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** ARGB colour. */
    val color: Int,
)

enum class Repeat(val title: String) {
    NONE("Doesn't repeat"),
    DAILY("Every day"),
    WEEKDAYS("Weekdays"),
    WEEKLY("Every week"),
    MONTHLY("Every month"),
    YEARLY("Every year"),
}

enum class AlertStyle(val title: String) {
    NOTIFICATION("Notification"),
    ALARM("Alarm (full screen, rings)"),
}

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String = "",
    /** Epoch millis it's due (null = no date, an "anytime" reminder). */
    val dueAt: Long? = null,
    /** All-day reminders alert at [ALL_DAY_ALERT_HOUR] on their date. */
    val allDay: Boolean = false,
    val repeat: String = Repeat.NONE.name,
    /** Extra alert this many minutes before it's due (0 = none). */
    val earlyMinutes: Int = 0,
    val categoryId: Long? = null,
    val pinned: Boolean = false,
    val alertStyle: String = AlertStyle.NOTIFICATION.name,
    val completed: Boolean = false,
    val completedAt: Long? = null,
    /** Note this reminder was created from. */
    val noteId: Long? = null,
    /** When snoozed, the next alert time (0 = not snoozed). */
    val snoozedUntil: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val repeatRule: Repeat get() = runCatching { Repeat.valueOf(repeat) }.getOrDefault(Repeat.NONE)
    val style: AlertStyle get() = runCatching { AlertStyle.valueOf(alertStyle) }.getOrDefault(AlertStyle.NOTIFICATION)

    companion object {
        const val ALL_DAY_ALERT_HOUR = 9
    }
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY completed, pinned DESC, dueAt IS NULL, dueAt, createdAt DESC")
    fun observeAll(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders")
    suspend fun getAll(): List<Reminder>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun get(id: Long): Reminder?

    @Query("SELECT * FROM reminders WHERE noteId = :noteId")
    fun observeForNote(noteId: Long): Flow<List<Reminder>>

    @Upsert
    suspend fun upsert(reminder: Reminder): Long

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE reminders SET categoryId = NULL WHERE categoryId = :categoryId")
    suspend fun uncategorize(categoryId: Long)

    @Query("UPDATE reminders SET noteId = NULL WHERE noteId = :noteId")
    suspend fun unlinkNote(noteId: Long)

    @Query("DELETE FROM reminders WHERE completed = 1")
    suspend fun deleteCompleted()

    @Query("DELETE FROM reminders")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(reminders: List<Reminder>) {
        deleteAll()
        reminders.forEach { upsert(it) }
    }
}

@Dao
interface ReminderCategoryDao {
    @Query("SELECT * FROM reminder_categories ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ReminderCategory>>

    @Query("SELECT * FROM reminder_categories")
    suspend fun getAll(): List<ReminderCategory>

    @Upsert
    suspend fun upsert(category: ReminderCategory): Long

    @Query("DELETE FROM reminder_categories WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM reminder_categories")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(categories: List<ReminderCategory>) {
        deleteAll()
        categories.forEach { upsert(it) }
    }
}
