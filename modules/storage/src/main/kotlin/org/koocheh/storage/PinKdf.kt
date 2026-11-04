package org.koocheh.storage

import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.PwHash
import com.sun.jna.NativeLong
import java.nio.CharBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Memory-hard PIN to key derivation. Returns 32 bytes. Never logs its input or output. */
interface PinKdf {
    val saltBytes: Int
    fun derive(pin: CharArray, salt: ByteArray): ByteArray
}

/**
 * Argon2id through libsodium (crypto_pwhash); no hand-rolled KDF.
 * [UNVERIFIED] The lazysodium native signature and the limits below were not run on a device.
 */
class SodiumPinKdf(
    private val opsLimit: Long = 2L,
    private val memLimitBytes: Long = 64L * 1024 * 1024,
) : PinKdf {
    override val saltBytes: Int = 16

    private val sodium by lazy { LazySodiumAndroid(SodiumAndroid()) }

    override fun derive(pin: CharArray, salt: ByteArray): ByteArray {
        require(salt.size == saltBytes) { "bad salt size" }
        val pw = VaultCrypto.utf8(pin)
        val out = ByteArray(32)
        try {
            val rc = sodium.cryptoPwHash(
                out, out.size.toLong(), pw, pw.size.toLong(), salt,
                opsLimit, NativeLong(memLimitBytes), PwHash.Alg.PWHASH_ALG_ARGON2ID13.value,
            )
            check(rc == 0) { "key derivation failed" }
            return out
        } finally {
            VaultCrypto.wipe(pw)
        }
    }
}

/** Small AES-GCM, HMAC and RNG helpers (JDK primitives only). */
internal object VaultCrypto {
    private val rng = SecureRandom()
    private const val NONCE = 12
    private const val TAG_BITS = 128

    fun random(n: Int): ByteArray = ByteArray(n).also { rng.nextBytes(it) }

    fun wipe(b: ByteArray) = b.fill(0)

    fun utf8(chars: CharArray): ByteArray {
        val bb = Charsets.UTF_8.encode(CharBuffer.wrap(chars))
        val out = ByteArray(bb.remaining())
        bb.get(out)
        if (bb.hasArray()) bb.array().fill(0)
        return out
    }

    /** Returns nonce || ciphertext||tag. */
    fun seal(key: ByteArray, plain: ByteArray): ByteArray {
        val nonce = random(NONCE)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        return nonce + c.doFinal(plain)
    }

    /** Returns null when the key is wrong or the blob was altered. */
    fun open(key: ByteArray, blob: ByteArray): ByteArray? {
        if (blob.size < NONCE + TAG_BITS / 8) return null
        return try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(
                Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"),
                GCMParameterSpec(TAG_BITS, blob.copyOfRange(0, NONCE)),
            )
            c.doFinal(blob, NONCE, blob.size - NONCE)
        } catch (e: GeneralSecurityException) {
            null
        }
    }

    fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val m = Mac.getInstance("HmacSHA256")
        m.init(SecretKeySpec(key, "HmacSHA256"))
        return m.doFinal(data)
    }
}
