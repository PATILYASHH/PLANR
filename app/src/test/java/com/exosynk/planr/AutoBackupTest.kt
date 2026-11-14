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
