package org.koocheh.crypto

import com.goterl.lazysodium.SodiumAndroid

/**
 * libsodium through lazysodium's low-level binding.
 * [UNVERIFIED] The native method names and parameter types below follow libsodium's C API
 * as exposed by lazysodium 5.1.0. They have not been compiled or run against the library.
 * Output-length pointers are passed as null, which libsodium allows for these calls.
 * Never logs keys, ids, tags or plaintext.
 */
class LazySodiumBackend(private val sodium: SodiumAndroid = SodiumAndroid()) : CryptoBackend {

    init {
        check(sodium.sodium_init() >= 0) { "libsodium init failed" }
    }

    override fun randomBytes(n: Int): ByteArray = ByteArray(n).also { sodium.randombytes_buf(it, n) }

    override fun hash(data: ByteArray, outLen: Int): ByteArray {
        val out = ByteArray(outLen)
        check(sodium.crypto_generichash(out, outLen, data, data.size.toLong(), null, 0) == 0)
        return out
    }

    override fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        require(key.size <= KEY_BYTES) { "hmac key too long" }
        // HMAC zero-pads short keys, so padding to libsodium's fixed 32 bytes is equivalent.
        val k = key.copyOf(KEY_BYTES)
        val out = ByteArray(32)
        check(sodium.crypto_auth_hmacsha256(out, data, data.size.toLong(), k) == 0)
        k.fill(0)
        return out
    }

    override fun signKeypair(): KeyPair {
        val pk = ByteArray(32)
        val sk = ByteArray(64)
        check(sodium.crypto_sign_keypair(pk, sk) == 0)
        return KeyPair(pk, sk).also { pk.fill(0); sk.fill(0) }
    }

    override fun sign(secretKey: ByteArray, message: ByteArray): ByteArray {
        require(secretKey.size == 64) { "bad signing key" }
        val sig = ByteArray(64)
        check(sodium.crypto_sign_detached(sig, null, message, message.size.toLong(), secretKey) == 0)
        return sig
    }

    override fun signVerify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
        if (publicKey.size != 32 || signature.size != 64) return false
        return sodium.crypto_sign_verify_detached(signature, message, message.size.toLong(), publicKey) == 0
    }

    override fun dhKeypair(): KeyPair {
        val pk = ByteArray(32)
        val sk = ByteArray(32)
        check(sodium.crypto_box_keypair(pk, sk) == 0)
        return KeyPair(pk, sk).also { pk.fill(0); sk.fill(0) }
    }

    override fun dh(secretKey: ByteArray, publicKey: ByteArray): ByteArray {
        require(secretKey.size == 32 && publicKey.size == 32) { "bad dh key" }
        val out = ByteArray(32)
        check(sodium.crypto_scalarmult(out, secretKey, publicKey) == 0) { "invalid peer key" }
        return out
    }

    override fun aeadEncrypt(key: ByteArray, nonce: ByteArray, plain: ByteArray, ad: ByteArray): ByteArray {
        require(key.size == 32 && nonce.size == 24) { "bad aead parameters" }
        val out = ByteArray(plain.size + TAG_BYTES)
        val rc = sodium.crypto_aead_xchacha20poly1305_ietf_encrypt(
            out, null, plain, plain.size.toLong(), ad, ad.size.toLong(), null, nonce, key,
        )
        check(rc == 0)
        return out
    }

    override fun aeadDecrypt(key: ByteArray, nonce: ByteArray, cipher: ByteArray, ad: ByteArray): ByteArray? {
        require(key.size == 32 && nonce.size == 24) { "bad aead parameters" }
        if (cipher.size < TAG_BYTES) return null
        val out = ByteArray(cipher.size - TAG_BYTES)
        val rc = sodium.crypto_aead_xchacha20poly1305_ietf_decrypt(
            out, null, null, cipher, cipher.size.toLong(), ad, ad.size.toLong(), nonce, key,
        )
        return if (rc == 0) out else null
    }

    private companion object {
        const val KEY_BYTES = 32
        const val TAG_BYTES = 16
    }
}
