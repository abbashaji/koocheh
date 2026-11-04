package org.koocheh.invites

import android.content.Context
import android.content.pm.PackageManager
import java.security.MessageDigest

/** Reads this APK's signing certificate fingerprint (SHA-256) for on-screen comparison. */
class SigningFingerprint(private val context: Context) {

    /** Colon-separated uppercase hex, e.g. "AB:CD:...". Empty string if unavailable. */
    fun current(): String {
        val cert = signingCertBytes() ?: return ""
        val digest = MessageDigest.getInstance("SHA-256").digest(cert)
        return digest.joinToString(":") { "%02X".format(it) }
    }

    @Suppress("DEPRECATION")
    private fun signingCertBytes(): ByteArray? = try {
        val pm = context.packageManager
        val name = context.packageName
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            val info = pm.getPackageInfo(name, PackageManager.GET_SIGNING_CERTIFICATES)
            info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
        } else {
            val info = pm.getPackageInfo(name, PackageManager.GET_SIGNATURES)
            info.signatures?.firstOrNull()?.toByteArray()
        }
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }
}
