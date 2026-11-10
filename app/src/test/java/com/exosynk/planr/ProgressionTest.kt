package com.exosynk.planr

import com.exosynk.planr.data.Achievement
import com.exosynk.planr.data.ProgressStats
import com.exosynk.planr.data.Progression
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionTest {

    @Test fun levelThresholds() {
        assertEquals(1, Progression.levelFor(0))
        assertEquals(1, Progression.levelFor(99))
        assertEquals(2, Progression.levelFor(100))
        assertEquals(2, Progression.levelFor(299))
        assertEquals(3, Progression.levelFor(300))
        assertEquals(5, Progression.levelFor(1000))
        assertEquals(10, Progression.levelFor(4500))
        assertEquals(1, Progression.levelFor(-5))
    }

    @Test fun levelIsConsistentWithThresholds() {
        for (xp in 0..20_000 step 7) {
            val l = Progression.levelFor(xp)
            assertTrue(Progression.xpForLevel(l) <= xp)
            assertTrue(Progression.xpForLevel(l + 1) > xp)
        }
    }

    @Test fun levelProgressMidway() {
        assertEquals(0.5f, Progression.levelProgress(200), 0.001f) // L2: 100..300
    }

    @Test fun completedOffGridAwardsXpAndStreak() {
        var s = ProgressStats()
        s = Progression.offGridFinished(s, completed = true, minutes = 60, scheduled = true)
        assertEquals(60, s.xp)
        assertEquals(1, s.currentStreak)
        assertEquals(1, s.scheduledOffGridCompleted)
        assertEquals(60, s.longestOffGridMinutes)
    }

    @Test fun earlyExitBreaksStreakButKeepsBest() {
        var s = ProgressStats()
        repeat(3) { s = Progression.offGridFinished(s, true, 30, false) }
        s = Progression.offGridFinished(s, completed = false, minutes = 30, scheduled = false)
        assertEquals(0, s.currentStreak)
        assertEquals(3, s.bestStreak)
        assertEquals(90, s.xp)
    }

    @Test fun everyFifthSessionGivesStreakBonus() {
        var s = ProgressStats()
        repeat(5) { s = Progression.offGridFinished(s, true, 10, false) }
        assertEquals(5 * 10 + Progression.XP_STREAK_BONUS, s.xp)
    }

    @Test fun achievementsUnlockOnce() {
        var s = ProgressStats()
        s = Progression.offGridFinished(s, true, 500, false)
        val first = Progression.newlyUnlocked(s, emptySet())
        assertTrue(Achievement.FIRST_OFFGRID in first)
        assertTrue(Achievement.MARATHON in first)
        val again = Progression.newlyUnlocked(s, first.map { it.name }.toSet())
        assertTrue(again.isEmpty())
    }

    @Test fun resistsAndFocus() {
        var s = ProgressStats()
        repeat(10) { s = Progression.resisted(s) }
        s = Progression.focusBlockDone(s, 25)
        assertEquals(10 * Progression.XP_PER_RESIST + 25, s.xp)
        val unlocked = Progression.newlyUnlocked(s, emptySet())
        assertTrue(Achievement.RESIST_10 in unlocked)
        assertTrue(Achievement.FIRST_FOCUS in unlocked)
    }
}
