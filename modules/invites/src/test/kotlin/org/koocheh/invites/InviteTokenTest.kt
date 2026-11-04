package org.koocheh.invites

import java.security.MessageDigest
import java.security.SecureRandom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InviteTokenTest {

    /** Fake: public key == secret key; signature = SHA-256(pub || message). */
    private object FakeSigner : InviteSigner {
        override fun sign(secretKey: ByteArray, message: ByteArray): ByteArray =
            MessageDigest.getInstance("SHA-256").digest(secretKey + message)

        override fun verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean =
            sign(publicKey, message).contentEquals(signature)
    }

    private val issuerKey = ByteArray(32) { 1 }
    private val inviteeKey = ByteArray(32) { 2 }
    private val otherKey = ByteArray(32) { 3 }
    private val ctx = "ctx-a"
    private val now = 1_000_000L

    private fun issuer() = InviteIssuer(FakeSigner, issuerKey, issuerKey, InMemoryNonceLedger(), SecureRandom())
    private fun verifier(context: String = ctx) = InviteVerifier(FakeSigner, context)
    private fun token() = issuer().issueForKey(ctx, inviteeKey, now)

    @Test
    fun validTokenAccepted() {
        val result = verifier().verify(token(), inviteeKey, now + 1)
        assertTrue(result is InviteResult.Valid)
    }

    @Test
    fun expiryBoundary() {
        val t = token()
        assertEquals(now + INVITE_VALIDITY_MS, t.expiresAtMs)
        assertTrue(verifier().verify(t, inviteeKey, t.expiresAtMs - 1) is InviteResult.Valid)
        assertEquals(InviteResult.Expired, verifier().verify(t, inviteeKey, t.expiresAtMs))
        assertEquals(InviteResult.Expired, verifier().verify(t, inviteeKey, t.expiresAtMs + 1))
    }

    @Test
    fun wrongContextRejected() {
        assertEquals(InviteResult.WrongContext, verifier("ctx-b").verify(token(), inviteeKey, now))
    }

    @Test
    fun wrongInviteeKeyRejected() {
        assertEquals(InviteResult.WrongInvitee, verifier().verify(token(), otherKey, now))
    }

    @Test
    fun replayedNonceRejected() {
        val v = verifier()
        val t = token()
        assertTrue(v.verify(t, inviteeKey, now) is InviteResult.Valid)
        assertEquals(InviteResult.Replayed, v.verify(t, inviteeKey, now))
    }

    @Test
    fun failedCheckDoesNotConsumeNonce() {
        val v = verifier()
        val t = token()
        assertEquals(InviteResult.WrongInvitee, v.verify(t, otherKey, now))
        assertTrue(v.verify(t, inviteeKey, now) is InviteResult.Valid)
    }

    @Test
    fun tamperedTokenRejected() {
        val t = token()
        val forged = InviteToken(
            t.version, t.contextKey, t.issuerPublicKey, otherKey, t.nonce, t.expiresAtMs, t.signature,
        )
        assertEquals(InviteResult.BadSignature, verifier().verify(forged, otherKey, now))
    }

    @Test
    fun issuerNeverRepeatsNonce() {
        val i = issuer()
        val nonces = (1..50).map { i.issueForKey(ctx, inviteeKey, now).nonce.toList() }.toSet()
        assertEquals(50, nonces.size)
    }

    @Test
    fun codecRoundTrip() {
        val t = token()
        assertEquals(t, QrCodec.decode(QrCodec.encode(t)))
        assertEquals(t, QrCodec.decodeText(QrCodec.encodeText(t)))
    }

    @Test
    fun truncatedInputReturnsNullOrMalformed() {
        val bytes = QrCodec.encode(token())
        for (n in 0 until bytes.size) {
            assertNull("len $n", QrCodec.decode(bytes.copyOf(n)))
            assertEquals(InviteResult.Malformed, verifier().verifyBytes(bytes.copyOf(n), inviteeKey, now))
        }
        assertNotNull(QrCodec.decode(bytes))
    }

    @Test
    fun trailingGarbageAndEmptyRejected() {
        val bytes = QrCodec.encode(token())
        assertNull(QrCodec.decode(bytes + byteArrayOf(0)))
        assertNull(QrCodec.decode(ByteArray(0)))
        assertNull(QrCodec.decodeText("!!not base64!!"))
    }
}
