package org.koocheh.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothDevice.PHY_LE_CODED
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koocheh.model.EphemeralId
import org.koocheh.model.ScanMode
import org.koocheh.model.Sighting
import java.nio.ByteBuffer
import java.time.LocalTime
import java.util.concurrent.ConcurrentHashMap

interface DiscoveryService {
    fun start(mode: ScanMode, windows: List<TimeWindow> = emptyList())
    fun stop()
    fun setAdvertisedId(id: EphemeralId)
    val sightings: Flow<Sighting>
}

/** In-memory only: maps a recent sighting to the device needed to connect. */
class PeerDirectory(private val ttlMs: Long = 60_000L) {
    class Entry(val device: BluetoothDevice, val peerSupportsCoded: Boolean, val atMs: Long)

    private val entries = ConcurrentHashMap<ByteBuffer, Entry>()

    fun put(id: EphemeralId, entry: Entry) {
        entries[ByteBuffer.wrap(id.bytes.copyOf())] = entry
        val cutoff = entry.atMs - ttlMs
        entries.values.removeIf { it.atMs < cutoff }
    }

    fun find(id: EphemeralId): Entry? = entries[ByteBuffer.wrap(id.bytes)]
}

/**
 * Duty-cycled BLE scanner. Sightings go to a Flow only; nothing is persisted
 * and ids are never logged. The platform scanner runs only while the
 * ScanScheduler says so.
 */
class BleScanner(
    private val adapter: BluetoothAdapter,
    val directory: PeerDirectory,
    private val nowElapsedMs: () -> Long = SystemClock::elapsedRealtime,
) {
    private val _sightings = MutableSharedFlow<Sighting>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val sightings: Flow<Sighting> = _sightings.asSharedFlow()
    private var callback: ScanCallback? = null

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (callback != null) return
        val scanner = adapter.bluetoothLeScanner ?: return
        val filter = ScanFilter.Builder()
            .setServiceData(ParcelUuid(BleProtocol.APP_SERVICE_UUID), ByteArray(0))
            .build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build()
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) = handle(result)
            override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach(::handle)
        }
        callback = cb
        scanner.startScan(listOf(filter), settings, cb)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        val cb = callback ?: return
        callback = null
        adapter.bluetoothLeScanner?.stopScan(cb)
    }

    private fun handle(result: ScanResult) {
        val bytes = result.scanRecord?.getServiceData(ParcelUuid(BleProtocol.APP_SERVICE_UUID)) ?: return
        if (bytes.size !in 1..BleProtocol.MAX_ID_BYTES) return
        val id = EphemeralId(bytes)
        val now = nowElapsedMs()
        // [UNVERIFIED] Legacy adverts report a 1M primary PHY, so peer Coded
        // support is only detected for extended adverts; measure in the pilot.
        val coded = result.primaryPhy == PHY_LE_CODED || result.secondaryPhy == PHY_LE_CODED
        directory.put(id, PeerDirectory.Entry(result.device, coded, now))
        _sightings.tryEmit(Sighting(id, result.rssi, now))
    }
}

/** Ties advertiser, scanner and scheduler together. */
class BleDiscoveryService(
    private val adapter: BluetoothAdapter,
    private val scope: CoroutineScope,
    val directory: PeerDirectory = PeerDirectory(),
    private val scheduler: ScanScheduler = ScanScheduler(),
    private val nowElapsedMs: () -> Long = SystemClock::elapsedRealtime,
    private val minuteOfDay: () -> Int = { LocalTime.now().let { it.hour * 60 + it.minute } },
) : DiscoveryService {
    private val scanner = BleScanner(adapter, directory, nowElapsedMs)
    private val advertiser = BleAdvertiser(adapter, scope)
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var loop: Job? = null

    override val sightings: Flow<Sighting> get() = scanner.sightings

    override fun start(mode: ScanMode, windows: List<TimeWindow>) {
        scheduler.start(mode, windows)
        advertiser.start()
        if (loop == null) loop = scope.launch { runLoop() }
        wake.trySend(Unit)
    }

    override fun stop() {
        scheduler.stop()
        loop?.cancel(); loop = null
        scanner.stopScan()
        advertiser.stop()
    }

    /** Slot 0 by default; call with a slot to alternate two contexts. */
    override fun setAdvertisedId(id: EphemeralId) = advertiser.setId(0, id)

    fun setAdvertisedId(slot: Int, id: EphemeralId?) = advertiser.setId(slot, id)

    /** The app wires this to its foreground state (PASSIVE mode depends on it). */
    fun setForeground(foreground: Boolean) {
        scheduler.setForeground(foreground)
        wake.trySend(Unit)
    }

    private suspend fun runLoop() {
        while (scope.isActive) {
            val d = scheduler.decide(nowElapsedMs(), minuteOfDay())
            if (d.scan) scanner.startScan() else scanner.stopScan()
            withTimeoutOrNull(d.recheckAfterMs) { wake.receive() }
        }
    }
}
