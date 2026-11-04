package org.koocheh.invites

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Hands the installed APK to another phone through the system share sheet, preferring the
 * Bluetooth target. Wi-Fi Direct bulk transfer is v1.5 and out of scope. No network code.
 */
class ApkShare(private val context: Context) {

    /** Copies the installed APK to cache and starts a Bluetooth share, else the chooser. */
    fun share() {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.apkshare", stageApk())
        val send = Intent(Intent.ACTION_SEND).apply {
            type = APK_MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val bluetooth = context.packageManager
            .queryIntentActivities(send, 0)
            .firstOrNull { it.activityInfo.packageName in BLUETOOTH_PACKAGES }
        val launch = if (bluetooth != null) {
            send.setPackage(bluetooth.activityInfo.packageName)
        } else {
            Intent.createChooser(send, null)
        }
        context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun stageApk(): File {
        val dir = File(context.cacheDir, "shared-apk").apply { mkdirs() }
        val target = File(dir, "koocheh.apk")
        File(context.applicationInfo.sourceDir).copyTo(target, overwrite = true)
        return target
    }

    private companion object {
        const val APK_MIME = "application/vnd.android.package-archive"
        val BLUETOOTH_PACKAGES = setOf("com.android.bluetooth", "com.google.android.bluetooth")
    }
}
