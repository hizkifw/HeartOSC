package red.kitsu.heartosc

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HeartbeatPulseGenerator(
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "HeartbeatPulseGenerator"
        private const val DEFAULT_PULSE_DURATION_MS = 200L
        private const val FRAME_DELAY_MS = 16L // ~60 FPS update rate
        private const val MIN_BPM = 30
        private const val MAX_BPM = 220
    }

    private var animationJob: Job? = null

    @Volatile
    private var currentBpm: Int = 0

    @Volatile
    private var pulseDurationMs: Long = DEFAULT_PULSE_DURATION_MS

    @Volatile
    private var nextPulseTime: Long = 0L

    @Volatile
    private var pulseEndTime: Long = 0L

    @Volatile
    private var lastBpmChange: Long = 0L

    private val _toggleState = MutableStateFlow(false)
    val toggleState: StateFlow<Boolean> = _toggleState.asStateFlow()

    private val _pulseState = MutableStateFlow(false)
    val pulseState: StateFlow<Boolean> = _pulseState.asStateFlow()

    init {
        // Start the animation loop immediately
        startAnimationLoop()
    }

    private fun startAnimationLoop() {
        animationJob = scope.launch {
            var toggleValue = false

            while (isActive) {
                val now = System.currentTimeMillis()

                // Check if we should pulse
                if (currentBpm > 0 && now >= nextPulseTime) {
                    val intervalMs = (60000.0 / currentBpm).toLong()

                    // Start pulse
                    toggleValue = !toggleValue
                    _toggleState.value = toggleValue
                    _pulseState.value = true
                    pulseEndTime = now + pulseDurationMs

                    // Calculate next pulse time based on current pulse time
                    nextPulseTime += intervalMs

                    // If we're too far behind (missed beats), reset to now
                    if (nextPulseTime < now) {
                        nextPulseTime = now + intervalMs
                        Log.d(TAG, "Pulse timing reset - caught up to current time")
                    }

                    Log.d(TAG, "Pulse triggered at $currentBpm BPM, next pulse in ${intervalMs}ms")
                }

                // Check if pulse should end
                if (_pulseState.value && now >= pulseEndTime) {
                    _pulseState.value = false
                }

                // Frame delay
                delay(FRAME_DELAY_MS)
            }
        }

        Log.d(TAG, "Animation loop started")
    }

    fun start(bpm: Int) {
        // Validate BPM range
        val validBpm = bpm.coerceIn(MIN_BPM, MAX_BPM)

        if (validBpm != bpm) {
            Log.w(TAG, "BPM $bpm out of range, clamped to $validBpm")
        }

        val now = System.currentTimeMillis()
        val bpmChanged = currentBpm != validBpm

        if (bpmChanged) {
            val oldBpm = currentBpm
            currentBpm = validBpm

            // Calculate how much the BPM changed
            val bpmDelta = if (oldBpm > 0) {
                kotlin.math.abs(validBpm - oldBpm).toFloat() / oldBpm
            } else {
                1.0f
            }

            // If BPM changed significantly (>20%) or this is the first beat, reset timing
            if (bpmDelta > 0.2f || oldBpm == 0 || nextPulseTime == 0L) {
                val intervalMs = (60000.0 / validBpm).toLong()
                nextPulseTime = now + (intervalMs / 4) // Start after 1/4 beat
                pulseEndTime = 0
                lastBpmChange = now
                Log.d(TAG, "BPM changed significantly: $oldBpm -> $validBpm (${(bpmDelta * 100).toInt()}% change), reset timing")
            } else {
                // Small BPM change, adjust timing gradually
                Log.d(TAG, "BPM changed slightly: $oldBpm -> $validBpm, timing preserved")
            }
        }
    }

    fun stop() {
        currentBpm = 0
        nextPulseTime = 0
        pulseEndTime = 0

        // Reset states
        _toggleState.value = false
        _pulseState.value = false

        Log.d(TAG, "Heartbeat stopped")
    }

    fun isRunning(): Boolean {
        return currentBpm > 0
    }

    fun getCurrentBpm(): Int {
        return currentBpm
    }

    fun setPulseDuration(durationMs: Long) {
        pulseDurationMs = durationMs.coerceAtLeast(1L) // Minimum 1ms
        Log.d(TAG, "Pulse duration set to ${pulseDurationMs}ms")
    }

    fun cleanup() {
        stop()
        animationJob?.cancel()
        animationJob = null
        Log.d(TAG, "Animation loop cleaned up")
    }
}
