package org.koocheh.security

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

/**
 * Every notification is built here and carries only fixed, generic text.
 * No peer, tag or count content is accepted as input, so none can leak.
 *
 * [UNVERIFIED] Channel creation and lock-screen visibility have not been run
 * on a device.
 */
class NotificationPolicy(private val context: Context) {

    fun generic(): Notification {
        ensureChannel()
        return Notification.Builder(context, CHANNEL_ID)
            .setContentTitle(TITLE)
            .setContentText(TEXT)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setVisibility(Notification.VISIBILITY_SECRET)
            .setAutoCancel(true)
            .build()
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW)
        )
    }

    companion object {
        const val CHANNEL_ID = "general"
        const val CHANNEL_NAME = "General"
        const val TITLE = "Notification"
        const val TEXT = "You have an update"
    }
}
