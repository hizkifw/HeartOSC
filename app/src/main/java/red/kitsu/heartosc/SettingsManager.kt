package red.kitsu.heartosc

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "heart_osc_settings"
        private const val KEY_OSC_HOST = "osc_host"
        private const val KEY_OSC_PORT = "osc_port"
        private const val KEY_HR_PARAM = "hr_param"
        private const val KEY_HR_CONNECTED_PARAM = "hr_connected_param"
        private const val KEY_HEARTBEAT_TOGGLE_PARAM = "heartbeat_toggle_param"
        private const val KEY_HEARTBEAT_PULSE_PARAM = "heartbeat_pulse_param"
        private const val KEY_HEARTBEAT_PULSE_DURATION = "heartbeat_pulse_duration"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"

        const val DEFAULT_OSC_HOST = "192.168.1.10"
        const val DEFAULT_OSC_PORT = 9000
        const val DEFAULT_HR_PARAM = "/avatar/parameters/HR"
        const val DEFAULT_HR_CONNECTED_PARAM = "/avatar/parameters/isHRConnected"
        const val DEFAULT_HEARTBEAT_TOGGLE_PARAM = "/avatar/parameters/HeartBeatToggle"
        const val DEFAULT_HEARTBEAT_PULSE_PARAM = "/avatar/parameters/isHRBeat"
        const val DEFAULT_HEARTBEAT_PULSE_DURATION = 200 // milliseconds
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _oscHost = MutableStateFlow(prefs.getString(KEY_OSC_HOST, DEFAULT_OSC_HOST) ?: DEFAULT_OSC_HOST)
    val oscHost: StateFlow<String> = _oscHost.asStateFlow()

    private val _oscPort = MutableStateFlow(prefs.getInt(KEY_OSC_PORT, DEFAULT_OSC_PORT))
    val oscPort: StateFlow<Int> = _oscPort.asStateFlow()

    private val _hrParam = MutableStateFlow(prefs.getString(KEY_HR_PARAM, DEFAULT_HR_PARAM) ?: DEFAULT_HR_PARAM)
    val hrParam: StateFlow<String> = _hrParam.asStateFlow()

    private val _hrConnectedParam = MutableStateFlow(prefs.getString(KEY_HR_CONNECTED_PARAM, DEFAULT_HR_CONNECTED_PARAM) ?: DEFAULT_HR_CONNECTED_PARAM)
    val hrConnectedParam: StateFlow<String> = _hrConnectedParam.asStateFlow()

    private val _heartbeatToggleParam = MutableStateFlow(prefs.getString(KEY_HEARTBEAT_TOGGLE_PARAM, DEFAULT_HEARTBEAT_TOGGLE_PARAM) ?: DEFAULT_HEARTBEAT_TOGGLE_PARAM)
    val heartbeatToggleParam: StateFlow<String> = _heartbeatToggleParam.asStateFlow()

    private val _heartbeatPulseParam = MutableStateFlow(prefs.getString(KEY_HEARTBEAT_PULSE_PARAM, DEFAULT_HEARTBEAT_PULSE_PARAM) ?: DEFAULT_HEARTBEAT_PULSE_PARAM)
    val heartbeatPulseParam: StateFlow<String> = _heartbeatPulseParam.asStateFlow()

    private val _heartbeatPulseDuration = MutableStateFlow(prefs.getInt(KEY_HEARTBEAT_PULSE_DURATION, DEFAULT_HEARTBEAT_PULSE_DURATION))
    val heartbeatPulseDuration: StateFlow<Int> = _heartbeatPulseDuration.asStateFlow()

    fun setOscHost(host: String) {
        _oscHost.value = host
        prefs.edit().putString(KEY_OSC_HOST, host).apply()
    }

    fun setOscPort(port: Int) {
        _oscPort.value = port
        prefs.edit().putInt(KEY_OSC_PORT, port).apply()
    }

    fun setHrParam(param: String) {
        _hrParam.value = param
        prefs.edit().putString(KEY_HR_PARAM, param).apply()
    }

    fun setHrConnectedParam(param: String) {
        _hrConnectedParam.value = param
        prefs.edit().putString(KEY_HR_CONNECTED_PARAM, param).apply()
    }

    fun setHeartbeatToggleParam(param: String) {
        _heartbeatToggleParam.value = param
        prefs.edit().putString(KEY_HEARTBEAT_TOGGLE_PARAM, param).apply()
    }

    fun setHeartbeatPulseParam(param: String) {
        _heartbeatPulseParam.value = param
        prefs.edit().putString(KEY_HEARTBEAT_PULSE_PARAM, param).apply()
    }

    fun setHeartbeatPulseDuration(duration: Int) {
        _heartbeatPulseDuration.value = duration
        prefs.edit().putInt(KEY_HEARTBEAT_PULSE_DURATION, duration).apply()
    }

    fun isOnboardingCompleted(): Boolean {
        return prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    fun setOnboardingCompleted() {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply()
    }
}
