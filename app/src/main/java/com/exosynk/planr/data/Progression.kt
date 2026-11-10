package com.exosynk.planr.data

import kotlin.math.sqrt

/** Lifetime totals that drive XP, levels and achievements. */
data class ProgressStats(
    val xp: Int = 0,
    val offGridCompleted: Int = 0,
    val offGridMinutes: Int = 0,
    val longestOffGridMinutes: Int = 0,
    val scheduledOffGridCompleted: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val focusBlocks: Int = 0,
    val focusMinutes: Int = 0,
    /** Times the user chose "Close" on a shield or gateway pause. */
    val resists: Int = 0,
) {
    val level: Int get() = Progression.levelFor(xp)
}

enum class Achievement(val title: String, val description: String, val check: (ProgressStats) -> Boolean) {
    FIRST_OFFGRID("Unplugged", "Complete your first Off-Grid session", { it.offGridCompleted >= 1 }),
    STREAK_5("On a roll", "Complete 5 Off-Grid sessions in a row", { it.bestStreak >= 5 }),
    STREAK_20("Unbreakable", "Complete 20 Off-Grid sessions in a row", { it.bestStreak >= 20 }),
    MARATHON("Marathon", "Finish an Off-Grid session of 8 hours or more", { it.longestOffGridMinutes >= 480 }),
    BEDTIME("Sleep hygiene", "Complete a scheduled Off-Grid window", { it.scheduledOffGridCompleted >= 1 }),
    FIRST_FOCUS("Deep work", "Complete a focus block", { it.focusBlocks >= 1 }),
    FOCUS_10H("Ten hours in", "Focus for 10 hours in total", { it.focusMinutes >= 600 }),
    FOCUS_50H("Flow state", "Focus for 50 hours in total", { it.focusMinutes >= 3000 }),
    RESIST_10("Not today", "Close a shielded app 10 times", { it.resists >= 10 }),
    RESIST_100("Iron will", "Close a shielded app 100 times", { it.resists >= 100 }),
    LEVEL_5("Level 5", "Reach level 5", { it.level >= 5 }),
    LEVEL_10("Level 10", "Reach level 10", { it.level >= 10 }),
}

/** Pure XP / level math, kept free of Android types so it is unit-tested. */
object Progression {

    const val XP_PER_OFFGRID_MINUTE = 1
    const val XP_PER_FOCUS_MINUTE = 1
    const val XP_PER_RESIST = 5
    const val XP_STREAK_BONUS = 10

    /** Total XP needed to reach [level]: 0, 100, 300, 600, 1000, … */
    fun xpForLevel(level: Int): Int = 50 * level * (level - 1)

    fun levelFor(xp: Int): Int {
        if (xp <= 0) return 1
        // Solve 50·L·(L−1) ≤ xp for the largest integer L.
        var l = ((1 + sqrt(1 + 4.0 * xp / 50)) / 2).toInt().coerceAtLeast(1)
        while (xpForLevel(l + 1) <= xp) l++
        while (l > 1 && xpForLevel(l) > xp) l--
        return l
    }

    /** Progress through the current level, 0f..1f. */
    fun levelProgress(xp: Int): Float {
        val l = levelFor(xp)
        val from = xpForLevel(l)
        val to = xpForLevel(l + 1)
        return ((xp - from).toFloat() / (to - from)).coerceIn(0f, 1f)
    }

    fun offGridFinished(
        s: ProgressStats,
        completed: Boolean,
        minutes: Int,
        scheduled: Boolean,
    ): ProgressStats {
        if (!completed) return s.copy(currentStreak = 0)
        val streak = s.currentStreak + 1
        val bonus = if (streak % 5 == 0) XP_STREAK_BONUS * (streak / 5) else 0
        return s.copy(
            xp = s.xp + minutes * XP_PER_OFFGRID_MINUTE + bonus,
            offGridCompleted = s.offGridCompleted + 1,
            offGridMinutes = s.offGridMinutes + minutes,
            longestOffGridMinutes = maxOf(s.longestOffGridMinutes, minutes),
            scheduledOffGridCompleted = s.scheduledOffGridCompleted + if (scheduled) 1 else 0,
            currentStreak = streak,
            bestStreak = maxOf(s.bestStreak, streak),
        )
    }

    fun focusBlockDone(s: ProgressStats, minutes: Int): ProgressStats = s.copy(
        xp = s.xp + minutes * XP_PER_FOCUS_MINUTE,
        focusBlocks = s.focusBlocks + 1,
        focusMinutes = s.focusMinutes + minutes,
    )

    fun resisted(s: ProgressStats): ProgressStats =
        s.copy(xp = s.xp + XP_PER_RESIST, resists = s.resists + 1)

    /** Achievements earned by [s] that aren't in [already]. */
    fun newlyUnlocked(s: ProgressStats, already: Set<String>): List<Achievement> =
        Achievement.entries.filter { it.name !in already && it.check(s) }
}
