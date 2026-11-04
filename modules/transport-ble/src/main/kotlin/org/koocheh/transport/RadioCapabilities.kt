package org.koocheh.transport

import android.bluetooth.BluetoothAdapter
import android.os.Build

/**
 * Detects optional radio features. Coded PHY is used only when BOTH phones
 * support it; peer support is learned from the peer's advertisement
 * (see PeerDirectory). Real indoor range is far below datasheet figures
 * [UNVERIFIED] and is measured in the pilot via RangeLog.
 */
class RadioCapabilities(private val adapter: BluetoothAdapter?) {

    fun supportsCodedPhy(): Boolean {
        val a = adapter ?: return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return runCatching { a.isLeCodedPhySupported }.getOrDefault(false)
    }

    /** True only if this phone and the peer both support Coded PHY. */
    fun shouldUseCoded(peerSupportsCoded: Boolean): Boolean =
        peerSupportsCoded && supportsCodedPhy()
}
