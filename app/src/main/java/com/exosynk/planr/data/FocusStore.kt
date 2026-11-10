package com.exosynk.planr.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class FocusPhase { FOCUS, BREAK }

data class FocusState(
    val active: Boolean = false,
    val phase: FocusPhase = FocusPhase.FOCUS,
    val phaseEndAt: Long = 0,
    /** 1-based number of the current focus block. */
    val cycle: Int = 1,
    val totalCycles: Int = 4,
    val focusMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val blocked: Set<String> = emptySet(),
    /** Focus minutes completed today (resets on a new day). */
    val minutesToday: Int = 0,
    val todayKey: String = "",
)

/**
 * Pomodoro state. Phases advance lazily: whoever reads it (UI tick or the
 * accessibility service) calls [advance], so no alarm is needed.
 */
class FocusStore(context: Context) {

    private val prefs = context.getSharedPreferences("focus", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<FocusState> = _state.asStateFlow()

    /** Called once for every focus block that runs to completion (argument: its minutes). */
    @Volatile var onFocusBlockDone: ((Int) -> Unit)? = null

    fun start(focusMinutes: Int, breakMinutes: Int, cycles: Int, blocked: Set<String>) {
        val s = rolledToToday(_state.value)
        write(
            s.copy(
                active = true,
                phase = FocusPhase.FOCUS,
                phaseEndAt = System.currentTimeMillis() + focusMinutes * 60_000L,
                cycle = 1,
                totalCycles = cycles.coerceIn(1, 12),
                focusMinutes = focusMinutes,
                breakMinutes = breakMinutes,
                blocked = blocked,
            )
        )
    }

    fun stop() {
        write(_state.value.copy(active = false))
    }

    /** Moves to the next phase(s) if their time is up. Returns the current state. */
    @Synchronized
    fun advance(now: Long = System.currentTimeMillis()): FocusState {
        var s = rolledToToday(_state.value)
        var changed = s != _state.value
        val finishedBlocks = mutableListOf<Int>()
        while (s.active && now >= s.phaseEndAt) {
            changed = true
            s = when (s.phase) {
                FocusPhase.FOCUS -> {
                    finishedBlocks += s.focusMinutes
                    val done = s.copy(minutesToday = s.minutesToday + s.focusMinutes)
                    if (s.cycle >= s.totalCycles) done.copy(active = false)
                    else done.copy(
                        phase = FocusPhase.BREAK,
                        phaseEndAt = s.phaseEndAt + s.breakMinutes * 60_000L,
                    )
                }
                FocusPhase.BREAK -> s.copy(
                    phase = FocusPhase.FOCUS,
                    cycle = s.cycle + 1,
                    phaseEndAt = s.phaseEndAt + s.focusMinutes * 60_000L,
                )
            }
        }
        if (changed) write(s)
        finishedBlocks.forEach { onFocusBlockDone?.invoke(it) }
        return s
    }

    /** True while a focus block (not a break) is running and [pkg] is on its list. */
    fun blocks(pkg: String): Boolean {
        val s = advance()
        return s.active && s.phase == FocusPhase.FOCUS && pkg in s.blocked
    }

    private fun rolledToToday(s: FocusState): FocusState {
        val today = java.time.LocalDate.now().toString()
        return if (s.todayKey == today) s else s.copy(todayKey = today, minutesToday = 0)
    }

    private fun write(s: FocusState) {
        prefs.edit {
            putBoolean("active", s.active)
            putString("phase", s.phase.name)
            putLong("phaseEnd", s.phaseEndAt)
            putInt("cycle", s.cycle)
            putInt("cycles", s.totalCycles)
            putInt("focus", s.focusMinutes)
            putInt("break", s.breakMinutes)
            putStringSet("blocked", s.blocked)
            putInt("today", s.minutesToday)
            putString("todayKey", s.todayKey)
        }
        _state.value = s
    }

    private fun read() = FocusState(
        active = prefs.getBoolean("active", false),
        phase = runCatching { FocusPhase.valueOf(prefs.getString("phase", null) ?: "") }
            .getOrDefault(FocusPhase.FOCUS),
        phaseEndAt = prefs.getLong("phaseEnd", 0),
        cycle = prefs.getInt("cycle", 1),
        totalCycles = prefs.getInt("cycles", 4),
        focusMinutes = prefs.getInt("focus", 25),
        breakMinutes = prefs.getInt("break", 5),
        blocked = prefs.getStringSet("blocked", emptySet())?.toSet() ?: emptySet(),
        minutesToday = prefs.getInt("today", 0),
        todayKey = prefs.getString("todayKey", "") ?: "",
    )
}
