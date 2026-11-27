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
