package red.kitsu.heartosc

import android.util.Log
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VRChatOSCSender(
    private val host: String,
    private val port: Int,
    private val pulseGenerator: HeartbeatPulseGenerator,
    private val hrParam: String,
    private val hrConnectedParam: String,
    private val heartbeatToggleParam: String,
    private val heartbeatPulseParam: String
) {
    companion object {
        private const val TAG = "VRChatOSCSender"
    }

    private var socket: DatagramSocket? = null
    private var currentHeartRate: Int? = null
    private var isConnected: Boolean = false
    private var toggleObserverJob: Job? = null
    private var pulseObserverJob: Job? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        try {
            socket = DatagramSocket()
            Log.d(TAG, "OSC sender initialized for $host:$port")
            observePulseGenerator()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create socket", e)
        }
    }

    private fun observePulseGenerator() {
        // Observe toggle state changes
        toggleObserverJob = scope.launch {
            pulseGenerator.toggleState.collect { toggleState ->
                sendBoolParameter(heartbeatToggleParam, toggleState)
                // Also send connection state with each heartbeat
                sendBoolParameter(hrConnectedParam, isConnected)
            }
        }

        // Observe pulse state changes
        pulseObserverJob = scope.launch {
            pulseGenerator.pulseState.collect { pulseState ->
                sendBoolParameter(heartbeatPulseParam, pulseState)
            }
        }
    }

    fun updateHeartRate(bpm: Int?) {
        if (currentHeartRate == bpm) return

        currentHeartRate = bpm

        // Send HR value
        bpm?.let {
            sendIntParameter(hrParam, it)
            Log.d(TAG, "Sent HR: $it bpm")
        }
    }

    fun updateConnectionState(connected: Boolean) {
        isConnected = connected
        sendBoolParameter(hrConnectedParam, isConnected)
        Log.d(TAG, "Sent isHRConnected: $isConnected")
    }

    private fun sendIntParameter(address: String, value: Int) {
        scope.launch {
            try {
                val message = buildOSCMessage(address, value)
                sendMessage(message)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send int parameter $address", e)
            }
        }
    }

    private fun sendBoolParameter(address: String, value: Boolean) {
        scope.launch {
            try {
                val message = buildOSCMessage(address, value)
                sendMessage(message)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send bool parameter $address", e)
            }
        }
    }

    private fun buildOSCMessage(address: String, value: Int): ByteArray {
        val addressBytes = padString(address)
        val typeTag = padString(",i")
        val valueBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(value).array()

        return addressBytes + typeTag + valueBytes
    }

    private fun buildOSCMessage(address: String, value: Boolean): ByteArray {
        val addressBytes = padString(address)
        // OSC uses 'T' for true, 'F' for false in type tag
        val typeTag = padString(if (value) ",T" else ",F")

        return addressBytes + typeTag
    }

    private fun padString(str: String): ByteArray {
        val bytes = str.toByteArray(Charsets.US_ASCII)
        val paddedSize = ((bytes.size + 4) / 4) * 4 // Round up to multiple of 4
        return bytes + ByteArray(paddedSize - bytes.size) // Pad with zeros
    }

    private suspend fun sendMessage(message: ByteArray) {
        withContext(Dispatchers.IO) {
            try {
                val address = InetAddress.getByName(host)
                val packet = DatagramPacket(message, message.size, address, port)
                socket?.send(packet)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send OSC message to $host:$port", e)
            }
        }
    }

    fun updateHostPort(newHost: String, newPort: Int) {
        // Recreation is handled by ViewModel when settings change
        Log.d(TAG, "Host/Port update requested: $newHost:$newPort")
    }

    fun cleanup() {
        Log.d(TAG, "Cleaning up OSC sender")
        toggleObserverJob?.cancel()
        pulseObserverJob?.cancel()
        scope.cancel()
        try {
            socket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing socket", e)
        }
        socket = null
    }
}
