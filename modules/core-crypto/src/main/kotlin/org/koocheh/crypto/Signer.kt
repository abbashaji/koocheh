package org.koocheh.crypto

/** Ed25519 signing, used for invite tokens. */
class Signer(private val backend: CryptoBackend) {
    fun newKeypair(): KeyPair = backend.signKeypair()

    fun sign(secretKey: ByteArray, message: ByteArray): ByteArray =
        backend.sign(secretKey, message)

    fun verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean =
        backend.signVerify(publicKey, message, signature)
}
