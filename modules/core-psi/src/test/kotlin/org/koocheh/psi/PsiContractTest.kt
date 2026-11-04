package org.koocheh.psi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.koocheh.model.InterestTag
import java.security.MessageDigest

/**
 * Contract any [PsiEngine] must pass. Subclass it and supply [engine].
 *
 * LIMIT OF THESE TESTS: they catch obvious leaks (plaintext tags, unsalted hashes of
 * tags in the transcript) and wrong results. They cannot prove that unmatched tags
 * stay hidden; that needs a protocol-level security argument and an audit.
 * Tags used here are 6+ characters so accidental byte matches are negligible.
 */
abstract class PsiContractTest {
    /** Called once per simulated device, so the two sides never share an engine instance. */
    abstract fun engine(): PsiEngine

    protected class Outcome(
        val a: PsiResult?,
        val b: PsiResult?,
        val transcript: List<ByteArray>,
    )

    protected fun run(
        tagsA: Set<InterestTag>,
        tagsB: Set<InterestTag>,
        levelA: RevealLevel,
        levelB: RevealLevel = levelA,
    ): Outcome {
        val sa = engine().newSession(tagsA, levelA)
        val sb = engine().newSession(tagsB, levelB)
        val transcript = mutableListOf<ByteArray>()
        for (round in 0 until MAX_ROUNDS) {
            if (sa.result != null && sb.result != null) break
            val ma = sa.outgoing()
            val mb = sb.outgoing()
            if (ma.isNotEmpty()) transcript += ma
            if (mb.isNotEmpty()) transcript += mb
            if (ma.isNotEmpty()) sb.incoming(ma)
            if (mb.isNotEmpty()) sa.incoming(mb)
        }
        return Outcome(sa.result, sb.result, transcript)
    }

    private val cases: List<Pair<Set<InterestTag>, Set<InterestTag>>> = listOf(
        tags("photography", "chess-club", "gardening") to tags("cooking", "cycling"),
        tags("photography", "chess-club", "gardening") to tags("photography", "gardening", "hiking"),
        tags("photography", "chess-club") to tags("photography", "chess-club"),
        tags("photography") to tags("photography", "chess-club", "gardening"),
        tags("photography", "chess-club") to emptySet(),
        emptySet<InterestTag>() to emptySet(),
        (1..10).map { InterestTag("interest-%02d".format(it)) }.toSet() to
            (6..15).map { InterestTag("interest-%02d".format(it)) }.toSet(),
    )

    @Test
    fun resultIsNullBeforeAnyExchange() {
        val s = engine().newSession(tags("photography"), RevealLevel.TAGS)
        assertNull(s.result)
    }

    @Test
    fun countEqualsIntersectionSize() {
        for (level in RevealLevel.values()) for ((x, y) in cases) {
            val o = run(x, y, level)
            val expected = x.intersect(y).size
            assertEquals(expected, o.a?.count)
            assertEquals(expected, o.b?.count)
        }
    }

    @Test
    fun resultsAreSymmetric() {
        for (level in RevealLevel.values()) for ((x, y) in cases) {
            val o = run(x, y, level)
            assertNotNull(o.a)
            assertEquals(o.a, o.b)
        }
    }

    @Test
    fun countOnlyNeverExposesTags() {
        for ((x, y) in cases) {
            val o = run(x, y, RevealLevel.COUNT_ONLY)
            assertNull(o.a?.sharedTags)
            assertNull(o.b?.sharedTags)
        }
    }

    @Test
    fun tagsLevelRevealsExactlyTheIntersection() {
        for ((x, y) in cases) {
            val o = run(x, y, RevealLevel.TAGS)
            assertEquals(x.intersect(y), o.a?.sharedTags)
            assertEquals(x.intersect(y), o.b?.sharedTags)
        }
    }

    @Test
    fun transcriptLeaksNoTagPlaintextOrUnsaltedHash() {
        for (level in RevealLevel.values()) for ((x, y) in cases) {
            val o = run(x, y, level)
            assertFalse("tag leaked in transcript", leaksTags(o.transcript, x + y))
        }
    }

    @Test
    fun mismatchedRevealLevelsAreRejected() {
        try {
            run(tags("photography"), tags("photography"), RevealLevel.COUNT_ONLY, RevealLevel.TAGS)
            fail("expected PsiProtocolException")
        } catch (expected: PsiProtocolException) {
            // ok
        }
    }

    @Test
    fun resultToStringDoesNotContainTagValues() {
        val o = run(tags("photography", "gardening"), tags("photography"), RevealLevel.TAGS)
        val text = o.a.toString()
        assertFalse(text.contains("photography"))
        assertTrue(text.contains("count=1"))
    }

    protected companion object {
        const val MAX_ROUNDS = 8

        fun tags(vararg s: String): Set<InterestTag> = s.map { InterestTag(it) }.toSet()

        /** True if any message contains a tag's UTF-8 bytes or its plain SHA-256 / SHA-1. */
        fun leaksTags(transcript: List<ByteArray>, all: Set<InterestTag>): Boolean {
            for (tag in all) {
                val raw = tag.value.toByteArray(Charsets.UTF_8)
                val needles = listOf(raw) + listOf("SHA-256", "SHA-1").map {
                    MessageDigest.getInstance(it).digest(raw)
                }
                for (msg in transcript) for (n in needles) {
                    if (containsSubsequence(msg, n)) return true
                }
            }
            return false
        }

        private fun containsSubsequence(hay: ByteArray, needle: ByteArray): Boolean {
            if (needle.isEmpty() || needle.size > hay.size) return false
            outer@ for (i in 0..hay.size - needle.size) {
                for (j in needle.indices) if (hay[i + j] != needle[j]) continue@outer
                return true
            }
            return false
        }
    }
}
