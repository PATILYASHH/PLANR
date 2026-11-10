package com.exosynk.planr

import com.exosynk.planr.data.Achievement
import com.exosynk.planr.data.Backup
import com.exosynk.planr.data.BackupData
import com.exosynk.planr.data.BackupFormatException
import com.exosynk.planr.data.PlanrSettings
import com.exosynk.planr.data.ProgressState
import com.exosynk.planr.data.ProgressStats
import com.exosynk.planr.data.db.OffGridSession
import com.exosynk.planr.data.db.Schedule
import com.exosynk.planr.data.db.ScheduleMode
import com.exosynk.planr.data.db.ShieldRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupTest {

    private val sample = BackupData(
        shields = listOf(ShieldRule("com.x", "X", pauseSeconds = 8, dailyLimitMinutes = 30, dailyOpenLimit = 4)),
        schedules = listOf(
            Schedule(id = 7, name = "Bedtime", startMinute = 1380, endMinute = 420, mode = ScheduleMode.OFFGRID,
                packages = "com.maps", challenge = "MATH"),
        ),
        settings = PlanrSettings(gatewayEnabled = true, gatewayWhitelist = setOf("com.a"), emergencyQuota = 1,
            lockdownEnabled = true, windDownDnd = true, eyeCareEnabled = true),
        progress = ProgressState(ProgressStats(xp = 420, bestStreak = 6, resists = 12), mapOf(Achievement.FIRST_OFFGRID.name to 123L)),
        sessions = listOf(OffGridSession(id = 3, startedAt = 1000, plannedEndAt = 5000, endedAt = 5000, scheduleId = 7)),
    )

    @Test fun roundTrip() {
        val back = Backup.decode(Backup.encode(sample, 1L, "0.3.0"))
        assertEquals(sample.shields, back.shields)
        // Ids are reassigned on import
        assertEquals(sample.schedules.map { it.copy(id = 0) }, back.schedules)
        assertEquals(sample.settings, back.settings)
        assertEquals(sample.progress, back.progress)
        assertEquals(1, back.sessions.size)
        assertEquals(-1L, back.sessions[0].scheduleId)
        assertEquals(5000L, back.sessions[0].endedAt)
    }

    @Test fun rejectsOtherFiles() {
        assertThrows(BackupFormatException::class.java) { Backup.decode("not json") }
        assertThrows(BackupFormatException::class.java) { Backup.decode("{\"format\":\"other\"}") }
        assertThrows(BackupFormatException::class.java) {
            Backup.decode("{\"format\":\"planr-backup\",\"version\":99}")
        }
    }

    @Test fun clampsAndSkipsBadValues() {
        val json = """
            {"format":"planr-backup","version":1,
             "shields":[{"packageName":"a","pauseSeconds":999,"passMinutes":-3},{"packageName":""}],
             "schedules":[{"name":"","startMinute":5000,"daysMask":0,"mode":"NOPE"}],
             "settings":{"emergencyQuota":50},
             "progress":{"xp":-10,"achievements":{"FAKE":1}},
             "offGridSessions":[{"startedAt":10,"plannedEndAt":5}]}
        """.trimIndent()
        val d = Backup.decode(json)
        assertEquals(1, d.shields.size)
        assertEquals(30, d.shields[0].pauseSeconds)
        assertEquals(1, d.shields[0].passMinutes)
        assertEquals("Schedule", d.schedules[0].name)
        assertEquals(1439, d.schedules[0].startMinute)
        assertEquals(0b1111111, d.schedules[0].daysMask)
        assertEquals(ScheduleMode.BLOCK, d.schedules[0].mode)
        assertEquals(PlanrSettings.MAX_EMERGENCY_QUOTA, d.settings.emergencyQuota)
        assertEquals(0, d.progress.stats.xp)
        assertEquals(emptyMap<String, Long>(), d.progress.unlocked)
        assertEquals(0, d.sessions.size)
    }
}
