package org.koocheh.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.koocheh.model.Sighting
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer

interface PeerLink {
    suspend fun open(sighting: Sighting): PeerChannel
}

/** Request/response channel; at most 4096 bytes per exchange. */
interface PeerChannel {
    suspend fun send(bytes: ByteArray)
    suspend fun receive(): ByteArray
    fun close()
}

/** Wire framing: 4-byte big-endian length, then payload, split to the MTU. */
object Framing {
    const val HEADER_BYTES = 4
    private const val ATT_OVERHEAD = 3

    fun chunk(payload: ByteArray, mtu: Int): List<ByteArray> {
        require(payload.size <= BleProtocol.MAX_EXCHANGE_BYTES) { "exchange too large" }
        val size = mtu - ATT_OVERHEAD
        require(size > 0) { "mtu too small" }
        val framed = ByteBuffer.allocate(HEADER_BYTES + payload.size).putInt(payload.size).put(payload).array()
        return (framed.indices step size).map { framed.copyOfRange(it, minOf(it + size, framed.size)) }
    }
}

/** Rebuilds one framed message from chunks. Throws IOException on bad framing. */
class Reassembler {
    private val buf = ByteArrayOutputStream()

    fun feed(chunk: ByteArray): ByteArray? {
        buf.write(chunk)
        val all = buf.toByteArray()
        if (all.size < Framing.HEADER_BYTES) return null
        val len = ByteBuffer.wrap(all).int
        if (len < 0 || len > BleProtocol.MAX_EXCHANGE_BYTES) throw IOException("bad frame length")
        val have = all.size - Framing.HEADER_BYTES
        if (have > len) throw IOException("frame overrun")
        if (have < len) return null
        buf.reset()
        return all.copyOfRange(Framing.HEADER_BYTES, all.size)
    }
}

/** Opens a GATT client channel to a recently sighted peer. No network code. */
class GattPeerLink(
    context: Context,
    private val directory: PeerDirectory,
    private val radio: RadioCapabilities,
    private val timeoutMs: Long = 10_000L,
) : PeerLink {
    private val appContext = context.applicationContext

    override suspend fun open(sighting: Sighting): PeerChannel {
        val entry = directory.find(sighting.ephemeralId) ?: throw IOException("peer not in range")
        val channel = GattPeerChannel(appContext, entry, radio, timeoutMs)
        try {
            channel.connect()
        } catch (t: Throwable) {
            channel.close()
            throw t
        }
        return channel
    }
}

private sealed interface GattEvent {
    data class Connected(val status: Int) : GattEvent
    data class Mtu(val mtu: Int, val status: Int) : GattEvent
    data class Services(val status: Int) : GattEvent
    data class DescriptorWritten(val status: Int) : GattEvent
    data class CharWritten(val status: Int) : GattEvent
    object Disconnected : GattEvent
}

@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
internal class GattPeerChannel(
    private val context: Context,
    private val entry: PeerDirectory.Entry,
    private val radio: RadioCapabilities,
    private val timeoutMs: Long,
) : PeerChannel {
    private val events = Channel<GattEvent>(Channel.UNLIMITED)
    private val inbox = Channel<ByteArray>(Channel.UNLIMITED)
    private val reassembler = Reassembler()
    private val sendLock = Mutex()
    private var gatt: BluetoothGatt? = null
    private var request: BluetoothGattCharacteristic? = null
    private var mtu = DEFAULT_MTU

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                events.trySend(GattEvent.Connected(status))
            } else {
                events.trySend(GattEvent.Disconnected)
                inbox.close(IOException("disconnected"))
            }
        }
        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            events.trySend(GattEvent.Mtu(mtu, status))
        }
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            events.trySend(GattEvent.Services(status))
        }
        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            events.trySend(GattEvent.DescriptorWritten(status))
        }
        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            events.trySend(GattEvent.CharWritten(status))
        }
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            try {
                reassembler.feed(c.value ?: return)?.let { inbox.trySend(it) }
            } catch (e: IOException) {
                inbox.close(e)
            }
        }
    }

    private suspend fun <T : GattEvent> await(type: Class<T>): T = withTimeout(timeoutMs) {
        while (true) {
            val e = events.receive()
            if (type.isInstance(e)) return@withTimeout type.cast(e)!!
            if (e is GattEvent.Disconnected) throw IOException("disconnected")
        }
        @Suppress("UNREACHABLE_CODE") error("unreachable")
    }

    suspend fun connect() {
        val g = entry.device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            ?: throw IOException("connect failed")
        gatt = g
        await(GattEvent.Connected::class.java)
        if (radio.shouldUseCoded(entry.peerSupportsCoded)) {
            // Best effort; the stack falls back to 1M if the link refuses.
            g.setPreferredPhy(
                BluetoothDevice.PHY_LE_CODED_MASK, BluetoothDevice.PHY_LE_CODED_MASK,
                BluetoothDevice.PHY_OPTION_NO_PREFERRED,
            )
        }
        if (g.requestMtu(REQUESTED_MTU)) {
            val m = await(GattEvent.Mtu::class.java)
            if (m.status == BluetoothGatt.GATT_SUCCESS) mtu = m.mtu
        }
        if (!g.discoverServices()) throw IOException("discovery failed")
        if (await(GattEvent.Services::class.java).status != BluetoothGatt.GATT_SUCCESS) {
            throw IOException("discovery failed")
        }
        val service = g.getService(BleProtocol.APP_SERVICE_UUID) ?: throw IOException("no service")
        request = service.getCharacteristic(BleProtocol.REQUEST_CHAR_UUID) ?: throw IOException("no request char")
        val response = service.getCharacteristic(BleProtocol.RESPONSE_CHAR_UUID) ?: throw IOException("no response char")
        g.setCharacteristicNotification(response, true)
        val cccd = response.getDescriptor(BleProtocol.CCCD_UUID) ?: throw IOException("no cccd")
        cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        if (!g.writeDescriptor(cccd)) throw IOException("subscribe failed")
        if (await(GattEvent.DescriptorWritten::class.java).status != BluetoothGatt.GATT_SUCCESS) {
            throw IOException("subscribe failed")
        }
    }

    override suspend fun send(bytes: ByteArray) = sendLock.withLock {
        val g = gatt ?: throw IOException("closed")
        val c = request ?: throw IOException("not connected")
        for (chunk in Framing.chunk(bytes, mtu)) {
            c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            c.value = chunk
            if (!g.writeCharacteristic(c)) throw IOException("write failed")
            if (await(GattEvent.CharWritten::class.java).status != BluetoothGatt.GATT_SUCCESS) {
                throw IOException("write failed")
            }
        }
    }

    override suspend fun receive(): ByteArray = try {
        withTimeout(timeoutMs) { inbox.receive() }
    } catch (e: ClosedReceiveChannelException) {
        throw IOException("closed")
    }

    override fun close() {
        gatt?.let { it.disconnect(); it.close() }
        gatt = null
        events.close()
        inbox.close()
    }

    private companion object {
        const val DEFAULT_MTU = 23
        const val REQUESTED_MTU = 247
    }
}
