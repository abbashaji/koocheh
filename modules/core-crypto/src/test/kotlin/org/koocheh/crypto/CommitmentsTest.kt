package org.koocheh.crypto

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommitmentsTest {
    private val commitments = Commitments(JdkTestBackend())
    private val value = "tier-2-reveal".toByteArray()

    @Test
    fun opensWithCorrectNonceAndValue() {
        val c = commitments.commit(value)
        assertEquals(32, c.commitment.size)
        assertTrue(commitments.verify(c.commitment, c.nonce, value))
    }

    @Test
    fun bindingRejectsOtherValueOrNonce() {
        val c = commitments.commit(value)
        assertFalse(commitments.verify(c.commitment, c.nonce, "other".toByteArray()))
        val badNonce = c.nonce.also { it[0] = (it[0].toInt() xor 1).toByte() }
        assertFalse(commitments.verify(c.commitment, badNonce, value))
        assertFalse(commitments.verify(c.commitment, ByteArray(8), value))
    }

    @Test
    fun committingTwiceToTheSameValueLooksUnrelated() {
        val a = commitments.commit(value)
        val b = commitments.commit(value)
        assertFalse(a.commitment.contentEquals(b.commitment))
        assertFalse(a.nonce.contentEquals(b.nonce))
    }

    @Test
    fun commitmentDoesNotContainTheValue() {
        val c = commitments.commit(value)
        assertFalse(String(c.commitment, Charsets.ISO_8859_1).contains(String(value, Charsets.ISO_8859_1)))
    }

    @Test
    fun toStringIsRedacted() {
        assertContentEquals("Commit(redacted)".toByteArray(), commitments.commit(value).toString().toByteArray())
    }
}
