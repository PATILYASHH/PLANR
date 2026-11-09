package com.exosynk.planr.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class UnlockChallenge(val title: String, val description: String) {
    TYPING("Typing challenge", "Retype a long passage exactly to unlock early"),
    COOLDOWN("Cooldown", "Request unlock, wait 10 minutes, then confirm"),
    MATH("Math", "Solve 10 multiplication problems"),
}

data class OffGridState(
    val active: Boolean = false,
    val sessionId: Long = 0,
    val startAt: Long = 0,
    val endAt: Long = 0,
    /** Extra apps the user chose to keep (dialer and camera are always added separately). */
    val extraAllowed: Set<String> = emptySet(),
    val allowCamera: Boolean = true,
    val challenge: UnlockChallenge = UnlockChallenge.TYPING,
    /** For COOLDOWN: when the user requested an early unlock (0 = not requested). */
    val cooldownRequestedAt: Long = 0,
)

/**
 * Off-Grid state lives in SharedPreferences, not Room, because the accessibility
 * service needs a synchronous answer on every window change and the state must
 * survive process death and reboot.
 */
class OffGridStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("offgrid", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<OffGridState> = _state.asStateFlow()

    /** Current state; also reports inactive once the end time has passed. */
    fun current(now: Long = System.currentTimeMillis()): OffGridState {
        val s = _state.value
        return if (s.active && now >= s.endAt) s.copy(active = false) else s
    }

    fun isLocked(now: Long = System.currentTimeMillis()): Boolean = current(now).active

    fun start(
        sessionId: Long,
        durationMs: Long,
        extraAllowed: Set<String>,
        allowCamera: Boolean,
        challenge: UnlockChallenge,
    ) {
        val now = System.currentTimeMillis()
        write(
            OffGridState(
                active = true,
                sessionId = sessionId,
                startAt = now,
                endAt = now + durationMs.coerceIn(MIN_DURATION_MS, MAX_DURATION_MS),
                extraAllowed = extraAllowed,
                allowCamera = allowCamera,
                challenge = challenge,
            )
        )
    }

    fun requestCooldown() {
        write(_state.value.copy(cooldownRequestedAt = System.currentTimeMillis()))
    }

    fun clear() {
        write(OffGridState())
    }

    private fun write(s: OffGridState) {
        prefs.edit(commit = true) {
            putBoolean(K_ACTIVE, s.active)
            putLong(K_SESSION, s.sessionId)
            putLong(K_START, s.startAt)
            putLong(K_END, s.endAt)
            putStringSet(K_EXTRA, s.extraAllowed)
            putBoolean(K_CAMERA, s.allowCamera)
            putString(K_CHALLENGE, s.challenge.name)
            putLong(K_COOLDOWN, s.cooldownRequestedAt)
        }
        _state.value = s
    }

    private fun read(): OffGridState = OffGridState(
        active = prefs.getBoolean(K_ACTIVE, false),
        sessionId = prefs.getLong(K_SESSION, 0),
        startAt = prefs.getLong(K_START, 0),
        endAt = prefs.getLong(K_END, 0),
        extraAllowed = prefs.getStringSet(K_EXTRA, emptySet())?.toSet() ?: emptySet(),
        allowCamera = prefs.getBoolean(K_CAMERA, true),
        challenge = runCatching {
            UnlockChallenge.valueOf(prefs.getString(K_CHALLENGE, null) ?: "")
        }.getOrDefault(UnlockChallenge.TYPING),
        cooldownRequestedAt = prefs.getLong(K_COOLDOWN, 0),
    )

    companion object {
        const val MIN_DURATION_MS = 5 * 60_000L
        const val MAX_DURATION_MS = 24 * 60 * 60_000L
        const val COOLDOWN_MS = 10 * 60_000L
        const val MAX_EXTRA_APPS = 5

        private const val K_ACTIVE = "active"
        private const val K_SESSION = "session"
        private const val K_START = "start"
        private const val K_END = "end"
        private const val K_EXTRA = "extra"
        private const val K_CAMERA = "camera"
        private const val K_CHALLENGE = "challenge"
        private const val K_COOLDOWN = "cooldown"
    }
}

/** Temporary "Continue" passes granted from the shield screen (in-memory). */
object PassStore {
    private val passes = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun grant(packageName: String, minutes: Int) {
        passes[packageName] = System.currentTimeMillis() + minutes.coerceAtLeast(1) * 60_000L
    }

    fun hasPass(packageName: String): Boolean {
        val until = passes[packageName] ?: return false
        if (System.currentTimeMillis() < until) return true
        passes.remove(packageName)
        return false
    }
}
