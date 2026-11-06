package org.koocheh.familiar

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koocheh.model.ContextId
import org.koocheh.model.InterestTag

// [UNVERIFIED] Assumes ContextId(String) and InterestTag(String) constructors; the model module
// was not present on this branch. If they differ, fix ctxA/ctxB/tag() below only.
private val ctxA = ContextId("ctx-a")
private val ctxB = ContextId("ctx-b")
private fun tag(name: String) = InterestTag(name)
private val key = byteArrayOf(1, 2, 3, 4)

private class FakeStore : EncounterStore {
    val rows = HashMap<String, MutableSet<Int>>()
    val incrementDays = mutableListOf<Int>()
    val sinceArgs = mutableListOf<Int>()
    val purgeArgs = mutableListOf<Long>()
    var encountersCalls = 0

    fun rowsFor(k: ByteArray): Set<Int> = rows[hex(k)] ?: emptySet()
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    override suspend fun incrementEncounter(linkKey: ByteArray, day: Int) {
        rows.getOrPut(hex(linkKey)) { mutableSetOf() }.add(day)
        incrementDays += day
    }

    override suspend fun encountersFor(linkKey: ByteArray, sinceDay: Int): List<Int> {
        encountersCalls++
        sinceArgs += sinceDay
        return rowsFor(linkKey).filter { it >= sinceDay }.sorted()
    }

    override suspend fun purgeExpired(nowMs: Long) {
        purgeArgs += nowMs
        val nowDay = (nowMs / DayBucket.MS_PER_DAY).toInt()
        rows.values.forEach { days -> days.removeAll { it < nowDay - 14 } }
    }
}

class FamiliarTrackerTest {
    @Test
    fun thresholdBoundaryTwoVersusThreeDays() = runTest {
        val tracker = FamiliarTracker(FakeStore())
        tracker.record(ctxA, key, 100)
        tracker.record(ctxA, key, 101)
        assertTrue(tracker.prompts(ctxA, 101).isEmpty())
        tracker.record(ctxA, key, 102)
        assertEquals(listOf(FamiliarPrompt(3, 7)), tracker.prompts(ctxA, 102))
    }

    @Test
    fun sameDayDuplicateCountsOnce() = runTest {
        val store = FakeStore()
        val tracker = FamiliarTracker(store)
        tracker.record(ctxA, key, 100)
        tracker.record(ctxA, key, 100)
        tracker.record(ctxA, key, 101)
        assertEquals(listOf(100, 101), store.incrementDays)
        assertTrue(tracker.prompts(ctxA, 101).isEmpty())
    }

    @Test
    fun userSetThresholdIsHonoured() = runTest {
        val tracker = FamiliarTracker(FakeStore(), FamiliarPolicy(threshold = 2))
        tracker.record(ctxA, key, 100)
        tracker.record(ctxA, key, 101)
        assertEquals(listOf(FamiliarPrompt(2, 7)), tracker.prompts(ctxA, 101))
    }

    @Test
    fun windowIsSevenDaysInclusiveOfToday() = runTest {
        val tracker = FamiliarTracker(FakeStore())
        listOf(100, 101, 102).forEach { tracker.record(ctxA, key, it) }
        assertEquals(1, tracker.prompts(ctxA, 106).size)
        assertTrue(tracker.prompts(ctxA, 107).isEmpty())
    }

    @Test
    fun expiryAtFourteenDays() = runTest {
        val store = FakeStore()
        val tracker = FamiliarTracker(store)
        listOf(10, 11, 12).forEach { tracker.record(ctxA, key, it) }
        assertEquals(1, tracker.prompts(ctxA, 12).size)

        val before = store.encountersCalls
        assertTrue(tracker.prompts(ctxA, 26).isEmpty())
        assertEquals(before + 1, store.encountersCalls) // last day 12 is still within 14 days
        assertEquals(setOf(12), store.rowsFor(key))

        assertTrue(tracker.prompts(ctxA, 27).isEmpty())
        assertEquals(before + 1, store.encountersCalls) // pruned from memory, not queried
        assertTrue(store.rowsFor(key).isEmpty()) // purged from the store
    }

    @Test
    fun contextsAreIsolated() = runTest {
        val tracker = FamiliarTracker(FakeStore())
        listOf(100, 101, 102).forEach { tracker.record(ctxA, key, it) }
        assertEquals(1, tracker.prompts(ctxA, 102).size)
        assertTrue(tracker.prompts(ctxB, 102).isEmpty())
    }

    @Test
    fun afterRestartPromptReturnsOnNextRecord() = runTest {
        val store = FakeStore()
        val first = FamiliarTracker(store)
        first.record(ctxA, key, 100)
        first.record(ctxA, key, 101)

        val second = FamiliarTracker(store)
        assertTrue(second.prompts(ctxA, 101).isEmpty())
        second.record(ctxA, key, 102)
        assertEquals(listOf(FamiliarPrompt(3, 7)), second.prompts(ctxA, 102))
    }

    @Test
    fun storeOnlySeesDayInts() = runTest {
        val store = FakeStore()
        val tracker = FamiliarTracker(store)
        val days = listOf(100, 101, 102, 103)
        days.forEach { tracker.record(ctxA, key, it) }
        tracker.prompts(ctxA, 103)
        assertEquals(days, store.incrementDays)
        assertTrue(store.sinceArgs.all { it in 97..103 })
        assertTrue(store.purgeArgs.isNotEmpty())
        assertTrue(store.purgeArgs.all { it % DayBucket.MS_PER_DAY == 0L })
    }

    // --- default linker ---

    private val secret = ByteArray(32) { it.toByte() }

    @Test
    fun linkerIgnoresDayAndTagOrder() {
        val linker = HmacEncounterLinker(secret)
        val a = linker.linkKey(ctxA, linkedSetOf(tag("chess"), tag("hiking")), 100)
        val b = linker.linkKey(ctxA, linkedSetOf(tag("hiking"), tag("chess")), 250)
        assertArrayEquals(a, b)
        assertEquals(32, a.size)
    }

    @Test
    fun linkerSeparatesTagsContextsAndSecrets() {
        val linker = HmacEncounterLinker(secret)
        val base = linker.linkKey(ctxA, setOf(tag("chess")), 1)
        assertFalse(base.contentEquals(linker.linkKey(ctxA, setOf(tag("chess"), tag("hiking")), 1)))
        assertFalse(base.contentEquals(linker.linkKey(ctxB, setOf(tag("chess")), 1)))
        val other = HmacEncounterLinker(ByteArray(32) { 9 })
        assertFalse(base.contentEquals(other.linkKey(ctxA, setOf(tag("chess")), 1)))
    }

    @Test
    fun linkerRejectsShortSecret() {
        assertThrows(IllegalArgumentException::class.java) { HmacEncounterLinker(ByteArray(8)) }
    }
}
