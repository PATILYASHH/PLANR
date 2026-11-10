package com.exosynk.planr.alarm

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.exosynk.planr.PlanrApp
import com.exosynk.planr.R
import com.exosynk.planr.data.Progression
import com.exosynk.planr.engine.Notifier
import com.exosynk.planr.engine.PlanrAccessibilityService
import com.exosynk.planr.reminder.Reminders
import com.exosynk.planr.routine.RoutineEngine
import com.exosynk.planr.routine.RoutineEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Receives AlarmManager fires and notification buttons, and hands them to [AlarmService]. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FIRE -> {
                val id = intent.getLongExtra(EXTRA_ID, -1)
                if (id < 0) return
                AlarmService.start(context, AlarmService.Kind.ALARM, id, snooze = intent.getBooleanExtra(EXTRA_SNOOZE, false))
            }
            ACTION_TEST -> AlarmService.start(context, AlarmService.Kind.TEST, 0L)
            ACTION_CANCEL_SNOOZE -> {
                val id = intent.getLongExtra(EXTRA_ID, -1)
                if (id < 0) return
                AlarmScheduler.cancelSnooze(context, id)
                AlarmScheduler.resetSnoozes(context, id)
                context.getSystemService(NotificationManager::class.java)?.cancel(Notifier.snoozedId(id))
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.exosynk.planr.alarm.FIRE"
        const val ACTION_CANCEL_SNOOZE = "com.exosynk.planr.alarm.CANCEL_SNOOZE"
        const val ACTION_TEST = "com.exosynk.planr.alarm.TEST"
        const val EXTRA_ID = "id"
        const val EXTRA_SNOOZE = "snooze"
    }
}

/**
 * Rings an alarm or an alarm-style reminder: sound on the alarm stream (per-alarm
 * volume, optional fade-in), vibration pattern, optional read-aloud, a full-screen
 * notification, snooze/dismiss and a 5-minute auto-silence.
 */
class AlarmService : Service() {

    /** TEST = the "Test alarm" button: rings with default settings, nothing saved. */
    enum class Kind { ALARM, REMINDER, TEST }

    data class Ring(
        val kind: Kind,
        val id: Long,
        val title: String,
        val subtitle: String,
        val canSnooze: Boolean,
        val snoozeMinutes: Int,
        val snoozesLeft: Int?, // null = unlimited
        val challenge: DismissChallenge,
        val morningBrief: Boolean,
        /** Reminders linked to a contact show a Call / Message button. */
        val contact: com.exosynk.planr.reminder.ReminderContact? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var player: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var vibrator: Vibrator? = null
    private var savedAlarmVolume = -1
    private var rampJob: Job? = null
    private var timeoutJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RING -> {
                goForeground(placeholder())
                val kind = runCatching { Kind.valueOf(intent.getStringExtra(EXTRA_KIND) ?: "") }.getOrDefault(Kind.ALARM)
                val id = intent.getLongExtra(EXTRA_ID, -1)
                val snooze = intent.getBooleanExtra(EXTRA_SNOOZE, false)
                // A new ring replaces the current one; the current one is snoozed if it can be.
                _ringing.value?.let { current ->
                    if (current.canSnooze) snooze(current, quiet = true) else dismiss(current, missed = true, quiet = true)
                }
                scope.launch { ring(kind, id, snooze) }
            }
            ACTION_SNOOZE -> _ringing.value?.let { snooze(it) } ?: stopIfIdle()
            ACTION_DISMISS -> _ringing.value?.let { dismiss(it) } ?: stopIfIdle()
            else -> stopIfIdle()
        }
        return START_NOT_STICKY
    }

    private suspend fun ring(kind: Kind, id: Long, isSnooze: Boolean) {
        val app = PlanrApp.instance
        val ring: Ring
        when (kind) {
            Kind.ALARM -> {
                val alarm = app.db.alarmDao().get(id) ?: return stopIfIdle()
                if (!isSnooze) {
                    AlarmScheduler.resetSnoozes(this, id)
                    // One-time alarms switch themselves off once they ring (snoozes still work).
                    if (!alarm.isRepeating) app.db.alarmDao().setEnabled(id, false)
                    if (alarm.skippedAt != 0L && alarm.skippedAt < System.currentTimeMillis()) {
                        app.db.alarmDao().upsert(alarm.copy(enabled = alarm.isRepeating, skippedAt = 0))
                    }
                    AlarmScheduler.rescheduleAll(this)
                }
                val used = AlarmScheduler.snoozesUsed(this, id)
                val left = if (alarm.snoozeRepeat == 0) null else (alarm.snoozeRepeat - used).coerceAtLeast(0)
                ring = Ring(
                    kind = kind,
                    id = id,
                    title = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date()),
                    subtitle = alarm.label.ifBlank { "Alarm" },
                    canSnooze = alarm.snoozeEnabled && (left == null || left > 0),
                    snoozeMinutes = alarm.snoozeMinutes,
                    snoozesLeft = left,
                    challenge = alarm.challenge,
                    morningBrief = alarm.morningBrief,
                )
                val speech = if (alarm.readAloud) {
                    "It's ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())}." +
                        (if (alarm.label.isNotBlank()) " ${alarm.label}." else "")
                } else null
                show(ring)
                startAudio(alarm.sound, alarm.volume, alarm.gradualVolume, alarm.vibrate, alarm.pattern.timings, speech)
            }
            Kind.TEST -> {
                ring = Ring(
                    kind = kind,
                    id = 0,
                    title = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date()),
                    subtitle = "Test alarm",
                    canSnooze = false,
                    snoozeMinutes = 5,
                    snoozesLeft = null,
                    challenge = DismissChallenge.NONE,
                    morningBrief = false,
                )
                show(ring)
                startAudio(null, 60, gradual = false, vibrate = true, pattern = VibrationPattern.BASIC.timings, speech = null)
            }
            Kind.REMINDER -> {
                val r = app.db.reminderDao().get(id) ?: return stopIfIdle()
                if (r.completed) return stopIfIdle()
                ring = Ring(
                    kind = kind,
                    id = id,
                    title = r.title,
                    subtitle = r.notes.ifBlank { "Reminder" },
                    canSnooze = true,
                    snoozeMinutes = Reminders.SNOOZE_MINUTES,
                    snoozesLeft = null,
                    challenge = DismissChallenge.NONE,
                    morningBrief = false,
                    contact = r.contact,
                )
                show(ring)
                startAudio(null, 70, gradual = true, vibrate = true, pattern = VibrationPattern.BASIC.timings, speech = r.title)
            }
        }
        timeoutJob = scope.launch {
            delay(RING_TIMEOUT_MS)
            _ringing.value?.let { if (it.canSnooze) snooze(it) else dismiss(it, missed = true) }
        }
    }

    private fun show(ring: Ring) {
        _ringing.value = ring
        _briefFor.value = null
        holdRingWakeLock()
        goForeground(ringNotification(ring))
        wakeScreen()
        launchRingScreen()
        releaseStartWakeLock()
    }

    /**
     * Opens the full-screen alarm right away. The notification's full-screen intent does
     * this when the phone is locked or the screen is off; launching directly also covers
     * an unlocked phone in use, and phones where the full-screen intent is denied.
     * Android allows the direct launch when "Display over other apps" is granted or the
     * PLANR accessibility service is on.
     */
    private fun launchRingScreen() {
        val canLaunch = Settings.canDrawOverlays(this)
        val svc = PlanrAccessibilityService.instance
        runCatching {
            when {
                canLaunch -> startActivity(AlarmRingActivity.intent(this))
                svc != null -> svc.startActivity(AlarmRingActivity.intent(svc))
            }
        }
    }

    /** Turns the screen on for the alarm (the ring screen then keeps it on). */
    @Suppress("DEPRECATION")
    private fun wakeScreen() {
        val pm = getSystemService(PowerManager::class.java) ?: return
        if (pm.isInteractive) return
        runCatching {
            pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "planr:alarm-screen",
            ).acquire(15_000L)
        }
    }

    private var ringWakeLock: PowerManager.WakeLock? = null

    /** Keeps the CPU awake while ringing so the timeout and fade-in run on time. */
    private fun holdRingWakeLock() {
        if (ringWakeLock?.isHeld == true) return
        ringWakeLock = getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "planr:alarm-ring")
            ?.apply { setReferenceCounted(false); acquire(RING_TIMEOUT_MS + 60_000L) }
    }

    // ---- actions ----

    /** [quiet]: another ring is taking over, so keep the service (and its notification) alive. */
    private fun snooze(ring: Ring, quiet: Boolean = false) {
        stopRinging()
        val at = System.currentTimeMillis() + ring.snoozeMinutes * 60_000L
        when (ring.kind) {
            Kind.ALARM -> {
                AlarmScheduler.addSnooze(this, ring.id)
                AlarmScheduler.scheduleSnooze(this, ring.id, at)
                Notifier.alarmSnoozed(this, ring.id, ring.subtitle, at)
            }
            Kind.REMINDER -> PlanrApp.instance.appScope.launch { Reminders.snooze(applicationContext, ring.id, ring.snoozeMinutes) }
            Kind.TEST -> Unit
        }
        if (quiet) _ringing.value = null else finishRing()
    }

    private fun dismiss(ring: Ring, missed: Boolean = false, quiet: Boolean = false) {
        stopRinging()
        val app = PlanrApp.instance
        when (ring.kind) {
            Kind.ALARM -> {
                val noSnooze = AlarmScheduler.snoozesUsed(this, ring.id) == 0
                AlarmScheduler.cancelSnooze(this, ring.id)
                AlarmScheduler.resetSnoozes(this, ring.id)
                getSystemService(NotificationManager::class.java)?.cancel(Notifier.snoozedId(ring.id))
                if (missed) {
                    Notifier.missedAlarm(this, ring.subtitle)
                } else {
                    app.progress.update { Progression.wokeUp(it, noSnooze) }
                    RoutineEngine.post(RoutineEvent.AlarmDismissed(ring.id))
                    if (ring.morningBrief) _briefFor.value = ring.id
                }
                app.appScope.launch { AlarmScheduler.rescheduleAll(applicationContext) }
            }
            Kind.REMINDER -> if (!missed) {
                app.appScope.launch { Reminders.complete(applicationContext, ring.id) }
            }
            Kind.TEST -> Unit
        }
        if (quiet) _ringing.value = null else finishRing()
    }

    private fun finishRing() {
        _ringing.value = null
        stopIfIdle()
    }

    private fun stopIfIdle() {
        if (_ringing.value != null) return
        releaseStartWakeLock()
        ringWakeLock?.let { if (it.isHeld) it.release() }
        ringWakeLock = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ---- audio ----

    private val alarmAttrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private fun startAudio(
        sound: String?,
        volume: Int,
        gradual: Boolean,
        vibrate: Boolean,
        pattern: LongArray,
        speech: String?,
    ) {
        stopRinging()
        val am = getSystemService(AudioManager::class.java)
        if (am != null && sound != Alarm.SILENT) {
            savedAlarmVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
            val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val target = (max * volume.coerceIn(0, 100) / 100f).toInt().coerceIn(1, max)
            runCatching { am.setStreamVolume(AudioManager.STREAM_ALARM, target, 0) }
        }
        if (vibrate) startVibration(pattern)
        val startSound = { if (sound != Alarm.SILENT) startPlayer(sound, gradual) }
        if (speech != null) speakThen(speech, startSound) else startSound()
    }

    private fun startPlayer(sound: String?, gradual: Boolean) {
        if (player != null) return
        val candidates = listOfNotNull(
            sound?.let { Uri.parse(it) },
            RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM),
            Settings.System.DEFAULT_ALARM_ALERT_URI,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
        )
        for (uri in candidates) {
            val mp = MediaPlayer()
            val ok = runCatching {
                mp.setAudioAttributes(alarmAttrs)
                mp.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK)
                mp.setDataSource(this, uri)
                mp.isLooping = true
                mp.prepare()
                mp.setVolume(if (gradual) MIN_FADE else 1f, if (gradual) MIN_FADE else 1f)
                mp.start()
            }.isSuccess
            if (ok) {
                player = mp
                break
            }
            mp.release()
        }
        if (gradual) {
            rampJob = scope.launch {
                var v = MIN_FADE
                while (v < 1f) {
                    delay(1_000)
                    v = (v + (1f - MIN_FADE) / FADE_SECONDS).coerceAtMost(1f)
                    runCatching { player?.setVolume(v, v) }
                }
            }
        }
    }

    private fun speakThen(text: String, then: () -> Unit) {
        var started = false
        val go = { scope.launch { if (!started) { started = true; then() } } }
        tts = TextToSpeech(this) { status ->
            val engine = tts
            if (status != TextToSpeech.SUCCESS || engine == null) {
                go()
                return@TextToSpeech
            }
            engine.setAudioAttributes(alarmAttrs)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) { go() }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { go() }
            })
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "planr-ring")
        }
        scope.launch {
            delay(10_000) // never let a slow or missing TTS engine keep the alarm silent
            go()
        }
    }

    @Suppress("DEPRECATION")
    private fun startVibration(pattern: LongArray) {
        val v = if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            getSystemService(Vibrator::class.java)
        }
        vibrator = v
        runCatching { v?.vibrate(VibrationEffect.createWaveform(pattern, 0), alarmAttrs) }
    }

    private fun stopRinging() {
        timeoutJob?.cancel()
        rampJob?.cancel()
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        tts?.let { runCatching { it.stop() }; it.shutdown() }
        tts = null
        vibrator?.cancel()
        vibrator = null
        if (savedAlarmVolume >= 0) {
            runCatching {
                getSystemService(AudioManager::class.java)?.setStreamVolume(AudioManager.STREAM_ALARM, savedAlarmVolume, 0)
            }
            savedAlarmVolume = -1
        }
    }

    // ---- notification ----

    private fun goForeground(n: Notification) {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED else 0
        runCatching { ServiceCompat.startForeground(this, Notifier.RINGING_ID, n, type) }
    }

    private fun placeholder(): Notification =
        NotificationCompat.Builder(this, Notifier.CH_ALARMS)
            .setSmallIcon(R.drawable.ic_stat_planr)
            .setContentTitle("Alarm")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setSilent(true)
            .build()

    private fun ringNotification(ring: Ring): Notification {
        val full = PendingIntent.getActivity(
            this, 0, AlarmRingActivity.intent(this),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = NotificationCompat.Builder(this, Notifier.CH_ALARMS)
            .setSmallIcon(R.drawable.ic_stat_planr)
            .setContentTitle(if (ring.kind == Kind.REMINDER) ring.title else ring.subtitle)
            .setContentText(if (ring.kind == Kind.REMINDER) ring.subtitle else ring.title)
            .setCategory(if (ring.kind == Kind.REMINDER) NotificationCompat.CATEGORY_REMINDER else NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setSilent(true) // the service plays the sound itself
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
        if (ring.canSnooze) {
            b.addAction(0, "Snooze", servicePending(this, ACTION_SNOOZE, 2))
        }
        if (ring.challenge == DismissChallenge.NONE) {
            b.addAction(0, if (ring.kind == Kind.REMINDER) "Complete" else "Dismiss", servicePending(this, ACTION_DISMISS, 3))
        }
        return b.build()
    }

    override fun onDestroy() {
        stopRinging()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_RING = "com.exosynk.planr.alarm.RING"
        const val ACTION_SNOOZE = "com.exosynk.planr.alarm.SNOOZE"
        const val ACTION_DISMISS = "com.exosynk.planr.alarm.DISMISS"
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_ID = "id"
        private const val EXTRA_SNOOZE = "snooze"

        const val RING_TIMEOUT_MS = 5 * 60_000L
        private const val MIN_FADE = 0.15f
        private const val FADE_SECONDS = 30f

        private val _ringing = MutableStateFlow<Ring?>(null)
        /** What is ringing right now (observed by [AlarmRingActivity]). */
        val ringing: StateFlow<Ring?> = _ringing.asStateFlow()

        private val _briefFor = MutableStateFlow<Long?>(null)
        /** Alarm id whose morning brief should be shown after dismissing. */
        val briefFor: StateFlow<Long?> = _briefFor.asStateFlow()

        fun clearBrief() {
            _briefFor.value = null
        }

        private var startWakeLock: PowerManager.WakeLock? = null

        /**
         * AlarmManager only keeps the phone awake during onReceive, so hold a wake lock
         * until the service has the alarm on screen (or gives up).
         */
        private fun acquireStartWakeLock(context: Context) {
            synchronized(this) {
                if (startWakeLock?.isHeld == true) return
                startWakeLock = context.getSystemService(PowerManager::class.java)
                    ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "planr:alarm-start")
                    ?.apply { setReferenceCounted(false); acquire(60_000L) }
            }
        }

        private fun releaseStartWakeLock() {
            synchronized(this) {
                startWakeLock?.let { if (it.isHeld) it.release() }
                startWakeLock = null
            }
        }

        /** Rings the test alarm in [seconds] (exact, like a real alarm). */
        fun scheduleTest(context: Context, seconds: Int = 10) {
            val pi = android.app.PendingIntent.getBroadcast(
                context, 0,
                Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_TEST)
                    .setData(android.net.Uri.parse("planr://alarm/test")),
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
            )
            AlarmScheduler.setClock(context, System.currentTimeMillis() + seconds * 1000L, pi)
        }

        fun start(context: Context, kind: Kind, id: Long, snooze: Boolean = false) {
            acquireStartWakeLock(context)
            val i = Intent(context, AlarmService::class.java)
                .setAction(ACTION_RING)
                .putExtra(EXTRA_KIND, kind.name)
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_SNOOZE, snooze)
            runCatching { ContextCompat.startForegroundService(context, i) }
        }

        fun send(context: Context, action: String) {
            runCatching { context.startService(Intent(context, AlarmService::class.java).setAction(action)) }
        }

        private fun servicePending(context: Context, action: String, code: Int): PendingIntent =
            PendingIntent.getService(
                context, code, Intent(context, AlarmService::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
