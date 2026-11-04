package org.koocheh.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.os.ParcelUuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koocheh.model.EphemeralId
import java.util.UUID
import kotlin.random.Random

/**
 * Every wire-level constant lives here, in one place.
 *
 * FINGERPRINTING SURFACE: a fixed app service UUID lets anyone running a
 * scanner enumerate Koocheh phones. The tracking test must review this
 * constant and the GATT characteristic UUIDs below. [UNVERIFIED] that the
 * chosen 16-bit value does not collide with an assigned number.
 */
object BleProtocol {
    val APP_SERVICE_UUID: UUID = UUID.fromString("0000fa11-0000-1000-8000-00805f9b34fb")
    val REQUEST_CHAR_UUID: UUID = UUID.fromString("0000fa12-0000-1000-8000-00805f9b34fb")
    val RESPONSE_CHAR_UUID: UUID = UUID.fromString("0000fa13-0000-1000-8000-00805f9b34fb")
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /** Legacy adv: 31 bytes - 3 (flags) - 4 (service data header) = 24. */
    const val MAX_ID_BYTES = 24
    const val MAX_EXCHANGE_BYTES = 4096
}

/**
 * Advertises one EphemeralId at a time. With both contexts enabled the two
 * slots alternate with randomised dwell; they are never advertised together.
 */
class BleAdvertiser(
    private val adapter: BluetoothAdapter,
    private val scope: CoroutineScope,
    private val random: Random = Random.Default,
    private val dwellMs: LongRange = 20_000L..60_000L,
) {
    private val lock = Any()
    private val ids = arrayOfNulls<EphemeralId>(SLOTS)
    private var job: Job? = null
    private var callback: AdvertiseCallback? = null
    private val wake = Channel<Unit>(Channel.CONFLATED)

    fun start() = synchronized(lock) {
        if (job == null) job = scope.launch { loop() }
    }

    fun stop() {
        synchronized(lock) { job?.cancel(); job = null }
        stopAdvertising()
    }

    /** Set (or clear with null) the id for a context slot. Also the rotation hook. */
    fun setId(slot: Int, id: EphemeralId?) {
        require(slot in 0 until SLOTS)
        if (id != null) require(id.bytes.size in 1..BleProtocol.MAX_ID_BYTES)
        synchronized(lock) { ids[slot] = id }
        wake.trySend(Unit)
    }

    private suspend fun loop() {
        var slot = -1
        var shown: EphemeralId? = null
        while (scope.isActive) {
            val (next, id) = synchronized(lock) { pick(slot) }
            if (id == null) {
                stopAdvertising(); shown = null; slot = -1
                wake.receive()
                continue
            }
            if (id !== shown) { advertise(id); shown = id }
            slot = next
            val multiple = synchronized(lock) { ids.count { it != null } > 1 }
            if (multiple) withTimeoutOrNull(random.nextLong(dwellMs.first, dwellMs.last + 1)) { wake.receive() }
            else wake.receive()
        }
    }

    /** Next non-null slot after [after], wrapping; or the same slot if updated in place. */
    private fun pick(after: Int): Pair<Int, EphemeralId?> {
        for (step in 1..SLOTS) {
            val s = (after + step + SLOTS) % SLOTS
            ids[s]?.let { return s to it }
        }
        return -1 to null
    }

    @SuppressLint("MissingPermission")
    private fun advertise(id: EphemeralId) {
        stopAdvertising()
        val advertiser = adapter.bluetoothLeAdvertiser ?: return
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceData(ParcelUuid(BleProtocol.APP_SERVICE_UUID), id.bytes)
            .build()
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .setTimeout(0)
            .build()
        val cb = object : AdvertiseCallback() {}
        callback = cb
        advertiser.startAdvertising(settings, data, cb)
    }

    @SuppressLint("MissingPermission")
    private fun stopAdvertising() {
        val cb = callback ?: return
        callback = null
        adapter.bluetoothLeAdvertiser?.stopAdvertising(cb)
    }

    private companion object { const val SLOTS = 2 }
}
