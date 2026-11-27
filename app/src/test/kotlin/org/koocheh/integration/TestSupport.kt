package org.koocheh.integration

import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.koocheh.crypto.CryptoBackend
import org.koocheh.crypto.KeyPair
import org.koocheh.model.DisclosureTier
import org.koocheh.model.InterestTag
import org.koocheh.psi.PsiEngine
import org.koocheh.psi.PsiProtocolException
import org.koocheh.psi.PsiResult
import org.koocheh.psi.PsiSession
import org.koocheh.psi.RevealLevel
import org.koocheh.storage.ContextStore
import org.koocheh.storage.PeerRecord
import org.koocheh.storage.StoredMessage

/** JDK-only CryptoBackend: enough for ids, commitments and sealing. Test use only, never shipped. */
class JdkCryptoBackend : CryptoBackend {
    private val rng = SecureRandom()

    override fun randomBytes(n: Int): ByteArray = ByteArray(n).also { rng.nextBytes(it) }

    override fun hash(data: ByteArray, outLen: Int): ByteArray =
        MessageDigest.getInstance("SHA-512").digest(data).copyOf(outLen)

    override fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(data)
        }

    override fun aeadEncrypt(key: ByteArray, nonce: ByteArray, plain: ByteArray, ad: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce.copyOf(12)))
        c.updateAAD(ad)
        return c.doFinal(plain)
    }

    override fun aeadDecrypt(key: ByteArray, nonce: ByteArray, cipher: ByteArray, ad: ByteArray): ByteArray? =
        try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce.copyOf(12)))
            c.updateAAD(ad)
            c.doFinal(cipher)
        } catch (e: GeneralSecurityException) {
            null
        }

    override fun signKeypair(): KeyPair = unsupported()
    override fun sign(secretKey: ByteArray, message: ByteArray): ByteArray = unsupported()
    override fun signVerify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean = unsupported()
    override fun dhKeypair(): KeyPair = unsupported()
    override fun dh(secretKey: ByteArray, publicKey: ByteArray): ByteArray = unsupported()

    private fun unsupported(): Nothing = throw UnsupportedOperationException("not needed by these tests")
}

/** INSECURE test PSI (tags travel in the clear). It does not depend on the build variant, unlike FakePsiEngine. */
class TestPsiEngine : PsiEngine {
    override fun newSession(localTags: Set<InterestTag>, reveal: RevealLevel): PsiSession = Session(localTags, reveal)

    private class Session(private val local: Set<InterestTag>, private val reveal: RevealLevel) : PsiSession {
        private var sent = false
        override var result: PsiResult? = null
            private set

        override fun outgoing(): ByteArray {
            if (sent) return ByteArray(0)
            sent = true
            return (listOf(reveal.name) + local.map { it.value }.sorted()).joinToString("\n").encodeToByteArray()
        }

        override fun incoming(msg: ByteArray) {
            val lines = msg.decodeToString().split("\n")
            if (lines.first() != reveal.name) throw PsiProtocolException("reveal level mismatch")
            val remote = lines.drop(1).filter { it.isNotEmpty() }.toSet()
            val shared = local.filter { it.value in remote }.toSet()
            result = PsiResult(shared.size, if (reveal == RevealLevel.TAGS) shared else null)
        }
    }
}

/** Plain in-memory ContextStore that also counts what was written, so tests can look inside. */
class InMemoryContextStore : ContextStore {
    val peers = ConcurrentHashMap<String, PeerRecord>()
    private val encounters = ConcurrentHashMap<String, MutableSet<Int>>()
    private val messageLog = ConcurrentHashMap<String, MutableList<StoredMessage>>()
    val incrementCalls = AtomicInteger()

    fun encounterDays(): Map<String, Set<Int>> = encounters.mapValues { it.value.toSet() }
    fun isEmpty(): Boolean = peers.isEmpty() && encounters.isEmpty() && messageLog.values.all { it.isEmpty() }

    override suspend fun putPeerRecord(peerKey: String, tier: DisclosureTier, payload: ByteArray) {
        peers[peerKey] = PeerRecord(peerKey, tier, payload.copyOf())
    }

    override suspend fun getPeerRecord(peerKey: String): PeerRecord? = peers[peerKey]

    override suspend fun deletePeer(peerKey: String) {
        peers.remove(peerKey)
        messageLog.remove(peerKey)
    }

    override suspend fun incrementEncounter(linkKey: ByteArray, day: Int) {
        incrementCalls.incrementAndGet()
        encounters.getOrPut(hex(linkKey)) { ConcurrentHashMap.newKeySet() }.add(day)
    }

    override suspend fun encountersFor(linkKey: ByteArray, sinceDay: Int): List<Int> =
        (encounters[hex(linkKey)] ?: emptySet<Int>()).filter { it >= sinceDay }.sorted()

    override suspend fun putMessage(peerKey: String, expiresAtMs: Long, cipher: ByteArray) {
        messageLog.getOrPut(peerKey) { java.util.Collections.synchronizedList(mutableListOf()) }
            .add(StoredMessage(peerKey, expiresAtMs, cipher.copyOf()))
    }

    override suspend fun messages(peerKey: String): List<StoredMessage> = messageLog[peerKey]?.toList() ?: emptyList()

    override suspend fun purgeExpired(nowMs: Long) {
        messageLog.values.forEach { list -> synchronized(list) { list.removeAll { it.expiresAtMs <= nowMs } } }
    }

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
}
