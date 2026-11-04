package org.koocheh.security

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koocheh.storage.KeyVault
import org.koocheh.storage.UnlockResult

class SecurityPoliciesTest {

    private class FakeVault : KeyVault {
        var destroyed = 0
        var locked = false
        override fun unlock(pin: CharArray) = UnlockResult.WRONG
        override fun lock() { locked = true }
        override fun destroyAll() { destroyed++ }
    }

    @Test
    fun gateMapsAllFourResults() {
        val gate = DecoyGate()
        assertEquals(AppMode.REAL, gate.route(UnlockResult.REAL))
        assertEquals(AppMode.DECOY, gate.route(UnlockResult.DECOY))
        assertEquals(AppMode.LOCKED, gate.route(UnlockResult.WIPED))
        assertEquals(AppMode.LOCKED, gate.route(UnlockResult.WRONG))
    }

    @Test
    fun decoyContentIsNotEmpty() {
        assertTrue(DecoyContent.profiles.isNotEmpty())
        assertTrue(DecoyContent.tags.isNotEmpty())
    }

    @Test
    fun wipeIsIdempotentAndRunsEveryStep() = runBlocking {
        val vault = FakeVault()
        var discovery = 0
        var notifications = 0
        var recents = 0
        var activities = 0
        val wipe = PanicWipe(
            vault,
            { discovery++ },
            { notifications++ },
            { recents++ },
            { activities++ }
        )
        wipe.execute()
        wipe.execute()
        assertEquals(2, vault.destroyed)
        assertEquals(2, discovery)
        assertEquals(2, notifications)
        assertEquals(2, recents)
        assertEquals(2, activities)
    }

    @Test
    fun wipeContinuesWhenAStepThrows() = runBlocking {
        val vault = FakeVault()
        var finished = 0
        val wipe = PanicWipe(
            vault,
            { throw IllegalStateException("boom") },
            { },
            { },
            { finished++ }
        )
        wipe.execute()
        assertEquals(1, vault.destroyed)
        assertEquals(1, finished)
    }

    @Test
    fun notificationTextIsGenericAndStatic() {
        val texts = listOf(
            NotificationPolicy.TITLE,
            NotificationPolicy.TEXT,
            NotificationPolicy.CHANNEL_NAME,
            NotificationPolicy.CHANNEL_ID
        )
        val dynamicMarkers = listOf("%", "{", "}", "$")
        texts.forEach { text ->
            dynamicMarkers.forEach { marker -> assertFalse(text.contains(marker)) }
            assertFalse(text.any { it.isDigit() })
        }
        val tagsAndNames = DecoyContent.tags + DecoyContent.profiles.map { it.displayName }
        texts.forEach { text ->
            tagsAndNames.forEach { word ->
                assertFalse(text.contains(word, ignoreCase = true))
            }
        }
    }
}
