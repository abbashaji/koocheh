package org.koocheh

import android.app.ActivityManager
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koocheh.chat.MessageSealer
import org.koocheh.crypto.Commitments as CryptoCommitments
import org.koocheh.crypto.ContextSecretSource
import org.koocheh.crypto.CryptoBackend
import org.koocheh.crypto.EphemeralIdProvider
import org.koocheh.crypto.HkdfEphemeralIdProvider
import org.koocheh.crypto.LazySodiumBackend
import org.koocheh.disclosure.AgeRangeCodec
import org.koocheh.disclosure.Commit as CommitPort
import org.koocheh.disclosure.Commitments as CommitmentsPort
import org.koocheh.disclosure.ContextStore as PeerStorePort
import org.koocheh.disclosure.DisclosureSession
import org.koocheh.disclosure.LocalDisclosure
import org.koocheh.disclosure.PeerChannel as ChannelPort
import org.koocheh.disclosure.PsiEngine as PsiEnginePort
import org.koocheh.disclosure.PsiResult as PsiResultPort
import org.koocheh.disclosure.PsiSession as PsiSessionPort
import org.koocheh.disclosure.RevealLevel as RevealLevelPort
import org.koocheh.familiar.EncounterLinker
import org.koocheh.familiar.EncounterStore
import org.koocheh.familiar.HmacEncounterLinker
import org.koocheh.model.AgeRange
import org.koocheh.model.Clock
import org.koocheh.model.ContextId
import org.koocheh.model.DisclosureTier
import org.koocheh.model.EphemeralId
import org.koocheh.model.InterestTag
import org.koocheh.model.Profile
import org.koocheh.model.Sighting
import org.koocheh.model.SystemClock
import org.koocheh.psi.PsiEngine
import org.koocheh.psi.PsiSession
import org.koocheh.psi.RevealLevel
import org.koocheh.security.PanicWipe as RealPanicWipe
import org.koocheh.storage.ContextStore
import org.koocheh.storage.FileVaultStorage
import org.koocheh.storage.KeystoreMasterKeyWrapper
import org.koocheh.storage.SecureStore
import org.koocheh.storage.SodiumPinKdf
import org.koocheh.transport.BleDiscoveryService
import org.koocheh.transport.GattPeerLink
import org.koocheh.transport.PeerChannel
import org.koocheh.transport.PeerDirectory
import org.koocheh.transport.PeerLink
import org.koocheh.transport.RadioCapabilities

/** Raised when a dependency cannot be provided on this device or in this build. */
class ServiceUnavailable(name: String) : IllegalStateException("$name is not available")

// The app talks to the real module types; these names stay for the UI code.
typealias DiscoveryService = org.koocheh.transport.DiscoveryService
typealias DisclosureEngine = org.koocheh.disclosure.DisclosureEngine
typealias FamiliarTracker = org.koocheh.familiar.FamiliarTracker
typealias ChatService = org.koocheh.chat.ChatService
typealias KeyVault = org.koocheh.storage.KeyVault

/** App-facing panic entry point; the real org.koocheh.security.PanicWipe is wrapped below. */
interface PanicWipe {
    fun wipe()
}

// ---- Adapters from the real module types to the local ports of disclosure and familiar. ----

internal class CommitmentsAdapter(private val real: CryptoCommitments) : CommitmentsPort {
    override fun commit(value: ByteArray): CommitPort =
        real.commit(value).let { CommitPort(it.commitment, it.nonce) }

    override fun verify(commitment: ByteArray, nonce: ByteArray, value: ByteArray): Boolean =
        real.verify(commitment, nonce, value)
}

internal class PsiEngineAdapter(private val real: PsiEngine) : PsiEnginePort {
    override fun newSession(localTags: Set<InterestTag>, reveal: RevealLevelPort): PsiSessionPort {
        val level = when (reveal) {
            RevealLevelPort.COUNT_ONLY -> RevealLevel.COUNT_ONLY
            RevealLevelPort.TAGS -> RevealLevel.TAGS
        }
        return PsiSessionAdapter(real.newSession(localTags, level))
    }
}

internal class PsiSessionAdapter(private val real: PsiSession) : PsiSessionPort {
    override fun outgoing(): ByteArray = real.outgoing()
    override fun incoming(msg: ByteArray) = real.incoming(msg)
    override val result: PsiResultPort?
        get() = real.result?.let { PsiResultPort(it.count, it.sharedTags) }
}

internal class DisclosureStoreAdapter(private val real: ContextStore) : PeerStorePort {
    override suspend fun putPeerRecord(peerKey: String, tier: DisclosureTier, payload: ByteArray) =
        real.putPeerRecord(peerKey, tier, payload)

    override suspend fun getPeerRecord(peerKey: String): ByteArray? = real.getPeerRecord(peerKey)?.payload
    override suspend fun deletePeer(peerKey: String) = real.deletePeer(peerKey)
}

internal class ChannelAdapter(private val real: PeerChannel) : ChannelPort {
    override suspend fun send(bytes: ByteArray) = real.send(bytes)
    override suspend fun receive(): ByteArray = real.receive()
    override fun close() = real.close()
}

/** One-line wrapper: the familiar port has the same signatures as ContextStore. */
internal class EncounterStoreAdapter(private val real: ContextStore) : EncounterStore {
    override suspend fun incrementEncounter(linkKey: ByteArray, day: Int) = real.incrementEncounter(linkKey, day)
    override suspend fun encountersFor(linkKey: ByteArray, sinceDay: Int): List<Int> =
        real.encountersFor(linkKey, sinceDay)

    override suspend fun purgeExpired(nowMs: Long) = real.purgeExpired(nowMs)
}

internal object EnumAgeRangeCodec : AgeRangeCodec {
    override fun encode(range: AgeRange): String = range.name
    override fun decode(text: String): AgeRange? = AgeRange.entries.firstOrNull { it.name == text }
}

private const val DAY_MS = 86_400_000L

internal fun epochDay(nowMs: Long): Int = Math.floorDiv(nowMs, DAY_MS).toInt()

/**
 * Feeds FamiliarTracker from Tier 1 shared tags. T0 is COUNT_ONLY, so sharedTags is null there and
 * nothing is recorded. The first state with sharedTags != null and withdrawn == false (after mutual
 * hello) records exactly one encounter for the session. A session that withdraws first records nothing.
 */
internal class FamiliarFeed(
    private val linker: EncounterLinker,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    fun attach(context: ContextId, tracker: FamiliarTracker, session: DisclosureSession): Job = scope.launch {
        val seen = session.state.first { it.withdrawn || it.sharedTags != null }
        val tags = seen.sharedTags
        if (seen.withdrawn || tags == null) return@launch
        val day = epochDay(clock.nowMs())
        try {
            tracker.record(context, linker.linkKey(context, tags, day), day)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Locked vault or store failure: skip this encounter. Link keys and tags are never logged.
        }
    }
}

/**
 * HmacEncounterLinker keyed from the vault. KeyVault cannot store an arbitrary secret, so the key is
 * derived on demand from the context's vault secret (HMAC with a fixed label) and is never persisted
 * or logged. It is stable across restarts, which a random in-memory secret would not be.
 */
internal class ContextSecretLinker(private val secrets: ContextSecretSource) : EncounterLinker {
    override fun linkKey(context: ContextId, sharedTags: Set<InterestTag>, day: Int): ByteArray {
        val root = secrets.secret(context)
        val secret = try {
            Mac.getInstance("HmacSHA256").run {
                init(SecretKeySpec(root, "HmacSHA256"))
                doFinal(LINK_LABEL)
            }
        } finally {
            root.fill(0)
        }
        try {
            return HmacEncounterLinker(secret).linkKey(context, sharedTags, day)
        } finally {
            secret.fill(0)
        }
    }

    private companion object {
        val LINK_LABEL = "koocheh/familiar-link/v1".toByteArray(Charsets.UTF_8)
    }
}

/** Seals chat records at rest with a key derived from the context's vault secret. */
internal class VaultMessageSealer(
    private val secrets: ContextSecretSource,
    private val backend: CryptoBackend,
    private val context: ContextId,
) : MessageSealer {
    override fun seal(plain: ByteArray): ByteArray {
        val key = key()
        try {
            val nonce = backend.randomBytes(NONCE_BYTES)
            return nonce + backend.aeadEncrypt(key, nonce, plain, AD)
        } finally {
            key.fill(0)
        }
    }

    override fun open(sealed: ByteArray): ByteArray {
        require(sealed.size > NONCE_BYTES) { "sealed record too short" }
        val key = key()
        try {
            val nonce = sealed.copyOfRange(0, NONCE_BYTES)
            val body = sealed.copyOfRange(NONCE_BYTES, sealed.size)
            return backend.aeadDecrypt(key, nonce, body, AD)
                ?: throw IllegalArgumentException("sealed record not authentic")
        } finally {
            key.fill(0)
        }
    }

    private fun key(): ByteArray {
        val root = secrets.secret(context)
        try {
            return backend.hmacSha256(root, KEY_LABEL)
        } finally {
            root.fill(0)
        }
    }

    private companion object {
        const val NONCE_BYTES = 24
        val KEY_LABEL = "koocheh/chat-seal/v1".toByteArray(Charsets.UTF_8)
        val AD = "koocheh/chat-record/v1".toByteArray(Charsets.UTF_8)
    }
}

/**
 * Advertises the current id of each context (one slot per context) and re-advertises at the first
 * rotation instant after now. If the vault is locked the loop retries; nothing is advertised then.
 */
internal class AdvertisedIdRotator(
    private val ids: EphemeralIdProvider,
    private val advertise: (slot: Int, id: EphemeralId) -> Unit,
    private val contexts: List<ContextId>,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    fun start(): Job = scope.launch {
        while (isActive) {
            val wait = try {
                val now = clock.nowMs()
                contexts.forEachIndexed { slot, c -> advertise(slot, ids.current(c, now)) }
                contexts.minOf { ids.nextRotationAtMs(it, now) } - now
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                RETRY_MS
            }
            delay(wait.coerceAtLeast(1L))
        }
    }

    private companion object {
        const val RETRY_MS = 5_000L
    }
}

/**
 * The wiring of the real modules, free of Android types so JVM tests can build whole stacks with fakes.
 * Everything that holds state for a context is kept per context; [reset] drops it when the vault locks.
 */
internal class AppStack(
    private val secrets: ContextSecretSource,
    private val storeFor: (ContextId) -> ContextStore,
    psi: PsiEngine,
    private val backend: CryptoBackend,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    val ids: EphemeralIdProvider = HkdfEphemeralIdProvider(secrets, backend)
    val linker: EncounterLinker = ContextSecretLinker(secrets)
    private val commitments = CommitmentsAdapter(CryptoCommitments(backend))
    private val psiPort = PsiEngineAdapter(psi)
    private val feed = FamiliarFeed(linker, clock, scope)
    private val trackers = ConcurrentHashMap<ContextId, FamiliarTracker>()
    private val chats = ConcurrentHashMap<ContextId, ChatService>()

    fun disclosureEngine(context: ContextId, local: LocalDisclosure): DisclosureEngine =
        DisclosureEngine(
            commitments, psiPort, DisclosureStoreAdapter(storeFor(context)), local, EnumAgeRangeCodec, scope,
        )

    fun familiar(context: ContextId): FamiliarTracker =
        trackers.getOrPut(context) { FamiliarTracker(EncounterStoreAdapter(storeFor(context))) }

    fun chat(context: ContextId): ChatService = chats.getOrPut(context) {
        org.koocheh.chat.ChatService.create(storeFor(context), VaultMessageSealer(secrets, backend, context), clock)
    }

    /** Opens a link to a sighted peer and runs the disclosure tiers over it. */
    suspend fun openSession(
        link: PeerLink,
        sighting: Sighting,
        context: ContextId,
        local: LocalDisclosure,
    ): DisclosureSession {
        val channel = ChannelAdapter(link.open(sighting))
        val session = disclosureEngine(context, local).open(channel, context)
        feed.attach(context, familiar(context), session)
        return session
    }

    fun reset() {
        trackers.clear()
        chats.clear()
    }
}

/**
 * Fails closed: without a chosen PSI library (see modules/core-psi/LIBRARY_EVALUATION.md) a release
 * build refuses to start disclosure. Only debug builds may use the insecure FakePsiEngine.
 */
internal fun resolvePsiEngine(
    real: (() -> PsiEngine)?,
    debugBuild: Boolean,
    loadFake: () -> PsiEngine = ::loadFakePsiEngine,
): PsiEngine {
    real?.let { return it() }
    check(debugBuild) {
        "No private set intersection library has been chosen (see modules/core-psi/LIBRARY_EVALUATION.md); " +
            "refusing to run disclosure in a release build."
    }
    return loadFake()
}

private fun loadFakePsiEngine(): PsiEngine = try {
    Class.forName("org.koocheh.psi.FakePsiEngine").getDeclaredConstructor().newInstance() as PsiEngine
} catch (e: ReflectiveOperationException) {
    throw IllegalStateException("FakePsiEngine is not available in this build", e)
}

/** What the local user discloses in a context; the photo is sent clear only at Tier 4. */
fun localDisclosure(profile: Profile, threshold: Int = 1, blurredPhoto: ByteArray? = null): LocalDisclosure =
    LocalDisclosure(
        tags = profile.tags.toSet(),
        threshold = threshold,
        nickname = profile.nickname,
        line = profile.line,
        ageRange = profile.ageRange,
        blurredPhoto = blurredPhoto,
        clearPhoto = profile.photo,
    )
