package red.kitsu.heartosc

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    viewModel: HeartRateViewModel,
    onComplete: () -> Unit,
    onPermissionsGranted: () -> Unit,
    checkPermissions: () -> Boolean,
    isBluetoothEnabled: Boolean
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { 5 })
    val scope = rememberCoroutineScope()

    // Helper function to check if Bluetooth is enabled
    fun isBluetoothCurrentlyEnabled(): Boolean {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return bluetoothManager?.adapter?.isEnabled == true
    }

    // Helper function to check notification permission
    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // No notification permission needed on older Android versions
        }
    }

    // Permission states
    var bluetoothPermissionsGranted by remember { mutableStateOf(false) }
    var bluetoothEnabledState by remember { mutableStateOf(false) }
    var notificationPermissionGranted by remember { mutableStateOf(false) }

    // OSC settings
    val oscHost by viewModel.oscHost.collectAsState()
    val oscPort by viewModel.oscPort.collectAsState()
    var hostText by remember { mutableStateOf(oscHost) }
    var portText by remember { mutableStateOf(oscPort.toString()) }

    // Permission launchers
    val bluetoothPermissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val bluetoothGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions[Manifest.permission.BLUETOOTH_SCAN] == true &&
            permissions[Manifest.permission.BLUETOOTH_CONNECT] == true &&
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        } else {
            permissions[Manifest.permission.BLUETOOTH] == true &&
            permissions[Manifest.permission.BLUETOOTH_ADMIN] == true &&
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        }
        bluetoothPermissionsGranted = bluetoothGranted
        bluetoothEnabledState = isBluetoothCurrentlyEnabled()
        if (bluetoothGranted) {
            onPermissionsGranted()
            // Auto-advance to next page after granting permissions
            if (bluetoothEnabledState) {
                scope.launch {
                    kotlinx.coroutines.delay(500) // Small delay for better UX
                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                }
            }
        }
    }

    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        bluetoothEnabledState = isBluetoothCurrentlyEnabled()
        // Auto-advance if bluetooth is now enabled
        if (bluetoothEnabledState && bluetoothPermissionsGranted) {
            scope.launch {
                kotlinx.coroutines.delay(500)
                pagerState.animateScrollToPage(pagerState.currentPage + 1)
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationPermissionGranted = granted || !hasNotificationPermission()
        // Auto-advance after granting or denying
        scope.launch {
            kotlinx.coroutines.delay(500)
            pagerState.animateScrollToPage(pagerState.currentPage + 1)
        }
    }

    // Check initial permission states
    LaunchedEffect(Unit) {
        bluetoothPermissionsGranted = checkPermissions()
        bluetoothEnabledState = isBluetoothCurrentlyEnabled()
        notificationPermissionGranted = hasNotificationPermission()
    }

    // Re-check Bluetooth enabled state when returning to the app
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage == 1) {
            bluetoothEnabledState = isBluetoothCurrentlyEnabled()
        }
    }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                userScrollEnabled = false
            ) { page ->
                when (page) {
                    0 -> WelcomePage()
                    1 -> BluetoothPermissionPage(
                        isGranted = bluetoothPermissionsGranted,
                        isBluetoothEnabled = bluetoothEnabledState,
                        onRequestPermission = {
                            val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                arrayOf(
                                    Manifest.permission.BLUETOOTH_SCAN,
                                    Manifest.permission.BLUETOOTH_CONNECT,
                                    Manifest.permission.ACCESS_FINE_LOCATION
                                )
                            } else {
                                arrayOf(
                                    Manifest.permission.BLUETOOTH,
                                    Manifest.permission.BLUETOOTH_ADMIN,
                                    Manifest.permission.ACCESS_FINE_LOCATION
                                )
                            }
                            bluetoothPermissionsLauncher.launch(permissions)
                        },
                        onEnableBluetooth = {
                            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                            enableBluetoothLauncher.launch(enableBtIntent)
                        }
                    )
                    2 -> NotificationPermissionPage(
                        isGranted = notificationPermissionGranted,
                        onRequestPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                notificationPermissionGranted = true
                            }
                        }
                    )
                    3 -> OscSetupPage(
                        hostText = hostText,
                        portText = portText,
                        onHostChanged = { hostText = it },
                        onPortChanged = { portText = it }
                    )
                    4 -> CompletePage()
                }
            }

            // Page indicator and navigation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Page indicators
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(5) { index ->
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index == pagerState.currentPage)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant
                                )
                        )
                    }
                }

                // Next/Complete button
                Button(
                    onClick = {
                        if (pagerState.currentPage == 4) {
                            // Save OSC settings
                            viewModel.setOscHost(hostText)
                            val port = portText.toIntOrNull()
                            if (port != null && port in 1..65535) {
                                viewModel.setOscPort(port)
                            }
                            onComplete()
                        } else {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    enabled = when (pagerState.currentPage) {
                        1 -> bluetoothPermissionsGranted && bluetoothEnabledState
                        2 -> notificationPermissionGranted || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                        3 -> hostText.isNotBlank() && portText.toIntOrNull()?.let { it in 1..65535 } == true
                        else -> true
                    }
                ) {
                    Text(if (pagerState.currentPage == 4) "Get Started" else "Next")
                }
            }
        }
    }
}

@Composable
fun WelcomePage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Welcome to HeartOSC",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Stream your heart rate to VRChat",
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(48.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("• Connect Bluetooth heart rate monitors")
                Text("• Real-time BPM with pulse animation")
                Text("• Background monitoring")
                Text("• Customizable OSC parameters")
            }
        }
    }
}

@Composable
fun BluetoothPermissionPage(
    isGranted: Boolean,
    isBluetoothEnabled: Boolean,
    onRequestPermission: () -> Unit,
    onEnableBluetooth: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Bluetooth,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Bluetooth Permissions",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Required to connect to your heart rate monitor",
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (!isGranted) {
            Button(
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Grant Bluetooth Permissions")
            }
        } else if (!isBluetoothEnabled) {
            Button(
                onClick = onEnableBluetooth,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Enable Bluetooth")
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Bluetooth permissions granted!",
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
fun NotificationPermissionPage(
    isGranted: Boolean,
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Notifications,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Notifications",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "See your heart rate while the app is in the background",
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (!isGranted) {
            Button(
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Enable Notifications")
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Skip for now")
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Notifications enabled!",
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
fun OscSetupPage(
    hostText: String,
    portText: String,
    onHostChanged: (String) -> Unit,
    onPortChanged: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "VRChat Connection",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Enter your VRChat PC's IP address",
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = hostText,
            onValueChange = onHostChanged,
            label = { Text("OSC Host") },
            placeholder = { Text(SettingsManager.DEFAULT_OSC_HOST) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = portText,
            onValueChange = {
                if (it.isEmpty() || it.all { char -> char.isDigit() }) {
                    onPortChanged(it)
                }
            },
            label = { Text("OSC Port") },
            placeholder = { Text(SettingsManager.DEFAULT_OSC_PORT.toString()) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = portText.isNotEmpty() && (portText.toIntOrNull() == null ||
                     portText.toInt() !in 1..65535)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "You can change these later in Settings",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun CompletePage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "All Set!",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Ready to connect your heart rate monitor",
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(48.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("1. Connect to your heart rate monitor")
                Text("2. Open VRChat")
                Text("3. Your heart rate will appear in-game")
            }
        }
    }
}
