package org.koocheh.integration

import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.koocheh.AppStack
import org.koocheh.disclosure.DisclosureSession
import org.koocheh.disclosure.LocalDisclosure
import org.koocheh.model.AgeRange
import org.koocheh.model.Clock
import org.koocheh.model.ContextId
import org.koocheh.model.EphemeralId
import org.koocheh.model.FakeClock
import org.koocheh.model.InterestTag
import org.koocheh.model.Sighting
import org.koocheh.storage.KeyVault
import org.koocheh.storage.MasterKeyWrapper
import org.koocheh.storage.PinKdf
import org.koocheh.storage.UnlockResult
import org.koocheh.storage.VaultStorage
import org.koocheh.transport.PeerLink

class TestPinKdf : PinKdf {
    override val saltBytes: Int = 16
    override fun derive(pin: CharArray, salt: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(String(pin).toByteArray(Charsets.UTF_8) + salt)
}

class MemoryVaultStorage : VaultStorage {
    @Volatile
    private var blob: ByteArray? = null
    override fun read(): ByteArray? = blob?.copyOf()
    override fun write(bytes: ByteArray) {
        blob = bytes.copyOf()
    }

    override fun destroy() {
        blob = null
    }
}

/** Stands in for the Keystore key: once destroyed, nothing can be unwrapped any more. */
class TestMasterKeyWrapper : MasterKeyWrapper {
    @Volatile
    var destroyed = false
        private set

    override fun wrap(plain: ByteArray): ByteArray = plain + MARK

    override fun unwrap(wrapped: ByteArray): ByteArray {
        if (destroyed) throw GeneralSecurityException("key destroyed")
        return wrapped.copyOfRange(0, wrapped.size - MARK.size)
    }

    override fun destroy() {
        destroyed = true
    }

    private companion object {
        val MARK = byteArrayOf(7, 7, 7, 7)
    }
}

/** Like SecureStore: one store per (namespace, context); readable only while the vault is unlocked. */
class MemoryStores(private val namespace: () -> String) {
    private val all = ConcurrentHashMap<String, InMemoryContextStore>()

    fun memory(context: ContextId): InMemoryContextStore =
        all.getOrPut("${namespace()}/$context") { InMemoryContextStore() }

    fun clear() = all.clear()
    fun isEmpty(): Boolean = all.isEmpty()
}

class VaultHarness {
    val storage = MemoryVaultStorage()
    val wrapper = TestMasterKeyWrapper()
    val destroyCalls = AtomicInteger()
    val stores = MemoryStores { vault.namespace }
    val vault: KeyVault = KeyVault(TestPinKdf(), wrapper, storage) {
        destroyCalls.incrementAndGet()
        stores.clear()
    }

    fun unlockReal(pin: String = "1111") {
        vault.initialize(pin.toCharArray())
        check(vault.unlock(pin.toCharArray()) == UnlockResult.REAL)
    }
}

/** One phone: its own vault, stores and wiring, over a shared clock. */
class Peer(
    val clock: Clock,
    scope: CoroutineScope,
    val nickname: String,
    tags: Set<String>,
) {
    val harness = VaultHarness().also { it.unlockReal() }
    val stack = AppStack(harness.vault, harness::storeFor, TestPsiEngine(), JdkCryptoBackend(), clock, scope)
    private val local = LocalDisclosure(
        tags = tags.map(::InterestTag).toSet(),
        threshold = 1,
        nickname = nickname,
        line = "$nickname's line",
        ageRange = AgeRange.R25_34,
        blurredPhoto = "blur-$nickname".encodeToByteArray(),
        clearPhoto = "clear-$nickname".encodeToByteArray(),
    )

    suspend fun open(link: PeerLink, context: ContextId = ContextId.WORK): DisclosureSession =
        stack.openSession(link, Sighting(EphemeralId(ByteArray(16)), -60, 0L), context, local)

    fun store(context: ContextId = ContextId.WORK): InMemoryContextStore = harness.stores.memory(context)
}

private fun VaultHarness.storeFor(context: ContextId): InMemoryContextStore = stores.memory(context)

class Sessions(val a: DisclosureSession, val b: DisclosureSession)

/** Two phones that meet over a fake radio. Ava and Bo share the tags coffee and chess. */
class Duo(
    scope: CoroutineScope,
    tagsA: Set<String> = setOf("coffee", "chess", "rust"),
    tagsB: Set<String> = setOf("coffee", "chess", "jazz"),
) {
    val clock = FakeClock(now = START_MS)
    val a = Peer(clock, scope, "Ava", tagsA)
    val b = Peer(clock, scope, "Bo", tagsB)
    lateinit var last: Sessions
        private set
    val sa: DisclosureSession get() = last.a
    val sb: DisclosureSession get() = last.b

    suspend fun connect(context: ContextId = ContextId.WORK): Sessions {
        val radio = FakeRadioLink()
        last = Sessions(a.open(radio.linkA, context), b.open(radio.linkB, context))
        return last
    }

    companion object {
        const val DAY = 20_000
        const val START_MS = DAY * 86_400_000L + 3_600_000L
    }
}

/** Both sides confirm each step from hello up to [upTo] (1 = T1 ... 4 = T4); assumes both are at T0. */
@OptIn(ExperimentalCoroutinesApi::class)
suspend fun TestScope.advanceBoth(s: Sessions, upTo: Int) {
    if (upTo >= 1) {
        s.a.sayHello()
        s.b.sayHello()
        advanceUntilIdle()
    }
    for (step in 2..upTo) {
        s.a.confirmNext()
        s.b.confirmNext()
        advanceUntilIdle()
    }
}
