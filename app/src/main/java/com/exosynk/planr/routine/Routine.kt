package com.exosynk.planr.routine

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

/** "If" part of a routine. State triggers stay true for a while and can undo their actions. */
sealed interface Trigger {
    /** Run with a button (or from another routine's notification). */
    data object Manual : Trigger

    /** At [start]; with an [end] it is a window and can undo at the end. Minutes of day. */
    data class Time(val start: Int, val end: Int = NO_END, val days: Int = 0b1111111) : Trigger {
        val isWindow: Boolean get() = end != NO_END && end != start
    }

    /** When an alarm is dismissed ([alarmId] null = any alarm). */
    data class AlarmDismissed(val alarmId: Long? = null) : Trigger

    /** While the phone is charging. */
    data object Charging : Trigger

    /** While the battery is at or below [percent] and not charging. */
    data class BatteryLow(val percent: Int = 20) : Trigger

    /** While wired, USB or Bluetooth headphones are connected. */
    data object Headphones : Trigger

    /** While [packageName] is the app in front. */
    data class AppOpened(val packageName: String) : Trigger

    data object OffGridStarted : Trigger
    data object OffGridEnded : Trigger
    data object FocusFinished : Trigger

    /** True if this trigger describes a condition that later stops being true. */
    val isState: Boolean
        get() = when (this) {
            is Time -> isWindow
            Charging, Headphones, is BatteryLow, is AppOpened -> true
            else -> false
        }

    companion object {
        const val NO_END = -1
    }
}

enum class RingerMode(val title: String) { SOUND("Sound"), VIBRATE("Vibrate"), SILENT("Mute") }

/** "Then" part of a routine. */
sealed interface Action {
    data class Dnd(val on: Boolean) : Action
    data class Ringer(val mode: RingerMode) : Action
    data class MediaVolume(val percent: Int) : Action
    data class StartOffGrid(val minutes: Int) : Action
    data class StartFocus(val minutes: Int = 25, val cycles: Int = 1) : Action
    data class Gateway(val on: Boolean) : Action
    data class AlarmGroupSwitch(val groupId: Long, val on: Boolean) : Action
    data class Notify(val text: String) : Action
    /** Notification with today's alarms, reminders and habit progress. */
    data object TodayBrief : Action
    data class OpenApp(val packageName: String) : Action

    /** Actions whose previous state can be put back when a state trigger ends. */
    val isUndoable: Boolean
        get() = this is Dnd || this is Ringer || this is MediaVolume || this is Gateway || this is AlarmGroupSwitch
}

@Entity(tableName = "routines")
data class Routine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val enabled: Boolean = true,
    /** JSON, see [RoutineCodec]. */
    val trigger: String,
    /** JSON array, see [RoutineCodec]. */
    val actions: String,
    /** For state triggers: restore DND, sound, volume, Gateway and alarm groups when it ends. */
    val undoOnEnd: Boolean = true,
    val lastRunAt: Long = 0,
) {
    val parsedTrigger: Trigger get() = RoutineCodec.decodeTrigger(trigger) ?: Trigger.Manual
    val parsedActions: List<Action> get() = RoutineCodec.decodeActions(actions)

    companion object {
        fun of(name: String, trigger: Trigger, actions: List<Action>, undo: Boolean = true, id: Long = 0, enabled: Boolean = true) =
            Routine(
                id = id,
                name = name,
                enabled = enabled,
                trigger = RoutineCodec.encodeTrigger(trigger),
                actions = RoutineCodec.encodeActions(actions),
                undoOnEnd = undo,
            )
    }
}

/** JSON (de)serialization for triggers and actions; unknown entries are dropped. */
object RoutineCodec {

    fun encodeTrigger(t: Trigger): String = JSONObject().apply {
        when (t) {
            Trigger.Manual -> put("type", "manual")
            is Trigger.Time -> put("type", "time").put("start", t.start).put("end", t.end).put("days", t.days)
            is Trigger.AlarmDismissed -> {
                put("type", "alarmDismissed")
                t.alarmId?.let { put("alarmId", it) }
            }
            Trigger.Charging -> put("type", "charging")
            is Trigger.BatteryLow -> put("type", "batteryLow").put("percent", t.percent)
            Trigger.Headphones -> put("type", "headphones")
            is Trigger.AppOpened -> put("type", "appOpened").put("pkg", t.packageName)
            Trigger.OffGridStarted -> put("type", "offGridStarted")
            Trigger.OffGridEnded -> put("type", "offGridEnded")
            Trigger.FocusFinished -> put("type", "focusFinished")
        }
    }.toString()

    fun decodeTrigger(json: String): Trigger? {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
        return when (o.optString("type")) {
            "manual" -> Trigger.Manual
            "time" -> Trigger.Time(
                start = o.optInt("start", 0).coerceIn(0, 1439),
                end = o.optInt("end", Trigger.NO_END).let { if (it == Trigger.NO_END) it else it.coerceIn(0, 1439) },
                days = (o.optInt("days", 0b1111111) and 0b1111111).takeIf { it != 0 } ?: 0b1111111,
            )
            "alarmDismissed" -> Trigger.AlarmDismissed(if (o.has("alarmId")) o.optLong("alarmId") else null)
            "charging" -> Trigger.Charging
            "batteryLow" -> Trigger.BatteryLow(o.optInt("percent", 20).coerceIn(5, 95))
            "headphones" -> Trigger.Headphones
            "appOpened" -> o.optString("pkg").takeIf { it.isNotBlank() }?.let { Trigger.AppOpened(it) }
            "offGridStarted" -> Trigger.OffGridStarted
            "offGridEnded" -> Trigger.OffGridEnded
            "focusFinished" -> Trigger.FocusFinished
            else -> null
        }
    }

    fun encodeActions(list: List<Action>): String = JSONArray().apply {
        list.forEach { a ->
            put(JSONObject().apply {
                when (a) {
                    is Action.Dnd -> put("type", "dnd").put("on", a.on)
                    is Action.Ringer -> put("type", "ringer").put("mode", a.mode.name)
                    is Action.MediaVolume -> put("type", "mediaVolume").put("percent", a.percent)
                    is Action.StartOffGrid -> put("type", "offGrid").put("minutes", a.minutes)
                    is Action.StartFocus -> put("type", "focus").put("minutes", a.minutes).put("cycles", a.cycles)
                    is Action.Gateway -> put("type", "gateway").put("on", a.on)
                    is Action.AlarmGroupSwitch -> put("type", "alarmGroup").put("groupId", a.groupId).put("on", a.on)
                    is Action.Notify -> put("type", "notify").put("text", a.text)
                    Action.TodayBrief -> put("type", "todayBrief")
                    is Action.OpenApp -> put("type", "openApp").put("pkg", a.packageName)
                }
            })
        }
    }.toString()

    fun decodeActions(json: String): List<Action> {
        val arr = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            when (o.optString("type")) {
                "dnd" -> Action.Dnd(o.optBoolean("on", true))
                "ringer" -> Action.Ringer(
                    runCatching { RingerMode.valueOf(o.optString("mode")) }.getOrDefault(RingerMode.VIBRATE)
                )
                "mediaVolume" -> Action.MediaVolume(o.optInt("percent", 30).coerceIn(0, 100))
                "offGrid" -> Action.StartOffGrid(o.optInt("minutes", 60).coerceIn(15, 24 * 60))
                "focus" -> Action.StartFocus(o.optInt("minutes", 25).coerceIn(5, 180), o.optInt("cycles", 1).coerceIn(1, 12))
                "gateway" -> Action.Gateway(o.optBoolean("on", true))
                "alarmGroup" -> Action.AlarmGroupSwitch(o.optLong("groupId"), o.optBoolean("on", true))
                "notify" -> o.optString("text").takeIf { it.isNotBlank() }?.let { Action.Notify(it.take(200)) }
                "todayBrief" -> Action.TodayBrief
                "openApp" -> o.optString("pkg").takeIf { it.isNotBlank() }?.let { Action.OpenApp(it) }
                else -> null
            }
        }
    }
}

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Routine>>

    @Query("SELECT * FROM routines")
    suspend fun getAll(): List<Routine>

    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun get(id: Long): Routine?

    @Upsert
    suspend fun upsert(routine: Routine): Long

    @Query("UPDATE routines SET lastRunAt = :at WHERE id = :id")
    suspend fun markRun(id: Long, at: Long)

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM routines")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(routines: List<Routine>) {
        deleteAll()
        routines.forEach { upsert(it) }
    }
}
