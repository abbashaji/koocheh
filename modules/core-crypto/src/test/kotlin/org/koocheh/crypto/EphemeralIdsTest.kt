package org.koocheh.crypto

import org.koocheh.model.ContextId
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** JDK-backed stand-in so these tests never load the native library. Only hash, hmac and random are real. */
internal class JdkTestBackend : CryptoBackend {
    private val rng = SecureRandom()

    override fun randomBytes(n: Int): ByteArray = ByteArray(n).also { rng.nextBytes(it) }

    override fun hash(data: ByteArray, outLen: Int): ByteArray {
        require(outLen == 32)
        return MessageDigest.getInstance("SHA-256").digest(data)
    }

    override fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    override fun signKeypair(): KeyPair = native()
    override fun sign(secretKey: ByteArray, message: ByteArray): ByteArray = native()
    override fun signVerify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean = native()
    override fun dhKeypair(): KeyPair = native()
    override fun dh(secretKey: ByteArray, publicKey: ByteArray): ByteArray = native()
    override fun aeadEncrypt(key: ByteArray, nonce: ByteArray, plain: ByteArray, ad: ByteArray): ByteArray = native()
    override fun aeadDecrypt(key: ByteArray, nonce: ByteArray, cipher: ByteArray, ad: ByteArray): ByteArray? = native()

    private fun native(): Nothing = throw UnsupportedOperationException("needs libsodium")
}

private class MapSecrets(private val secrets: Map<ContextId, ByteArray>) : ContextSecretSource {
    override fun secret(context: ContextId): ByteArray = secrets.getValue(context).copyOf()
}

class EphemeralIdsTest {
    private val backend = JdkTestBackend()
    private val minute = 60_000L

    private fun provider(work: ByteArray, social: ByteArray = work) =
        HkdfEphemeralIdProvider(MapSecrets(mapOf(ContextId.WORK to work, ContextId.SOCIAL to social)), backend)

    private fun secret(rnd: Random) = rnd.nextBytes(32)

    @Test
    fun hkdfMatchesRfc5869Vector() {
        val ikm = ByteArray(22) { 0x0b }
        val salt = ByteArray(13) { it.toByte() }
        val info = ByteArray(10) { (0xf0 + it).toByte() }
        val okm = Hkdf.derive(backend, ikm, salt, info, 42)
        val expected = "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865"
        assertEquals(expected, okm.joinToString("") { "%02x".format(it) })
    }

    @Test
    fun rotationStaysWithinTenToFifteenMinutes() {
        val rnd = Random(1)
        repeat(200) {
            val p = provider(secret(rnd))
            var t = rnd.nextLong(1_700_000_000_000L, 1_800_000_000_000L)
            val first = p.nextRotationAtMs(ContextId.WORK, t)
            assertTrue(first > t && first - t <= 15 * minute)
            t = first
            repeat(20) {
                val next = p.nextRotationAtMs(ContextId.WORK, t)
                val len = next - t
                assertTrue(len in 10 * minute..15 * minute, "epoch length $len out of range")
                t = next
            }
        }
    }

    @Test
    fun rotationInstantsAreNotOneFixedGrid() {
        val rnd = Random(2)
        val shapes = (1..50).map {
            val s = EpochSchedule.fromEntropy(rnd.nextLong(0, 1L shl 32), rnd.nextLong(0, 1L shl 32))
            s.lengthMs to s.offsetMs
        }.toSet()
        assertTrue(shapes.size > 25)
    }

    @Test
    fun idsDifferAcrossContexts() {
        val rnd = Random(3)
        val same = secret(rnd)
        val shared = provider(same)
        assertNotEquals(shared.current(ContextId.WORK, 1_750_000_000_000L), shared.current(ContextId.SOCIAL, 1_750_000_000_000L))
        val split = provider(secret(rnd), secret(rnd))
        assertNotEquals(split.current(ContextId.WORK, 1_750_000_000_000L), split.current(ContextId.SOCIAL, 1_750_000_000_000L))
    }

    @Test
    fun idIsStableWithinAnEpochAndChangesAtRotation() {
        val p = provider(secret(Random(4)))
        val t = 1_750_000_000_000L
        val rotation = p.nextRotationAtMs(ContextId.WORK, t)
        assertEquals(p.current(ContextId.WORK, t), p.current(ContextId.WORK, rotation - 1))
        assertNotEquals(p.current(ContextId.WORK, t), p.current(ContextId.WORK, rotation))
    }

    @Test
    fun noBytePositionIsConstantAcrossAThousandEpochs() {
        val p = provider(secret(Random(5)))
        var t = 1_750_000_000_000L
        val ids = (1..1000).map {
            val id = p.current(ContextId.WORK, t).bytes
            t = p.nextRotationAtMs(ContextId.WORK, t)
            id
        }
        assertEquals(1000, ids.map { it.toList() }.toSet().size)
        for (pos in ids.first().indices) {
            assertTrue(ids.map { it[pos] }.toSet().size > 1, "byte $pos is constant")
        }
    }

    @Test
    fun sameSecretGivesSameIds() {
        val s = secret(Random(6))
        assertContentEquals(
            provider(s).current(ContextId.WORK, 1_750_000_000_000L).bytes,
            provider(s).current(ContextId.WORK, 1_750_000_000_000L).bytes,
        )
    }
}
