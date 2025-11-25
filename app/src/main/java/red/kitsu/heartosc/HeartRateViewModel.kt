package red.kitsu.heartosc

import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private data class OscConfig(
    val host: String,
    val port: Int,
    val hrParam: String,
    val hrConnectedParam: String,
    val heartbeatToggleParam: String,
    val heartbeatPulseParam: String
)

class HeartRateViewModel(application: Application) : AndroidViewModel(application) {

    private val heartRateManager = HeartRateMonitorManager(application)
    private val settingsManager = SettingsManager(application)
    private val pulseGenerator = HeartbeatPulseGenerator(viewModelScope)
    private var oscSender: VRChatOSCSender? = null
    var heartRateService: HeartRateService? = null

    val connectionState = heartRateManager.connectionState
    val heartRate = heartRateManager.heartRate
    val energyExpended = heartRateManager.energyExpended
    val rrIntervals = heartRateManager.rrIntervals
    val discoveredDevices = heartRateManager.discoveredDevices
    val scanningState = heartRateManager.scanningState

    val oscHost = settingsManager.oscHost
    val oscPort = settingsManager.oscPort
    val hrParam = settingsManager.hrParam
    val hrConnectedParam = settingsManager.hrConnectedParam
    val heartbeatToggleParam = settingsManager.heartbeatToggleParam
    val heartbeatPulseParam = settingsManager.heartbeatPulseParam

    // Expose pulse state for UI
    val heartbeatPulse = pulseGenerator.pulseState

    init {
        // Initialize OSC sender with current settings
        viewModelScope.launch {
            combine(
                combine(oscHost, oscPort) { host, port -> Pair(host, port) },
                combine(hrParam, hrConnectedParam) { hr, hrConn -> Pair(hr, hrConn) },
                combine(heartbeatToggleParam, heartbeatPulseParam) { hbToggle, hbPulse -> Pair(hbToggle, hbPulse) }
            ) { hostPort, hrParams, hbParams ->
                OscConfig(
                    hostPort.first,
                    hostPort.second,
                    hrParams.first,
                    hrParams.second,
                    hbParams.first,
                    hbParams.second
                )
            }.collect { config ->
                // Recreate OSC sender when settings change
                oscSender?.cleanup()
                oscSender = VRChatOSCSender(
                    config.host,
                    config.port,
                    pulseGenerator,
                    config.hrParam,
                    config.hrConnectedParam,
                    config.heartbeatToggleParam,
                    config.heartbeatPulseParam
                )
            }
        }

        // Monitor heart rate changes and manage pulse generator
        viewModelScope.launch {
            heartRate.collect { bpm ->
                oscSender?.updateHeartRate(bpm)
                heartRateService?.updateHeartRate(bpm)

                // Start/stop pulse generator based on BPM
                if (bpm != null && bpm > 0) {
                    pulseGenerator.start(bpm)
                } else {
                    pulseGenerator.stop()
                }
            }
        }

        // Monitor connection state and send to OSC
        viewModelScope.launch {
            connectionState.collect { state ->
                val isConnected = state is HeartRateMonitorManager.ConnectionState.Connected ||
                                 state is HeartRateMonitorManager.ConnectionState.Discovering
                // During reconnection, maintain the last connected state for OSC
                val shouldSendConnected = isConnected || state is HeartRateMonitorManager.ConnectionState.Reconnecting
                oscSender?.updateConnectionState(shouldSendConnected)
                heartRateService?.updateConnectionState(shouldSendConnected)

                // Stop pulse generator when disconnected (but not when reconnecting)
                if (!isConnected && state !is HeartRateMonitorManager.ConnectionState.Reconnecting) {
                    pulseGenerator.stop()
                }
            }
        }
    }

    fun checkPermissions(): Boolean {
        return heartRateManager.checkPermissions()
    }

    fun isBluetoothEnabled(): Boolean {
        return heartRateManager.isBluetoothEnabled()
    }

    fun startScan() {
        viewModelScope.launch {
            heartRateManager.startScan()
        }
    }

    fun stopScan() {
        viewModelScope.launch {
            heartRateManager.stopScan()
        }
    }

    fun connectToDevice(device: BluetoothDevice) {
        viewModelScope.launch {
            heartRateManager.connectToDevice(device)
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            heartRateManager.disconnect()
        }
    }

    fun setOscHost(host: String) {
        settingsManager.setOscHost(host)
    }

    fun setOscPort(port: Int) {
        settingsManager.setOscPort(port)
    }

    fun setHrParam(param: String) {
        settingsManager.setHrParam(param)
    }

    fun setHrConnectedParam(param: String) {
        settingsManager.setHrConnectedParam(param)
    }

    fun setHeartbeatToggleParam(param: String) {
        settingsManager.setHeartbeatToggleParam(param)
    }

    fun setHeartbeatPulseParam(param: String) {
        settingsManager.setHeartbeatPulseParam(param)
    }

    override fun onCleared() {
        super.onCleared()
        pulseGenerator.cleanup()
        oscSender?.cleanup()
        heartRateManager.cleanup()
    }
}
