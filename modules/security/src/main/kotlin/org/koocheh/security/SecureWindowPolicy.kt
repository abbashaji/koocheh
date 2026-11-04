package org.koocheh.security

import android.view.Window
import android.view.WindowManager

/**
 * Applies FLAG_SECURE so the window is excluded from screenshots, screen
 * recording and the recents thumbnail. Call before setContentView on every
 * sensitive screen.
 *
 * [UNVERIFIED] Behaviour on real devices (OEM recents handling, screen-capture
 * APIs, accessibility services) has not been run on a device.
 */
class SecureWindowPolicy {
    fun apply(window: Window) {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
    }
}
