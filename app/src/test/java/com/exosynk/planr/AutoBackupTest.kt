package com.exosynk.planr

import com.exosynk.planr.data.AutoBackup
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class AutoBackupTest {
    @Test fun nameMatchesXpencStyle() {
        assertEquals("141126PLANRBACKUP.json", AutoBackup.fileName(LocalDate.of(2026, 11, 14)))
    }

    @Test fun keepsNewestSevenAndNeverTouchesOtherFiles() {
        val days = (1..10).map { AutoBackup.fileName(LocalDate.of(2026, 11, it)) }
        val other = listOf("notes.txt", "011126XPENCEBACKUP.json", "PLANRBACKUP.json")
        val pruned = AutoBackup.namesToPrune(days.shuffled() + other)
        assertEquals(days.take(3).toSet(), pruned.toSet()) // Nov 1–3 go, Nov 4–10 stay
    }

    @Test fun ordersByDateNotByName() {
        // 31 Dec 2026 sorts before 01 Jan 2027 as text; by date it's older.
        val dec = AutoBackup.fileName(LocalDate.of(2026, 12, 31))
        val jan = AutoBackup.fileName(LocalDate.of(2027, 1, 1))
        assertEquals(listOf(dec), AutoBackup.namesToPrune(listOf(jan, dec), keep = 1))
    }
}

class SyncRuleTest {
    private fun h(at: Long, device: String) = com.exosynk.planr.data.Backup.Header(at, device, "Phone")

    @org.junit.Test fun onlyAnotherDevicesNewerUnhandledBackupCounts() {
        val rule = com.exosynk.planr.data.AutoBackup::isRemoteNewer
        org.junit.Assert.assertTrue(rule(h(200, "B"), "A", 0, 100))   // newer, from B
        org.junit.Assert.assertFalse(rule(h(200, "A"), "A", 0, 100))  // our own file
        org.junit.Assert.assertFalse(rule(h(200, "B"), "A", 200, 100)) // already handled
        org.junit.Assert.assertFalse(rule(h(50, "B"), "A", 0, 100))   // older than our last save
        org.junit.Assert.assertFalse(rule(h(200, ""), "A", 0, 100))   // pre-sync backup, no device id
    }

    @org.junit.Test fun headerRoundTrip() {
        val data = com.exosynk.planr.data.BackupData(emptyList(), emptyList(), com.exosynk.planr.data.PlanrSettings(),
            com.exosynk.planr.data.ProgressState(), emptyList())
        val json = com.exosynk.planr.data.Backup.encode(data, 123L, "0.6.0", device = "dev-1", deviceName = "Pixel")
        org.junit.Assert.assertEquals(com.exosynk.planr.data.Backup.Header(123L, "dev-1", "Pixel"), com.exosynk.planr.data.Backup.header(json))
        org.junit.Assert.assertNull(com.exosynk.planr.data.Backup.header("{\"format\":\"other\"}"))
    }
}
