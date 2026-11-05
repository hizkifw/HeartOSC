package red.kitsu.heartosc.wear.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import red.kitsu.heartosc.wear.WearMainActivity
import red.kitsu.heartosc.wear.sensor.WearHeartRateManager
import java.nio.ByteBuffer

class WearHeartRateService : Service() {

    companion object {
        private const val TAG = "WearHeartRateService"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "wear_heart_rate_channel"
        private const val WEAR_PATH_HR = "/heartrate"
        private const val NODE_CACHE_TTL_MS = 30_000L

        const val ACTION_START = "red.kitsu.heartosc.wear.ACTION_START"
        const val ACTION_STOP = "red.kitsu.heartosc.wear.ACTION_STOP"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _currentBpm = MutableStateFlow(0)
        val currentBpm: StateFlow<Int> = _currentBpm.asStateFlow()

        private val _sensorStatus = MutableStateFlow(WearHeartRateManager.Status.IDLE)
        val sensorStatus: StateFlow<WearHeartRateManager.Status> = _sensorStatus.asStateFlow()
    }

    private val binder = LocalBinder()
    private var wakeLock: PowerManager.WakeLock? = null
    private var sensorManager: WearHeartRateManager? = null

    // Cached companion node IDs so we don't query connected nodes for every BPM sample.
    private var cachedNodeIds: List<String> = emptyList()
    private var nodesFetchedAtMs = 0L
    private var nodeRefreshInFlight = false

    inner class LocalBinder : Binder() {
        fun getService(): WearHeartRateService = this@WearHeartRateService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        sensorManager = WearHeartRateManager(this)
        Log.d(TAG, "WearHeartRateService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand action: ${intent?.action}")
        when (intent?.action) {
            ACTION_START -> startForegroundTracking()
            ACTION_STOP -> stopForegroundTracking()
            else -> {
                // Not started by us with an action (e.g. a stale restart). A health foreground
                // service can't be resumed from the background, so don't linger idle.
                if (!_isRunning.value) stopSelf(startId)
            }
        }
        // Tracking is always started explicitly from the activity; don't let the system
        // recreate the service in a half-started state after the process is killed.
        return START_NOT_STICKY
    }

    private fun startForegroundTracking() {
        if (_isRunning.value) return

        val notification = createNotification(0)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            _isRunning.value = true
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting foreground health service", e)
            _isRunning.value = false
            stopSelf()
            return
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground tracking service", e)
            _isRunning.value = false
            stopSelf()
            return
        }

        acquireWakeLock()

        sensorManager?.start(
            onBpm = { bpm ->
                _currentBpm.value = bpm
                updateNotification(bpm)
                sendHeartRateToPhone(bpm)
            },
            onStatus = { status -> _sensorStatus.value = status }
        )
        Log.d(TAG, "Started foreground tracking on Wear OS")
    }

    private fun stopForegroundTracking() {
        if (!_isRunning.value) return

        sensorManager?.stop()
        releaseWakeLock()

        _isRunning.value = false
        _currentBpm.value = 0
        _sensorStatus.value = WearHeartRateManager.Status.IDLE
        cachedNodeIds = emptyList()
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping foreground service", e)
        }
        stopSelf()
        Log.d(TAG, "Stopped foreground tracking on Wear OS")
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "HeartOSC::WearTrackingWakeLock"
            ).apply {
                acquire(10 * 60 * 60 * 1000L) // 10 hours max timeout
            }
            Log.d(TAG, "Acquired partial wake lock for Wear OS background streaming")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing wake lock", e)
        }
        wakeLock = null
        Log.d(TAG, "Released wake lock")
    }

    private fun sendHeartRateToPhone(bpm: Int) {
        if (!_isRunning.value) return
        val payload = ByteBuffer.allocate(4).putInt(bpm).array()

        val cacheFresh = SystemClock.elapsedRealtime() - nodesFetchedAtMs < NODE_CACHE_TTL_MS
        if (cachedNodeIds.isNotEmpty() && cacheFresh) {
            sendToNodes(cachedNodeIds, payload, bpm)
            return
        }

        // Drop this sample if a lookup is already running; the next one (~1s later) will go out.
        if (nodeRefreshInFlight) return
        nodeRefreshInFlight = true
        Wearable.getNodeClient(this).connectedNodes
            .addOnSuccessListener { nodes ->
                cachedNodeIds = nodes.map { it.id }
                nodesFetchedAtMs = SystemClock.elapsedRealtime()
                if (_isRunning.value) sendToNodes(cachedNodeIds, payload, bpm)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed getting connected nodes", e)
            }
            .addOnCompleteListener {
                nodeRefreshInFlight = false
            }
    }

    private fun sendToNodes(nodeIds: List<String>, payload: ByteArray, bpm: Int) {
        val messageClient = Wearable.getMessageClient(this)
        for (nodeId in nodeIds) {
            messageClient.sendMessage(nodeId, WEAR_PATH_HR, payload)
                .addOnSuccessListener {
                    Log.d(TAG, "Sent BPM $bpm to companion node: $nodeId")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed sending BPM to node $nodeId", e)
                    // Node may have disconnected; force a fresh lookup on the next sample.
                    cachedNodeIds = emptyList()
                }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "HeartOSC Wear Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Continuous Heart Rate Streaming Service for Wear OS"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(bpm: Int): Notification {
        val intent = Intent(this, WearMainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, WearHeartRateService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val contentText = if (bpm > 0) "Streaming: $bpm BPM" else "Measuring Heart Rate..."

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("HeartOSC Wear")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopPendingIntent
            )
            .build()
    }

    private fun updateNotification(bpm: Int) {
        if (!_isRunning.value) return
        try {
            val notification = createNotification(bpm)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating notification", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForegroundTracking()
        Log.d(TAG, "WearHeartRateService destroyed")
    }
}
