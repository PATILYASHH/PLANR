package com.exosynk.planr

import com.exosynk.planr.data.PlanrSettings
import com.exosynk.planr.data.db.ShieldRule
import com.exosynk.planr.data.db.wouldLoosen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class LockdownTest {

    // 2026-11-09 is a Monday
    private fun at(day: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, day, h, m)

    private val work = PlanrSettings(lockdownEnabled = true, lockdownStart = 9 * 60, lockdownEnd = 17 * 60)

    @Test fun activeOnlyInWindowOnWeekdays() {
        assertTrue(work.isLockdown(at(9, 10)))
        assertFalse(work.isLockdown(at(9, 17)))
        assertFalse(work.isLockdown(at(14, 10))) // Saturday
        assertFalse(work.copy(lockdownEnabled = false).isLockdown(at(9, 10)))
    }

    @Test fun sameStartAndEndNeverLocksForever() {
        assertFalse(work.copy(lockdownStart = 600, lockdownEnd = 600).isLockdown(at(9, 12)))
    }

    @Test fun endTime() {
        assertEquals(at(9, 17), work.lockdownEndsAt(at(9, 10)))
        assertNull(work.lockdownEndsAt(at(9, 18)))
    }

    @Test fun onlyStricterSettingsAllowed() {
        val s = work.copy(gatewayEnabled = true, emergencyQuota = 3, gatewayWhitelist = setOf("a"))
        assertTrue(s.isAllowedDuringLockdown(s.copy(emergencyQuota = 1)))
        assertTrue(s.isAllowedDuringLockdown(s.copy(gatewayWhitelist = emptySet())))
        assertTrue(s.isAllowedDuringLockdown(s.copy(eyeCareEnabled = true)))
        assertFalse(s.isAllowedDuringLockdown(s.copy(emergencyQuota = 4)))
        assertFalse(s.isAllowedDuringLockdown(s.copy(gatewayEnabled = false)))
        assertFalse(s.isAllowedDuringLockdown(s.copy(gatewayWhitelist = setOf("a", "b"))))
        assertFalse(s.isAllowedDuringLockdown(s.copy(lockdownEnabled = false)))
        assertFalse(s.isAllowedDuringLockdown(s.copy(lockdownEnd = 18 * 60)))
    }

    @Test fun shieldTighteningVsLoosening() {
        val r = ShieldRule("p", "P", pauseSeconds = 5, dailyLimitMinutes = 30, dailyOpenLimit = 0, passMinutes = 5)
        assertFalse(r.wouldLoosen(r))
        assertFalse(r.wouldLoosen(r.copy(pauseSeconds = 10)))
        assertFalse(r.wouldLoosen(r.copy(dailyLimitMinutes = 20)))
        assertFalse(r.wouldLoosen(r.copy(dailyOpenLimit = 3))) // none → 3 is stricter
        assertTrue(r.wouldLoosen(r.copy(dailyLimitMinutes = 0)))  // removing the limit
        assertTrue(r.wouldLoosen(r.copy(dailyLimitMinutes = 45)))
        assertTrue(r.wouldLoosen(r.copy(passMinutes = 10)))
        assertTrue(r.wouldLoosen(r.copy(enabled = false)))
        assertFalse(r.copy(enabled = false).wouldLoosen(r)) // turning a shield on is fine
    }
}
