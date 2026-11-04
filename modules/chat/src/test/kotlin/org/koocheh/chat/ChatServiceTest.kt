package org.koocheh.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koocheh.crypto.SecureSession

private class FakePort : MessagePort {
    val data = mutableMapOf<String, MutableList<Pair<Long, ByteArray>>>()
    override suspend fun append(peerKey: String, expiresAtMs: Long, cipher: ByteArray) {
        data.getOrPut(peerKey) { mutableListOf() }.add(expiresAtMs to cipher)
    }
    override suspend fun list(peerKey: String) = data[peerKey].orEmpty().map { it.second }
    override suspend fun delete(peerKey: String) { data.remove(peerKey) }
    override suspend fun purge(nowMs: Long) {
        data.values.forEach { l -> l.removeAll { it.first <= nowMs } }
    }
}

private class XorSealer : MessageSealer {
    override fun seal(plain: ByteArray) = plain.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
    override fun open(sealed: ByteArray) = seal(sealed)
}

private class FakeSession(var failDecrypt: Boolean = false) : SecureSession {
    override fun encrypt(plain: ByteArray) = plain.map { (it.toInt() xor 0x11).toByte() }.toByteArray()
    override fun decrypt(cipher: ByteArray): ByteArray {
        if (failDecrypt) throw IllegalStateException("bad tag")
        return encrypt(cipher)
    }
}

class ChatServiceTest {
    private var t = 0L
    private val port = FakePort()
    private var n = 0
    private val service = ChatService(ChatStore(port, XorSealer()), { t }, { "id${n++}" })
    private val peer = "peer"

    private fun convo() = runBlocking { service.conversation(peer).first() }

    @Test
    fun expiryBoundary() = runBlocking {
        service.send(peer, "hi")
        t = DEFAULT_EXPIRY_MS - 1
        assertEquals(1, convo().size)
        t = DEFAULT_EXPIRY_MS
        assertTrue(convo().isEmpty())
        service.purgeExpired()
        assertTrue(port.data[peer].orEmpty().isEmpty())
    }

    @Test
    fun expiryCanOnlyBeShortened() = runBlocking {
        service.expiryMs = DEFAULT_EXPIRY_MS * 3
        assertEquals(DEFAULT_EXPIRY_MS, service.expiryMs)
        service.expiryMs = 120_000
        service.send(peer, "short")
        assertEquals(120_000L, convo().single().expiresAtMs)
    }

    @Test
    fun queuedDeliveryOrder() = runBlocking {
        for (s in listOf("a", "b", "c")) { t += 10; service.send(peer, s) }
        val session = FakeSession()
        val out = service.outgoing(peer, session)
        assertEquals(listOf("a", "b", "c"), out.map { String(session.decrypt(it.ciphertext())) })
        out.forEach { service.delivered(peer, it) }
        assertTrue(service.outgoing(peer, session).isEmpty())
    }

    @Test
    fun decryptFailureIsDropped() = runBlocking {
        val good = FakeSession()
        val env = MessageEnvelope.of(EnvelopeKind.MESSAGE, "x", 0, 1000, good.encrypt("secret".toByteArray()))
        service.incoming(peer, FakeSession(failDecrypt = true), env)
        assertTrue(convo().isEmpty())
        service.incoming(peer, good, env)
        assertEquals("secret", convo().single().text)
    }

    @Test
    fun withdrawalDeletesConversation() = runBlocking {
        service.send(peer, "one")
        service.withdraw(peer)
        assertTrue(convo().isEmpty())

        service.send(peer, "two")
        val session = FakeSession()
        val w = MessageEnvelope.of(EnvelopeKind.WITHDRAW, "w", 0, 0, session.encrypt(ByteArray(0)))
        service.incoming(peer, session, w)
        assertTrue(convo().isEmpty())
    }
}
