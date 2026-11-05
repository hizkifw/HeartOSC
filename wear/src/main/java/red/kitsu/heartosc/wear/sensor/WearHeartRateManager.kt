package red.kitsu.heartosc.wear.sensor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.util.Log
import androidx.concurrent.futures.await
import androidx.core.content.ContextCompat
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DeltaDataType
import kotlinx.coroutines.*

class WearHeartRateManager(private val context: Context) : SensorEventListener {

    companion object {
        private const val TAG = "WearHeartRateManager"
        private const val NO_DATA_TIMEOUT_MS = 20_000L
        const val PERMISSION_HEALTH_READ_HEART_RATE = "android.permission.health.READ_HEART_RATE"
        const val PERMISSION_HEALTH_READ_HEALTH_DATA_IN_BACKGROUND = "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"

        fun hasBodySensorsPermission(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BODY_SENSORS
            ) == PackageManager.PERMISSION_GRANTED
        }

        fun hasHealthReadHeartRatePermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                ContextCompat.checkSelfPermission(
                    context,
                    PERMISSION_HEALTH_READ_HEART_RATE
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                false
            }
        }

        fun hasHealthServicesPermissions(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                hasBodySensorsPermission(context) || hasHealthReadHeartRatePermission(context)
            } else {
                hasBodySensorsPermission(context)
            }
        }
    }

    enum class Status {
        IDLE,
        WAITING,
        MEASURING,
        NO_DATA,
        UNAVAILABLE
    }

    private var onBpmListener: ((Int) -> Unit)? = null
    private var onStatusListener: ((Status) -> Unit)? = null
    @Volatile
    private var isTracking = false
    private var hasReceivedData = false
    private var watchdogJob: Job? = null

    // Health Services (Primary for Wear OS 3+ / One UI Watch)
    private val healthServicesClient by lazy { HealthServices.getClient(context) }
    private val measureClient by lazy { healthServicesClient.measureClient }
    private var isUsingHealthServices = false

    // Legacy SensorManager (Fallback)
    private val sensorManager by lazy { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    private var heartRateSensor: Sensor? = null

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var startJob: Job? = null
    private var unregisterJob: Job? = null

    private val measureCallback = object : MeasureCallback {
        override fun onRegistered() {
            Log.d(TAG, "MeasureClient callback successfully registered")
        }

        override fun onRegistrationFailed(throwable: Throwable) {
            Log.e(TAG, "MeasureClient registration failed asynchronously, falling back to SensorManager", throwable)
            if (!isTracking) return
            isUsingHealthServices = false
            startSensorManagerFallback()
        }

        override fun onDataReceived(data: DataPointContainer) {
            if (!isTracking) return
            val points = data.getData(DataType.HEART_RATE_BPM)
            for (point in points) {
                val bpm = point.value.toInt()
                if (bpm > 0) {
                    Log.d(TAG, "HealthServices HR BPM: $bpm")
                    deliverBpm(bpm)
                }
            }
        }

        override fun onAvailabilityChanged(
            dataType: DeltaDataType<*, *>,
            availability: Availability
        ) {
            Log.d(TAG, "HealthServices availability changed for $dataType: $availability")
        }
    }

    fun start(onBpm: (Int) -> Unit, onStatus: (Status) -> Unit = {}) {
        if (isTracking) return
        isTracking = true
        hasReceivedData = false
        onBpmListener = onBpm
        onStatusListener = onStatus
        setStatus(Status.WAITING)

        startJob?.cancel()
        startJob = scope.launch {
            unregisterJob?.join()
            unregisterJob = null

            if (hasHealthServicesPermissions(context)) {
                try {
                    val capabilities = measureClient.getCapabilitiesAsync().await()
                    if (!isTracking) return@launch
                    if (DataType.HEART_RATE_BPM in capabilities.supportedDataTypesMeasure) {
                        Log.d(TAG, "Registering MeasureClient for HEART_RATE_BPM")
                        measureClient.registerMeasureCallback(
                            DataType.HEART_RATE_BPM,
                            measureCallback
                        )
                        isUsingHealthServices = true
                        scheduleNoDataWatchdog()
                        return@launch
                    } else {
                        Log.w(TAG, "HEART_RATE_BPM not supported in measureClient capabilities")
                    }
                } catch (e: CancellationException) {
                    // Either stop() cancelled this job, or the Health Services future itself was
                    // cancelled (e.g. the service is unavailable). Only the former should abort.
                    if (!isActive) {
                        Log.d(TAG, "HealthServices registration cancelled")
                        return@launch
                    }
                    Log.w(TAG, "HealthServices request was cancelled, falling back to SensorManager", e)
                } catch (e: SecurityException) {
                    Log.w(TAG, "SecurityException registering HealthServices, falling back to SensorManager", e)
                } catch (e: Throwable) {
                    Log.w(TAG, "HealthServices register failed, falling back to SensorManager", e)
                }
            } else {
                Log.w(TAG, "Missing Health Services permissions (BODY_SENSORS / READ_HEART_RATE), bypassing HealthServices and trying SensorManager fallback")
            }

            if (!isTracking) return@launch
            // Fallback to standard SensorManager
            startSensorManagerFallback()
        }
    }

    private fun setStatus(status: Status) {
        onStatusListener?.invoke(status)
    }

    private fun deliverBpm(bpm: Int) {
        if (!hasReceivedData) {
            hasReceivedData = true
            watchdogJob?.cancel()
            watchdogJob = null
            setStatus(Status.MEASURING)
        }
        onBpmListener?.invoke(bpm)
    }

    /**
     * If the active source has not produced a reading within [NO_DATA_TIMEOUT_MS], switch from
     * Health Services to SensorManager (when possible), or report [Status.NO_DATA].
     */
    private fun scheduleNoDataWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            delay(NO_DATA_TIMEOUT_MS)
            if (!isTracking || hasReceivedData) return@launch
            if (isUsingHealthServices && canUseSensorManagerFallback()) {
                Log.w(TAG, "No Health Services data after ${NO_DATA_TIMEOUT_MS}ms, falling back to SensorManager")
                unregisterHealthServices()
                startSensorManagerFallback()
            } else {
                Log.w(TAG, "No heart rate data received after ${NO_DATA_TIMEOUT_MS}ms")
                setStatus(Status.NO_DATA)
            }
        }
    }

    private fun canUseSensorManagerFallback(): Boolean {
        return hasBodySensorsPermission(context) &&
            sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE) != null
    }

    private fun startSensorManagerFallback() {
        isUsingHealthServices = false
        watchdogJob?.cancel()
        watchdogJob = null
        if (!hasBodySensorsPermission(context)) {
            Log.e(TAG, "Cannot start SensorManager fallback: BODY_SENSORS permission not granted")
            setStatus(Status.UNAVAILABLE)
            return
        }
        try {
            val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
            if (sensor == null) {
                Log.e(TAG, "No heart rate sensor available on device")
                setStatus(Status.UNAVAILABLE)
                return
            }
            if (!isTracking) return
            heartRateSensor = sensor
            if (!sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)) {
                Log.e(TAG, "SensorManager rejected heart rate listener registration")
                setStatus(Status.UNAVAILABLE)
                return
            }
            Log.d(TAG, "Started tracking with legacy SensorManager")
            scheduleNoDataWatchdog()
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting legacy SensorManager listener", e)
            setStatus(Status.UNAVAILABLE)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting legacy SensorManager listener", e)
            setStatus(Status.UNAVAILABLE)
        }
    }

    private fun unregisterHealthServices() {
        if (!isUsingHealthServices) return
        isUsingHealthServices = false
        unregisterJob = scope.launch {
            try {
                measureClient.unregisterMeasureCallbackAsync(
                    DataType.HEART_RATE_BPM,
                    measureCallback
                ).await()
                Log.d(TAG, "Unregistered MeasureClient callback")
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException unregistering MeasureClient callback", e)
            } catch (e: Throwable) {
                Log.e(TAG, "Error unregistering MeasureClient callback", e)
            }
        }
    }

    fun stop() {
        if (!isTracking) return
        isTracking = false

        startJob?.cancel()
        startJob = null
        watchdogJob?.cancel()
        watchdogJob = null

        unregisterHealthServices()

        try {
            sensorManager.unregisterListener(this)
            Log.d(TAG, "Unregistered legacy SensorManager listener")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException unregistering legacy SensorManager listener", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering legacy SensorManager listener", e)
        }
        onBpmListener = null
        onStatusListener = null
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!isUsingHealthServices && isTracking && event?.sensor?.type == Sensor.TYPE_HEART_RATE) {
            try {
                val bpm = event.values[0].toInt()
                if (bpm > 0) {
                    Log.d(TAG, "SensorManager HR BPM: $bpm")
                    deliverBpm(bpm)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing sensor event", e)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
