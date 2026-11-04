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
