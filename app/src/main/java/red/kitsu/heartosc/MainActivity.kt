package red.kitsu.heartosc

import android.annotation.SuppressLint
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import red.kitsu.heartosc.ui.theme.HeartOSCTheme

class MainActivity : ComponentActivity() {

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            permissionsGranted = true
        }
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        bluetoothEnabled = result.resultCode == RESULT_OK
    }

    private var permissionsGranted by mutableStateOf(false)
    private var bluetoothEnabled by mutableStateOf(false)
    private var serviceBound = false
    private var viewModelInstance: HeartRateViewModel? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as HeartRateService.LocalBinder
            val heartRateService = binder.getService()
            // Store service reference in ViewModel
            viewModelInstance?.heartRateService = heartRateService
            // Set disconnect callback
            heartRateService.onDisconnectRequested = {
                Log.d("MainActivity", "Disconnect requested from notification")
                // Permission already granted (service only runs when connected)
                @SuppressLint("MissingPermission")
                viewModelInstance?.disconnect()
            }
            serviceBound = true
            Log.d("MainActivity", "Service connected and bound")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            viewModelInstance?.heartRateService = null
            serviceBound = false
            Log.d("MainActivity", "Service disconnected")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            HeartOSCTheme {
                val viewModel: HeartRateViewModel = viewModel()
                viewModelInstance = viewModel

                val settingsManager = remember { SettingsManager(applicationContext) }
                var showOnboarding by remember { mutableStateOf(!settingsManager.isOnboardingCompleted()) }

                LaunchedEffect(Unit) {
                    permissionsGranted = viewModel.checkPermissions()
                    bluetoothEnabled = viewModel.isBluetoothEnabled()

                    // Only auto-request permissions if onboarding is complete
                    if (!showOnboarding) {
                        if (!permissionsGranted) {
                            // Only request Bluetooth permissions (notifications are optional)
                            requestPermissionsLauncher.launch(
                                HeartRateMonitorManager.REQUIRED_BLUETOOTH_PERMISSIONS
                            )
                        }

                        if (!bluetoothEnabled) {
                            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                            enableBluetoothLauncher.launch(enableBtIntent)
                        }
                    }
                }

                // Monitor connection state to start/stop service
                val connectionState by viewModel.connectionState.collectAsState()
                LaunchedEffect(connectionState) {
                    val isConnected = connectionState is HeartRateMonitorManager.ConnectionState.Connected ||
                                     connectionState is HeartRateMonitorManager.ConnectionState.Discovering ||
                                     connectionState is HeartRateMonitorManager.ConnectionState.Reconnecting
                    val serviceRunning = HeartRateService.isRunning()

                    Log.d("MainActivity", "Connection state: $connectionState, isConnected: $isConnected, serviceRunning: $serviceRunning")

                    if (isConnected && !serviceRunning) {
                        // Start foreground service only if not already started
                        Log.d("MainActivity", "Starting foreground service for the first time")
                        val serviceIntent = Intent(this@MainActivity, HeartRateService::class.java).apply {
                            action = HeartRateService.ACTION_START
                        }
                        startForegroundService(serviceIntent)

                        // Bind to service
                        if (!serviceBound) {
                            Log.d("MainActivity", "Binding to service")
                            bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
                        }
                    } else if (isConnected && serviceRunning && !serviceBound) {
                        // Service is already running but we need to bind (e.g., activity was recreated)
                        Log.d("MainActivity", "Rebinding to existing service")
                        val serviceIntent = Intent(this@MainActivity, HeartRateService::class.java)
                        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
                    } else if (!isConnected && serviceRunning) {
                        // Stop foreground service when disconnected
                        Log.d("MainActivity", "Disconnected, stopping service")
                        if (serviceBound) {
                            unbindService(serviceConnection)
                            serviceBound = false
                        }
                        val serviceIntent = Intent(this@MainActivity, HeartRateService::class.java).apply {
                            action = HeartRateService.ACTION_STOP
                        }
                        startService(serviceIntent)
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (showOnboarding) {
                        OnboardingScreen(
                            viewModel = viewModel,
                            onComplete = {
                                settingsManager.setOnboardingCompleted()
                                // Re-check permissions and bluetooth state after onboarding
                                permissionsGranted = viewModel.checkPermissions()
                                bluetoothEnabled = viewModel.isBluetoothEnabled()
                                showOnboarding = false
                            },
                            onPermissionsGranted = {
                                permissionsGranted = true
                            },
                            checkPermissions = {
                                viewModel.checkPermissions()
                            },
                            isBluetoothEnabled = bluetoothEnabled
                        )
                    } else {
                        val navController = rememberNavController()

                        NavHost(
                            navController = navController,
                            startDestination = "main",
                            enterTransition = { slideInHorizontally(initialOffsetX = { fullWidth -> fullWidth }) },
                            exitTransition = { fadeOut() },
                            popEnterTransition = { fadeIn() },
                            popExitTransition = { slideOutHorizontally(targetOffsetX = { fullWidth -> fullWidth }) }
                        ) {
                        composable("main") {
                            MainScreen(
                                viewModel = viewModel,
                                onNavigateToDeviceList = {
                                    navController.navigate("deviceList")
                                },
                                onNavigateToSettings = {
                                    navController.navigate("settings")
                                },
                                onNavigateToAbout = {
                                    navController.navigate("about")
                                },
                                permissionsGranted = permissionsGranted,
                                bluetoothEnabled = bluetoothEnabled
                            )
                        }
                        composable("deviceList") {
                            // Permission is checked before navigation (permissionsGranted flag in MainScreen)
                            @SuppressLint("MissingPermission")
                            DeviceListScreen(
                                viewModel = viewModel,
                                onDeviceSelected = {
                                    navController.popBackStack()
                                },
                                onBackPressed = {
                                    navController.popBackStack()
                                }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                viewModel = viewModel,
                                onBackPressed = {
                                    navController.popBackStack()
                                }
                            )
                        }
                        composable("about") {
                            AboutScreen(
                                onBackPressed = {
                                    navController.popBackStack()
                                }
                            )
                        }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
    }
}
