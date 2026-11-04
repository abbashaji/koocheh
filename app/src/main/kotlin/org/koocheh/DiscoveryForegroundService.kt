package org.koocheh

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import org.koocheh.model.ScanMode

/**
 * Owns background scanning. The notification text is generic on purpose:
 * no counts, no names, no identifiers.
 */
class DiscoveryForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopScanning()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        startInForeground()

        val requested = intent?.getStringExtra(EXTRA_MODE)
        val mode = ScanMode.entries.firstOrNull { it.name == requested }
        if (mode != null) {
            ServiceLocator.discoveryService.start(mode)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopScanning()
        super.onDestroy()
    }

    private fun stopScanning() {
        ServiceLocator.discoveryService.stop()
    }

    private fun startInForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_placeholder)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "background"
        private const val NOTIFICATION_ID = 1
        private const val EXTRA_MODE = "mode"
        private const val ACTION_STOP = "org.koocheh.action.STOP"

        fun start(context: Context, mode: ScanMode) {
            val intent = Intent(context, DiscoveryForegroundService::class.java)
                .putExtra(EXTRA_MODE, mode.name)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, DiscoveryForegroundService::class.java)
                .setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
