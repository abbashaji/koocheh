package org.koocheh.crypto

enum class Role { INITIATOR, RESPONDER }

/** Raised for malformed, replayed, out-of-order or unauthentic messages. Never carries key material. */
class SessionException(message: String) : Exception(message)

interface SecureSession {
    fun encrypt(plain: ByteArray): ByteArray
    fun decrypt(cipher: ByteArray): ByteArray
}

/**
 * Drives the handshake. Initiator: next(null) returns message 1, then next(message 2) returns
 * null and session() is ready. Responder: next(message 1) returns message 2 and session() is ready.
 */
interface HandshakeState {
    fun next(incoming: ByteArray?): ByteArray?
    fun session(): SecureSession?
}

class SecureSessionFactory(private val backend: CryptoBackend) {
    /**
     * [peerStaticPublic] is the peer's X25519 static key learned at match time. When it is null the
     * handshake is ephemeral-only and gives forward secrecy but no peer authentication.
     */
    fun start(role: Role, ownStatic: KeyPair, peerStaticPublic: ByteArray?): HandshakeState =
        DhHandshake(backend, role, ownStatic, peerStaticPublic?.copyOf())
}

/**
 * [UNVERIFIED] This exact handshake construction needs the independent audit before the pilot.
 * Shape: Noise-like, two messages, each carrying one ephemeral X25519 public key. Shared secret
 * input is ee, plus es and se when the peer static key is known. Keys come from HKDF-SHA256 with
 * the transcript hash as salt. Known gaps for the audit: no key-confirmation message (the
 * initiator is confirmed only by its first data message), no identity hiding, and the choice to
 * allow an unauthenticated mode.
 */
private class DhHandshake(
    private val backend: CryptoBackend,
    private val role: Role,
    private val ownStatic: KeyPair,
    private val peerStatic: ByteArray?,
) : HandshakeState {
    private var step = 0
    private var ready: SecureSession? = null

    init {
        require(peerStatic == null || peerStatic.size == KEY_BYTES) { "bad peer static key" }
    }

    override fun session(): SecureSession? = ready

    @Synchronized
    override fun next(incoming: ByteArray?): ByteArray? = when (role) {
        Role.INITIATOR -> initiator(incoming)
        Role.RESPONDER -> responder(incoming)
    }

    private fun initiator(incoming: ByteArray?): ByteArray? = when (step) {
        0 -> {
            if (incoming != null) throw SessionException("unexpected input")
            val e = backend.dhKeypair()
            pendingEphemeral = e
            step = 1
            e.publicKey
        }
        1 -> {
            val e = pendingEphemeral ?: throw SessionException("handshake state lost")
            finish(e, remoteEphemeral(incoming))
            step = 2
            null
        }
        else -> throw SessionException("handshake already complete")
    }

    private fun responder(incoming: ByteArray?): ByteArray? {
        if (step != 0) throw SessionException("handshake already complete")
        val remote = remoteEphemeral(incoming)
        val e = backend.dhKeypair()
        val reply = e.publicKey
        finish(e, remote)
        step = 2
        return reply
    }

    private var pendingEphemeral: KeyPair? = null

    private fun remoteEphemeral(msg: ByteArray?): ByteArray {
        if (msg == null || msg.size != KEY_BYTES) throw SessionException("malformed handshake message")
        return msg.copyOf()
    }

    private fun finish(ownEphemeral: KeyPair, remoteEphemeral: ByteArray) {
        val initiator = role == Role.INITIATOR
        val ownEphPub = ownEphemeral.publicKey
        val ePubI = if (initiator) ownEphPub else remoteEphemeral
        val ePubR = if (initiator) remoteEphemeral else ownEphPub
        val ownEphSec = ownEphemeral.secretKey
        val ownStaticSec = ownStatic.secretKey
        val parts = ArrayList<ByteArray>()
        try {
            parts += try {
                backend.dh(ownEphSec, remoteEphemeral)
            } catch (e: IllegalStateException) {
                throw SessionException("invalid peer key")
            }
            var transcript = TRANSCRIPT_LABEL + ePubI + ePubR
            val remoteStatic = peerStatic
            if (remoteStatic != null) {
                val sPubI = if (initiator) ownStatic.publicKey else remoteStatic
                val sPubR = if (initiator) remoteStatic else ownStatic.publicKey
                transcript += byteArrayOf(1) + sPubI + sPubR
                try {
                    // es: initiator ephemeral with responder static. se: initiator static with responder ephemeral.
                    parts += if (initiator) backend.dh(ownEphSec, remoteStatic) else backend.dh(ownStaticSec, remoteEphemeral)
                    parts += if (initiator) backend.dh(ownStaticSec, remoteEphemeral) else backend.dh(ownEphSec, remoteStatic)
                } catch (e: IllegalStateException) {
                    throw SessionException("invalid peer key")
                }
            } else {
                transcript += byteArrayOf(0)
            }
            val ikm = parts.fold(ByteArray(0)) { acc, p -> acc + p }
            val salt = backend.hash(transcript, 32)
            val okm = Hkdf.derive(backend, ikm, salt, KEYS_INFO, 64)
            val first = okm.copyOfRange(0, 32)
            val second = okm.copyOfRange(32, 64)
            ready = if (initiator) RatchetSession(backend, first, second) else RatchetSession(backend, second, first)
            ikm.fill(0); okm.fill(0); first.fill(0); second.fill(0)
        } finally {
            parts.forEach { it.fill(0) }
            ownEphSec.fill(0)
            ownStaticSec.fill(0)
            ownEphemeral.destroy()
            pendingEphemeral = null
        }
    }

    private companion object {
        const val KEY_BYTES = 32
        val TRANSCRIPT_LABEL = "koocheh/session/v1".toByteArray(Charsets.UTF_8)
        val KEYS_INFO = "koocheh/session/keys/v1".toByteArray(Charsets.UTF_8)
    }
}

/**
 * Per-direction symmetric ratchet. Each message key is HMAC(chain, 0x01) and the chain then advances
 * to HMAC(chain, 0x02), so earlier keys cannot be recomputed from the current state. Messages must
 * arrive in order: the 8-byte counter prefix is authenticated as associated data and must equal the
 * expected value. A failed authentication leaves the chain untouched so forged input cannot desync it.
 */
private class RatchetSession(
    private val backend: CryptoBackend,
    sendChain: ByteArray,
    recvChain: ByteArray,
) : SecureSession {
    private var send: ByteArray = sendChain.copyOf()
    private var recv: ByteArray = recvChain.copyOf()
    private var sendCounter = 0L
    private var recvCounter = 0L

    @Synchronized
    override fun encrypt(plain: ByteArray): ByteArray {
        val msgKey = backend.hmacSha256(send, byteArrayOf(1))
        val nextChain = backend.hmacSha256(send, byteArrayOf(2))
        val header = u64be(sendCounter)
        val body = backend.aeadEncrypt(msgKey, nonce(sendCounter), plain, header)
        msgKey.fill(0)
        send.fill(0)
        send = nextChain
        sendCounter++
        return header + body
    }

    @Synchronized
    override fun decrypt(cipher: ByteArray): ByteArray {
        if (cipher.size < HEADER_BYTES + TAG_BYTES) throw SessionException("malformed message")
        val counter = readU64be(cipher)
        if (counter != recvCounter) throw SessionException("unexpected message counter")
        val msgKey = backend.hmacSha256(recv, byteArrayOf(1))
        val nextChain = backend.hmacSha256(recv, byteArrayOf(2))
        val plain = backend.aeadDecrypt(
            msgKey,
            nonce(counter),
            cipher.copyOfRange(HEADER_BYTES, cipher.size),
            cipher.copyOfRange(0, HEADER_BYTES),
        )
        msgKey.fill(0)
        if (plain == null) {
            nextChain.fill(0)
            throw SessionException("authentication failed")
        }
        recv.fill(0)
        recv = nextChain
        recvCounter++
        return plain
    }

    private fun nonce(counter: Long): ByteArray =
        ByteArray(NONCE_BYTES).also { u64be(counter).copyInto(it, NONCE_BYTES - 8) }

    private companion object {
        const val HEADER_BYTES = 8
        const val TAG_BYTES = 16
        const val NONCE_BYTES = 24
    }
}
