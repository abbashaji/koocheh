package org.koocheh.familiar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FamiliarPolicyTest {
    @Test
    fun defaultsMatchSpec() {
        val p = FamiliarPolicy()
        assertEquals(3, p.threshold)
        assertEquals(7, p.windowDays)
        assertEquals(14, p.expiryDays)
    }

    @Test
    fun invalidValuesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { FamiliarPolicy(threshold = 0) }
        assertThrows(IllegalArgumentException::class.java) { FamiliarPolicy(windowDays = 0) }
        assertThrows(IllegalArgumentException::class.java) { FamiliarPolicy(windowDays = 10, expiryDays = 7) }
    }

    @Test
    fun promptTextIsPlainStatement() {
        val text = FamiliarPolicy().promptText(FamiliarPrompt(3, 7))
        assertEquals("You have crossed paths with someone who shares interests, 3 times this week.", text)
        assertFalse(text.contains("!"))
        listOf("hurry", "don't miss", "unlock", "find out").forEach {
            assertFalse(text.lowercase().contains(it))
        }
    }

    @Test
    fun promptTextHandlesSingularAndOtherWindows() {
        assertTrue(FamiliarPolicy(threshold = 1).promptText(FamiliarPrompt(1, 7)).contains("1 time this week"))
        assertTrue(FamiliarPolicy().promptText(FamiliarPrompt(4, 10)).endsWith("4 times in the last 10 days."))
    }

    @Test
    fun dayBucketIsDayAligned() {
        assertEquals(0L, DayBucket(100).startMs % DayBucket.MS_PER_DAY)
        assertEquals(DayBucket(5), DayBucket.ofEpochMs(5 * DayBucket.MS_PER_DAY + 86_399_999L))
        assertEquals(DayBucket(-1), DayBucket.ofEpochMs(-1L))
    }
}
