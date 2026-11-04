package org.koocheh.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetentionPolicyTest {
    private val day = RetentionPolicy.MS_PER_DAY
    private val now = 100 * day + 5_000
    private val today = RetentionPolicy.epochDay(now)

    @Test fun thirteenDaysOldIsKept() {
        assertFalse(RetentionPolicy.isEncounterExpired(today - 13, now))
        assertTrue(today - 13 >= RetentionPolicy.encounterCutoffDay(now))
    }

    @Test fun fourteenDaysOldIsKept() {
        assertFalse(RetentionPolicy.isEncounterExpired(today - 14, now))
    }

    @Test fun fifteenDaysOldIsDeleted() {
        assertTrue(RetentionPolicy.isEncounterExpired(today - 15, now))
        assertTrue(today - 15 < RetentionPolicy.encounterCutoffDay(now))
    }

    @Test fun cutoffMatchesExpiry() {
        for (age in 0..30) {
            val d = today - age
            assertEquals(d < RetentionPolicy.encounterCutoffDay(now), RetentionPolicy.isEncounterExpired(d, now))
        }
    }

    @Test fun messageExpiresAtItsDeadline() {
        assertFalse(RetentionPolicy.isMessageExpired(now + 1, now))
        assertTrue(RetentionPolicy.isMessageExpired(now, now))
        assertTrue(RetentionPolicy.isMessageExpired(now - 1, now))
    }
}
