package com.exosynk.planr.routine

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import androidx.core.content.edit
import com.exosynk.planr.PlanrApp
import com.exosynk.planr.alarm.AlarmScheduler
import com.exosynk.planr.alarm.Alarms
import com.exosynk.planr.data.UnlockChallenge
import com.exosynk.planr.engine.Notifier
import com.exosynk.planr.engine.OffGridManager
import com.exosynk.planr.engine.PlanrAccessibilityService
import com.exosynk.planr.today.TodaySummary
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.time.LocalDateTime

/** Things that can start (or end) a routine. */
sealed interface RoutineEvent {
    data class Manual(val routineId: Long) : RoutineEvent
    data class AlarmDismissed(val alarmId: Long) : RoutineEvent
    data object OffGridStarted : RoutineEvent
    data object OffGridEnded : RoutineEvent
    data object FocusFinished : RoutineEvent
    /** Time boundary, device state change or periodic check: re-evaluate state triggers. */
    data object Evaluate : RoutineEvent
    data class TimeStart(val routineId: Long) : RoutineEvent
}

/**
 * Samsung-style "Modes & Routines": if a trigger happens, run the actions. State
 * triggers (time windows, charging, headphones, low battery, an app in front) remember
 * what they changed and put it back when they end, if "undo" is on.
 */
object RoutineEngine {

    private const val PREFS = "routine_state"
    private val mutex = Mutex()

    // Live device state, fed by the accessibility service. Until it reports, the state
    // is unknown and device-state routines are left exactly as they are (no flapping
    // after a process restart).
    @Volatile var charging = false
    @Volatile var batteryPercent = 100
    @Volatile var batteryKnown = false
    @Volatile var headphones = false
    @Volatile var headphonesKnown = false
    @Volatile var foregroundPackage: String? = null

    fun post(event: RoutineEvent) {
        val app = PlanrApp.instance
        app.appScope.launch { runCatching { mutex.withLock { handle(app, event) } } }
    }

    /** Runs a routine now, regardless of its trigger (the "Run" button). */
    fun runNow(routineId: Long) = post(RoutineEvent.Manual(routineId))

    fun activeIds(app: PlanrApp): Set<Long> =
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all.keys
            .filter { it.startsWith("active:") }
            .mapNotNull { it.removePrefix("active:").toLongOrNull() }
            .toSet()

    private suspend fun handle(app: PlanrApp, event: RoutineEvent) {
        val routines = app.db.routineDao().getAll()
        val enabled = routines.filter { it.enabled }
        when (event) {
            is RoutineEvent.Manual -> routines.firstOrNull { it.id == event.routineId }?.let { run(app, it) }
            is RoutineEvent.TimeStart -> enabled.firstOrNull { it.id == event.routineId }?.let { r ->
                val t = r.parsedTrigger
                if (t is Trigger.Time && !t.isWindow) run(app, r)
            }
            is RoutineEvent.AlarmDismissed -> enabled.filter {
                val t = it.parsedTrigger
                t is Trigger.AlarmDismissed && (t.alarmId == null || t.alarmId == event.alarmId)
            }.forEach { run(app, it) }
            RoutineEvent.OffGridStarted -> enabled.filter { it.parsedTrigger == Trigger.OffGridStarted }.forEach { run(app, it) }
            RoutineEvent.OffGridEnded -> enabled.filter { it.parsedTrigger == Trigger.OffGridEnded }.forEach { run(app, it) }
            RoutineEvent.FocusFinished -> enabled.filter { it.parsedTrigger == Trigger.FocusFinished }.forEach { run(app, it) }
            RoutineEvent.Evaluate -> Unit
        }
        evaluateStates(app, routines)
    }

    /** Starts state routines whose condition became true and ends those whose condition stopped. */
    private suspend fun evaluateStates(app: PlanrApp, routines: List<Routine>) {
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = LocalDateTime.now()
        routines.forEach { r ->
            val t = r.parsedTrigger
            if (!t.isState) {
                if (prefs.contains(key(r.id))) prefs.edit { remove(key(r.id)) }
                return@forEach
            }
            val condition = conditionTrue(t, now) ?: return@forEach // unknown yet
            val want = r.enabled && condition
            val active = prefs.contains(key(r.id))
            if (want && !active) {
                val snapshot = run(app, r)
                prefs.edit(commit = true) { putString(key(r.id), snapshot.toString()) }
            } else if (!want && active) {
                val snapshot = runCatching { JSONObject(prefs.getString(key(r.id), "{}") ?: "{}") }.getOrNull()
                prefs.edit(commit = true) { remove(key(r.id)) }
                if (r.undoOnEnd && snapshot != null) undo(app, snapshot)
            }
        }
    }

    private fun key(id: Long) = "active:$id"

    /** null = can't tell yet (device state not reported). */
    private fun conditionTrue(t: Trigger, now: LocalDateTime): Boolean? = when (t) {
        is Trigger.Time -> RoutineTime.isInWindow(t, now)
        Trigger.Charging -> if (batteryKnown) charging else null
        is Trigger.BatteryLow -> if (batteryKnown) !charging && batteryPercent <= t.percent else null
        Trigger.Headphones -> if (headphonesKnown) headphones else null
        is Trigger.AppOpened -> foregroundPackage?.let { it == t.packageName }
        else -> false
    }

    /** Runs every action; returns what they replaced, for undo. */
    private suspend fun run(app: PlanrApp, r: Routine): JSONObject {
        val snapshot = JSONObject()
        r.parsedActions.forEach { a -> runCatching { perform(app, a, snapshot) } }
        app.db.routineDao().markRun(r.id, System.currentTimeMillis())
        return snapshot
    }

    private suspend fun perform(app: PlanrApp, a: Action, snap: JSONObject) {
        val nm = app.getSystemService(NotificationManager::class.java)
        val am = app.getSystemService(AudioManager::class.java)
        when (a) {
            is Action.Dnd -> if (nm?.isNotificationPolicyAccessGranted == true) {
                if (!snap.has("dnd")) snap.put("dnd", nm.currentInterruptionFilter)
                nm.setInterruptionFilter(
                    if (a.on) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL
                )
            }
            is Action.Ringer -> if (am != null) {
                if (!snap.has("ringer")) snap.put("ringer", am.ringerMode)
                am.ringerMode = when (a.mode) {
                    RingerMode.SOUND -> AudioManager.RINGER_MODE_NORMAL
                    RingerMode.VIBRATE -> AudioManager.RINGER_MODE_VIBRATE
                    RingerMode.SILENT -> AudioManager.RINGER_MODE_SILENT
                }
            }
            is Action.MediaVolume -> if (am != null) {
                if (!snap.has("media")) snap.put("media", am.getStreamVolume(AudioManager.STREAM_MUSIC))
                val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, max * a.percent / 100, 0)
            }
            is Action.Gateway -> {
                if (!snap.has("gateway")) snap.put("gateway", app.settings.current.gatewayEnabled)
                app.settings.edit { it.copy(gatewayEnabled = a.on) }
            }
            is Action.AlarmGroupSwitch -> {
                val group = app.db.alarmGroupDao().getAll().firstOrNull { it.id == a.groupId } ?: return
                val groups = snap.optJSONObject("groups") ?: JSONObject().also { snap.put("groups", it) }
                if (!groups.has(a.groupId.toString())) groups.put(a.groupId.toString(), group.enabled)
                Alarms.setGroupEnabled(app, a.groupId, a.on)
            }
            is Action.StartOffGrid -> if (!app.offGrid.isLocked()) {
                OffGridManager.start(
                    PlanrAccessibilityService.instance ?: app,
                    a.minutes * 60_000L, emptySet(), allowCamera = true, challenge = UnlockChallenge.TYPING,
                )
            }
            is Action.StartFocus -> if (!app.focus.state.value.active) {
                val blocked = app.db.shieldDao().getAll().filter { it.enabled }.map { it.packageName }.toSet()
                app.focus.start(a.minutes, 5, a.cycles, blocked)
            }
            is Action.Notify -> Notifier.routine(app, a.text)
            Action.TodayBrief -> Notifier.today(app, TodaySummary.text(TodaySummary.load()))
            is Action.OpenApp -> {
                val launch = app.apps.launchIntent(a.packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return
                // Only the accessibility service may open apps from the background.
                val svc = PlanrAccessibilityService.instance
                if (svc != null) svc.startActivity(launch) else Notifier.openApp(app, a.packageName, launch)
            }
        }
    }

    private suspend fun undo(app: PlanrApp, snap: JSONObject) {
        val nm = app.getSystemService(NotificationManager::class.java)
        val am = app.getSystemService(AudioManager::class.java)
        runCatching {
            if (snap.has("dnd") && nm?.isNotificationPolicyAccessGranted == true) nm.setInterruptionFilter(snap.getInt("dnd"))
        }
        runCatching { if (snap.has("ringer")) am?.ringerMode = snap.getInt("ringer") }
        runCatching { if (snap.has("media")) am?.setStreamVolume(AudioManager.STREAM_MUSIC, snap.getInt("media"), 0) }
        runCatching {
            if (snap.has("gateway")) {
                val on = snap.getBoolean("gateway")
                app.settings.edit { it.copy(gatewayEnabled = on) }
            }
        }
        snap.optJSONObject("groups")?.let { groups ->
            groups.keys().forEach { k ->
                k.toLongOrNull()?.let { id -> runCatching { Alarms.setGroupEnabled(app, id, groups.getBoolean(k)) } }
            }
        }
    }
}

/** Wakes the routine engine at time-trigger boundaries (start and, for windows, end). */
object RoutineScheduler {

    suspend fun rescheduleAll(context: Context) {
        val now = LocalDateTime.now()
        PlanrApp.instance.db.routineDao().getAll().forEach { r ->
            val t = r.parsedTrigger
            val at = if (r.enabled && t is Trigger.Time) RoutineTime.nextBoundary(t, now) else null
            val pi = pending(context, r.id)
            if (at == null) {
                context.getSystemService(AlarmManager::class.java)?.cancel(pi)
            } else {
                AlarmScheduler.setExact(context, com.exosynk.planr.alarm.AlarmTime.toMillis(at), pi)
            }
        }
        RoutineEngine.post(RoutineEvent.Evaluate)
    }

    fun cancel(context: Context, id: Long) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pending(context, id))
    }

    private fun pending(context: Context, id: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context, 0,
            Intent(context, RoutineReceiver::class.java)
                .setAction(RoutineReceiver.ACTION_TIME)
                .setData(Uri.parse("planr://routine/$id"))
                .putExtra(RoutineReceiver.EXTRA_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}

class RoutineReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1)
        if (id < 0) return
        when (intent.action) {
            ACTION_TIME -> {
                RoutineEngine.post(RoutineEvent.TimeStart(id))
                val pending = goAsync()
                PlanrApp.instance.appScope.launch {
                    try { RoutineScheduler.rescheduleAll(context) } finally { pending.finish() }
                }
            }
            ACTION_RUN -> RoutineEngine.runNow(id)
        }
    }

    companion object {
        const val ACTION_TIME = "com.exosynk.planr.routine.TIME"
        const val ACTION_RUN = "com.exosynk.planr.routine.RUN"
        const val EXTRA_ID = "id"
    }
}

/** Repository for routine writes (keeps time triggers registered). */
object Routines {
    suspend fun save(context: Context, routine: Routine): Long {
        val rowId = PlanrApp.instance.db.routineDao().upsert(routine)
        RoutineScheduler.rescheduleAll(context)
        return if (routine.id != 0L) routine.id else rowId
    }

    suspend fun delete(context: Context, id: Long) {
        RoutineScheduler.cancel(context, id)
        PlanrApp.instance.db.routineDao().delete(id)
        RoutineEngine.post(RoutineEvent.Evaluate)
    }

    /** Ready-made routines that show how the modules work together. */
    fun templates(): List<Routine> = listOf(
        Routine.of(
            "Good morning",
            Trigger.AlarmDismissed(),
            listOf(Action.Dnd(false), Action.Ringer(RingerMode.SOUND), Action.TodayBrief),
            undo = false,
        ),
        Routine.of(
            "Bedtime",
            Trigger.Time(start = 23 * 60, end = 7 * 60),
            listOf(Action.Dnd(true), Action.Gateway(true), Action.MediaVolume(20)),
        ),
        Routine.of(
            "Desk focus",
            Trigger.Charging,
            listOf(Action.StartFocus(25, 2), Action.Ringer(RingerMode.VIBRATE)),
        ),
        Routine.of(
            "Headphones on",
            Trigger.Headphones,
            listOf(Action.MediaVolume(40)),
        ),
        Routine.of(
            "After Off-Grid",
            Trigger.OffGridEnded,
            listOf(Action.TodayBrief),
            undo = false,
        ),
    )
}
