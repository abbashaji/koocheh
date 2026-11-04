package org.koocheh.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Server half of a GATT exchange: reassembles a request (max 4 KB), calls
 * [handler], and notifies the framed response in MTU-sized chunks.
 * Addresses are keyed in memory only for the life of a connection.
 */
@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
class GattResponder(
    private val context: Context,
    private val scope: CoroutineScope,
    private val handler: suspend (ByteArray) -> ByteArray,
    private val timeoutMs: Long = 10_000L,
) {
    private val reassemblers = ConcurrentHashMap<String, Reassembler>()
    private val mtus = ConcurrentHashMap<String, Int>()
    private val sent = Channel<Int>(Channel.UNLIMITED)
    private val notifyLock = Mutex()
    private var server: BluetoothGattServer? = null
    private lateinit var response: BluetoothGattCharacteristic

    private val callback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(d: BluetoothDevice, status: Int, newState: Int) {
            if (newState == 0) { reassemblers.remove(d.address); mtus.remove(d.address) }
        }

        override fun onMtuChanged(d: BluetoothDevice, mtu: Int) { mtus[d.address] = mtu }

        override fun onDescriptorWriteRequest(
            d: BluetoothDevice, id: Int, desc: BluetoothGattDescriptor,
            prepared: Boolean, needsResponse: Boolean, offset: Int, value: ByteArray?,
        ) {
            if (needsResponse) server?.sendResponse(d, id, BluetoothGatt.GATT_SUCCESS, 0, null)
        }

        override fun onCharacteristicWriteRequest(
            d: BluetoothDevice, id: Int, c: BluetoothGattCharacteristic,
            prepared: Boolean, needsResponse: Boolean, offset: Int, value: ByteArray?,
        ) {
            val ok = try {
                reassemblers.getOrPut(d.address) { Reassembler() }.feed(value ?: ByteArray(0))
                    ?.let { request -> scope.launch { respond(d, request) } }
                true
            } catch (e: IOException) {
                reassemblers.remove(d.address)
                false
            }
            if (needsResponse) {
                val status = if (ok) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE
                server?.sendResponse(d, id, status, 0, null)
            }
        }

        override fun onNotificationSent(d: BluetoothDevice, status: Int) { sent.trySend(status) }
    }

    fun start() {
        if (server != null) return
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val s = manager.openGattServer(context, callback) ?: return
        val service = BluetoothGattService(BleProtocol.APP_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        service.addCharacteristic(BluetoothGattCharacteristic(
            BleProtocol.REQUEST_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE,
        ))
        response = BluetoothGattCharacteristic(
            BleProtocol.RESPONSE_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ,
        ).also {
            it.addDescriptor(BluetoothGattDescriptor(
                BleProtocol.CCCD_UUID,
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE,
            ))
        }
        service.addCharacteristic(response)
        s.addService(service)
        server = s
    }

    fun stop() {
        server?.close()
        server = null
        reassemblers.clear()
        mtus.clear()
    }

    private suspend fun respond(device: BluetoothDevice, request: ByteArray) {
        val reply = handler(request)
        val mtu = mtus[device.address] ?: 23
        notifyLock.withLock {
            for (chunk in Framing.chunk(reply, mtu)) {
                response.value = chunk
                if (server?.notifyCharacteristicChanged(device, response, false) != true) return
                val status = withTimeoutOrNull(timeoutMs) { sent.receive() } ?: return
                if (status != BluetoothGatt.GATT_SUCCESS) return
            }
        }
    }
}
