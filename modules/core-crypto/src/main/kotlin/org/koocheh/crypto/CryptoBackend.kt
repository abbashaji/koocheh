package org.koocheh.crypto

/** Public/secret key pair. toString never prints key bytes. */
class KeyPair(publicKey: ByteArray, secretKey: ByteArray) {
    private val pub: ByteArray = publicKey.copyOf()
    private val sec: ByteArray = secretKey.copyOf()

    val publicKey: ByteArray get() = pub.copyOf()
    val secretKey: ByteArray get() = sec.copyOf()

    /** Zeroes the held key material. The pair must not be used afterwards. */
    fun destroy() {
        pub.fill(0)
        sec.fill(0)
    }

    override fun toString(): String = "KeyPair(redacted)"
}

/**
 * The libsodium surface this module needs. Everything above this port is pure Kotlin logic
 * that unit-tests on the JVM with a fake backend. [LazySodiumBackend] is the real one.
 * Implementations never log inputs or outputs.
 */
interface CryptoBackend {
    fun randomBytes(n: Int): ByteArray

    /** BLAKE2b generic hash, outLen 16..64. */
    fun hash(data: ByteArray, outLen: Int): ByteArray

    /** HMAC-SHA-256 with a key of at most 32 bytes. Output is 32 bytes. */
    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray

    /** Ed25519: public 32 bytes, secret 64 bytes. */
    fun signKeypair(): KeyPair
    fun sign(secretKey: ByteArray, message: ByteArray): ByteArray
    fun signVerify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean

    /** X25519: public 32 bytes, secret 32 bytes. */
    fun dhKeypair(): KeyPair

    /** X25519 shared point. Throws IllegalStateException when the result is all zero. */
    fun dh(secretKey: ByteArray, publicKey: ByteArray): ByteArray

    /** XChaCha20-Poly1305 (IETF): key 32 bytes, nonce 24 bytes, 16-byte tag appended. */
    fun aeadEncrypt(key: ByteArray, nonce: ByteArray, plain: ByteArray, ad: ByteArray): ByteArray

    /** Returns null when authentication fails. */
    fun aeadDecrypt(key: ByteArray, nonce: ByteArray, cipher: ByteArray, ad: ByteArray): ByteArray?
}

/** HKDF-SHA256 (RFC 5869) composed over the backend's HMAC. Salt must be at most 32 bytes. */
internal object Hkdf {
    private const val HASH_LEN = 32

    fun derive(
        backend: CryptoBackend,
        ikm: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        outLen: Int,
    ): ByteArray {
        require(outLen in 1..(255 * HASH_LEN)) { "bad output length" }
        val prk = backend.hmacSha256(if (salt.isEmpty()) ByteArray(HASH_LEN) else salt, ikm)
        val out = ByteArray(outLen)
        var t = ByteArray(0)
        var pos = 0
        var counter = 1
        while (pos < outLen) {
            t = backend.hmacSha256(prk, t + info + byteArrayOf(counter.toByte()))
            val n = minOf(HASH_LEN, outLen - pos)
            t.copyInto(out, pos, 0, n)
            pos += n
            counter++
        }
        prk.fill(0)
        t.fill(0)
        return out
    }
}

internal fun u64be(v: Long): ByteArray = ByteArray(8) { i -> (v ushr (56 - 8 * i)).toByte() }

internal fun readU64be(b: ByteArray, off: Int = 0): Long {
    var r = 0L
    for (i in 0 until 8) r = (r shl 8) or (b[off + i].toLong() and 0xff)
    return r
}

internal fun readU32be(b: ByteArray, off: Int = 0): Long {
    var r = 0L
    for (i in 0 until 4) r = (r shl 8) or (b[off + i].toLong() and 0xff)
    return r
}
