package red.kitsu.heartosc.wear

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import red.kitsu.heartosc.wear.sensor.WearHeartRateManager
import red.kitsu.heartosc.wear.service.WearHeartRateService
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

class WearMainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "WearMainActivity"
        const val PERMISSION_HEALTH_READ_HEART_RATE = "android.permission.health.READ_HEART_RATE"
        const val PERMISSION_HEALTH_READ_HEALTH_DATA_IN_BACKGROUND = "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"
    }

    private val requestBackgroundPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        Log.d(TAG, "Background permissions results: $results")
        // Start service even if background permission isn't fully granted, but log outcome
        startHeartRateService()
    }

    private val requestForegroundPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val bodySensorsGranted = permissions[Manifest.permission.BODY_SENSORS] ?: (
            ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED
        )
        val hasForegroundPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            val healthGranted = permissions[PERMISSION_HEALTH_READ_HEART_RATE] ?: (
                ContextCompat.checkSelfPermission(this, PERMISSION_HEALTH_READ_HEART_RATE) == PackageManager.PERMISSION_GRANTED
            )
            bodySensorsGranted || healthGranted
        } else {
            bodySensorsGranted
        }

        Log.d(TAG, "Foreground permissions result: hasForegroundPermission=$hasForegroundPermission (bodySensors=$bodySensorsGranted)")
        if (hasForegroundPermission) {
            checkAndRequestBackgroundPermission()
        } else {
            Log.w(TAG, "Required sensor permissions denied by user")
            Toast.makeText(this, "Body sensor permissions are required for heart rate tracking", Toast.LENGTH_LONG).show()
        }
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        checkAndRequestForegroundPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen awake while active in UI
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            WearHeartOSCTheme {
                val isMonitoring by WearHeartRateService.isRunning.collectAsState()
                val currentBpm by WearHeartRateService.currentBpm.collectAsState()

                var isDimmed by remember { mutableStateOf(false) }
                var interactionCount by remember { mutableIntStateOf(0) }

                LaunchedEffect(interactionCount, isDimmed) {
                    if (!isDimmed) {
                        delay(10000L.milliseconds)
                        isDimmed = true
                    }
                }

                val scale by animateFloatAsState(
                    targetValue = if (isDimmed) 0.5f else 1.0f,
                    animationSpec = tween(durationMillis = 500),
                    label = "dim_scale"
                )
                val alpha by animateFloatAsState(
                    targetValue = if (isDimmed) 0.2f else 1.0f,
                    animationSpec = tween(durationMillis = 500),
                    label = "dim_alpha"
                )

                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent(PointerEventPass.Initial)
                                    interactionCount++
                                }
                            }
                        },
                    color = Color.Black
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    alpha = alpha
                                )
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = if (isMonitoring && currentBpm > 0) "$currentBpm" else "--",
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isMonitoring && currentBpm > 0) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                            Text(
                                text = "BPM",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    if (isMonitoring) {
                                        stopHeartRateService()
                                    } else {
                                        checkAndStartMonitoringFlow()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isMonitoring) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(
                                    text = if (isMonitoring) "Stop" else "Start",
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isMonitoring) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }

                        if (isDimmed) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        awaitPointerEventScope {
                                            val event = awaitPointerEvent()
                                            event.changes.forEach { it.consume() }
                                            isDimmed = false
                                            interactionCount++
                                        }
                                    }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun checkAndStartMonitoringFlow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            checkAndRequestForegroundPermissions()
        }
    }

    private fun checkAndRequestForegroundPermissions() {
        val hasBodySensors = ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED
        val hasForegroundPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            val hasHealthRead = ContextCompat.checkSelfPermission(this, PERMISSION_HEALTH_READ_HEART_RATE) == PackageManager.PERMISSION_GRANTED
            hasBodySensors || hasHealthRead
        } else {
            hasBodySensors
        }

        if (hasForegroundPermission) {
            checkAndRequestBackgroundPermission()
        } else {
            val missingPermissions = mutableListOf<String>()
            missingPermissions.add(Manifest.permission.BODY_SENSORS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                missingPermissions.add(PERMISSION_HEALTH_READ_HEART_RATE)
            }
            requestForegroundPermissionsLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun checkAndRequestBackgroundPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            val missingBackgroundPermissions = mutableListOf<String>()
            if (ContextCompat.checkSelfPermission(this, PERMISSION_HEALTH_READ_HEALTH_DATA_IN_BACKGROUND) != PackageManager.PERMISSION_GRANTED) {
                missingBackgroundPermissions.add(PERMISSION_HEALTH_READ_HEALTH_DATA_IN_BACKGROUND)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS_BACKGROUND) != PackageManager.PERMISSION_GRANTED) {
                missingBackgroundPermissions.add(Manifest.permission.BODY_SENSORS_BACKGROUND)
            }

            if (missingBackgroundPermissions.isEmpty()) {
                startHeartRateService()
            } else {
                try {
                    requestBackgroundPermissionsLauncher.launch(missingBackgroundPermissions.toTypedArray())
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to launch background permissions launcher, starting service with foreground sensor permission", e)
                    startHeartRateService()
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasBodySensors = ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED
            if (hasBodySensors) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS_BACKGROUND) == PackageManager.PERMISSION_GRANTED) {
                    startHeartRateService()
                } else {
                    try {
                        requestBackgroundPermissionsLauncher.launch(arrayOf(Manifest.permission.BODY_SENSORS_BACKGROUND))
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to launch BODY_SENSORS_BACKGROUND launcher, starting service with foreground sensor permission", e)
                        startHeartRateService()
                    }
                }
            } else {
                startHeartRateService()
            }
        } else {
            startHeartRateService()
        }
    }

    private fun startHeartRateService() {
        try {
            val intent = Intent(this, WearHeartRateService::class.java).apply {
                action = WearHeartRateService.ACTION_START
            }
            ContextCompat.startForegroundService(this, intent)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting WearHeartRateService", e)
            Toast.makeText(this, "Permission missing to start health service", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start WearHeartRateService", e)
            Toast.makeText(this, "Failed to start heart rate service", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopHeartRateService() {
        try {
            val intent = Intent(this, WearHeartRateService::class.java).apply {
                action = WearHeartRateService.ACTION_STOP
            }
            startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop WearHeartRateService", e)
        }
    }
}

private val Purple80 = Color(0xFFD0BCFF)
private val PurpleGrey80 = Color(0xFFCCC2DC)
private val Pink80 = Color(0xFFEFB8C8)

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF1C1B1F),
    onSurface = Color.White
)

@Composable
fun WearHeartOSCTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> DarkColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
