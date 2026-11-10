package com.exosynk.planr.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ProgressState(
    val stats: ProgressStats = ProgressStats(),
    /** Achievement name → epoch millis it was unlocked. */
    val unlocked: Map<String, Long> = emptyMap(),
)

/** XP, levels and achievements. Small and synchronous, so SharedPreferences. */
class ProgressStore(context: Context) {

    private val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<ProgressState> = _state.asStateFlow()

    /** Called with achievements that were just unlocked (e.g. to post a notification). */
    @Volatile var onUnlocked: ((List<Achievement>) -> Unit)? = null

    @Synchronized
    fun update(transform: (ProgressStats) -> ProgressStats) {
        val cur = _state.value
        val stats = transform(cur.stats)
        val fresh = Progression.newlyUnlocked(stats, cur.unlocked.keys)
        val now = System.currentTimeMillis()
        val next = ProgressState(stats, cur.unlocked + fresh.associate { it.name to now })
        write(next)
        if (fresh.isNotEmpty()) onUnlocked?.invoke(fresh)
    }

    /** Replaces everything (used by backup restore). */
    @Synchronized
    fun restore(state: ProgressState) = write(state)

    private fun write(s: ProgressState) {
        val st = s.stats
        prefs.edit(commit = true) {
            putInt("xp", st.xp)
            putInt("ogDone", st.offGridCompleted)
            putInt("ogMin", st.offGridMinutes)
            putInt("ogLongest", st.longestOffGridMinutes)
            putInt("ogSched", st.scheduledOffGridCompleted)
            putInt("streak", st.currentStreak)
            putInt("bestStreak", st.bestStreak)
            putInt("focusBlocks", st.focusBlocks)
            putInt("focusMin", st.focusMinutes)
            putInt("resists", st.resists)
            putStringSet("unlocked", s.unlocked.map { (k, v) -> "$k@$v" }.toSet())
        }
        _state.value = s
    }

    private fun read(): ProgressState {
        val stats = ProgressStats(
            xp = prefs.getInt("xp", 0),
            offGridCompleted = prefs.getInt("ogDone", 0),
            offGridMinutes = prefs.getInt("ogMin", 0),
            longestOffGridMinutes = prefs.getInt("ogLongest", 0),
            scheduledOffGridCompleted = prefs.getInt("ogSched", 0),
            currentStreak = prefs.getInt("streak", 0),
            bestStreak = prefs.getInt("bestStreak", 0),
            focusBlocks = prefs.getInt("focusBlocks", 0),
            focusMinutes = prefs.getInt("focusMin", 0),
            resists = prefs.getInt("resists", 0),
        )
        val unlocked = prefs.getStringSet("unlocked", emptySet()).orEmpty().mapNotNull { entry ->
            val name = entry.substringBefore('@')
            val at = entry.substringAfter('@', "0").toLongOrNull() ?: 0L
            name.takeIf { n -> Achievement.entries.any { it.name == n } }?.let { it to at }
        }.toMap()
        return ProgressState(stats, unlocked)
    }
}
