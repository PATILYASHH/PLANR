package com.exosynk.planr.notes

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

data class ChecklistItem(val text: String, val done: Boolean = false)

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val body: String = "",
    /** Checklist items as JSON (see [Checklist]); used when [isChecklist]. */
    val checklist: String = "",
    val isChecklist: Boolean = false,
    /** Index into the note colour palette. */
    val color: Int = 0,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val items: List<ChecklistItem> get() = Checklist.decode(checklist)

    val isEmpty: Boolean get() = title.isBlank() && body.isBlank() && items.none { it.text.isNotBlank() }

    /** Text used in lists, search and when sharing to a reminder. */
    val preview: String
        get() = if (isChecklist) items.joinToString("\n") { (if (it.done) "☑ " else "☐ ") + it.text } else body
}

object Checklist {
    fun encode(items: List<ChecklistItem>): String = JSONArray().apply {
        items.forEach { put(JSONObject().put("t", it.text).put("d", it.done)) }
    }.toString()

    fun decode(json: String): List<ChecklistItem> {
        if (json.isBlank()) return emptyList()
        val arr = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { ChecklistItem(it.optString("t"), it.optBoolean("d")) }
        }
    }

    /** Plain text → checklist (one item per non-blank line). */
    fun fromText(text: String): List<ChecklistItem> =
        text.lines().map { it.trim().removePrefix("- ").removePrefix("☐ ").removePrefix("☑ ") }
            .filter { it.isNotBlank() }.map { ChecklistItem(it) }

    fun toText(items: List<ChecklistItem>): String = items.joinToString("\n") { it.text }
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY pinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<Note>>

    @Query("SELECT * FROM notes")
    suspend fun getAll(): List<Note>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: Long): Note?

    @Upsert
    suspend fun upsert(note: Note): Long

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM notes")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(notes: List<Note>) {
        deleteAll()
        notes.forEach { upsert(it) }
    }
}
