package red.kitsu.heartosc

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class HeartRateService : Service() {
    companion object {
        private const val TAG = "HeartRateService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "heart_rate_service_channel"
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_DISCONNECT = "ACTION_DISCONNECT"
        const val ACTION_NOTIFICATION_DISMISSED = "ACTION_NOTIFICATION_DISMISSED"

        @Volatile
        private var isServiceRunning = false

        fun isRunning(): Boolean = isServiceRunning
    }

    private val binder = LocalBinder()
    private var currentHeartRate: Int? = null
    private var isConnected: Boolean = false
    var onDisconnectRequested: (() -> Unit)? = null

    inner class LocalBinder : Binder() {
        fun getService(): HeartRateService = this@HeartRateService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called with action: ${intent?.action}")
        when (intent?.action) {
            ACTION_START -> {
                if (!isServiceRunning) {
                    Log.d(TAG, "Starting foreground service")
                    try {
                        val notification = createNotification()
                        Log.d(TAG, "Notification created, calling startForeground")
                        startForeground(NOTIFICATION_ID, notification)
                        isServiceRunning = true
                        Log.d(TAG, "startForeground called successfully")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error starting foreground service", e)
                    }
                } else {
                    Log.d(TAG, "Service already running, ignoring START action")
                }
            }
            ACTION_NOTIFICATION_DISMISSED -> {
                Log.d(TAG, "Notification dismissed by user, re-showing it")
                // Re-show the notification immediately
                if (isServiceRunning) {
                    try {
                        val notification = createNotification()
                        startForeground(NOTIFICATION_ID, notification)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error re-showing notification", e)
                    }
                }
            }
            ACTION_DISCONNECT -> {
                Log.d(TAG, "Disconnect action received from notification")
                onDisconnectRequested?.invoke()
            }
            ACTION_STOP -> {
                Log.d(TAG, "Stopping foreground service")
                isServiceRunning = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Create delete intent to detect when notification is dismissed
        val deleteIntent = Intent(this, HeartRateService::class.java).apply {
            action = ACTION_NOTIFICATION_DISMISSED
        }
        val deletePendingIntent = PendingIntent.getService(
            this,
            2,
            deleteIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Create disconnect action
        val disconnectIntent = Intent(this, HeartRateService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val disconnectPendingIntent = PendingIntent.getService(
            this,
            1,
            disconnectIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = if (isConnected) getString(R.string.notification_title_connected) else getString(R.string.notification_title_disconnected)
        val content = if (isConnected && currentHeartRate != null) {
            getString(R.string.notification_content_bpm, currentHeartRate)
        } else {
            getString(R.string.notification_content_waiting)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(pendingIntent)
            .setDeleteIntent(deletePendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .setUsesChronometer(false)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setLocalOnly(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.notification_action_disconnect),
                disconnectPendingIntent
            )
            .build()
    }

    fun updateHeartRate(bpm: Int?) {
        currentHeartRate = bpm
        updateNotification()
    }

    fun updateConnectionState(connected: Boolean) {
        isConnected = connected
        updateNotification()
    }

    private fun updateNotification() {
        if (isServiceRunning) {
            try {
                // For foreground services, we should use startForeground again to update
                val notification = createNotification()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // On Android 10+, we can update the foreground notification
                    startForeground(NOTIFICATION_ID, notification)
                } else {
                    // On older versions, use NotificationManager
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.notify(NOTIFICATION_ID, notification)
                }
                Log.d(TAG, "Notification updated")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating notification", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        Log.d(TAG, "Service destroyed")
    }
}
