package red.kitsu.heartosc

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: HeartRateViewModel,
    onNavigateToDeviceList: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAbout: () -> Unit,
    permissionsGranted: Boolean,
    bluetoothEnabled: Boolean
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val heartRate by viewModel.heartRate.collectAsState()
    val heartbeatPulse by viewModel.heartbeatPulse.collectAsState()

    val isConnected = connectionState is HeartRateMonitorManager.ConnectionState.Connected ||
            connectionState is HeartRateMonitorManager.ConnectionState.Discovering

    var showMenu by remember { mutableStateOf(false) }

    // Animate scale when heartbeat pulse is active
    val scale by animateFloatAsState(
        targetValue = if (heartbeatPulse) 1.1f else 1.0f,
        animationSpec = tween(durationMillis = 100),
        label = "heartbeat_scale"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("HeartOSC") },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options"
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            onClick = {
                                showMenu = false
                                onNavigateToSettings()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("About") },
                            onClick = {
                                showMenu = false
                                onNavigateToAbout()
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Heart Rate Display with pulse animation
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.scale(scale)
            ) {
                Text(
                    text = heartRate?.toString() ?: "--",
                    fontSize = 96.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "BPM",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 32.dp)
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Connection Status
            Text(
                text = when (connectionState) {
                    is HeartRateMonitorManager.ConnectionState.Disconnected -> "Not connected"
                    is HeartRateMonitorManager.ConnectionState.Connecting -> "Connecting..."
                    is HeartRateMonitorManager.ConnectionState.Connected -> "Connected"
                    is HeartRateMonitorManager.ConnectionState.Discovering -> "Discovering services..."
                    is HeartRateMonitorManager.ConnectionState.Error -> {
                        (connectionState as HeartRateMonitorManager.ConnectionState.Error).message
                    }
                },
                style = MaterialTheme.typography.bodyLarge,
                color = when (connectionState) {
                    is HeartRateMonitorManager.ConnectionState.Error ->
                        MaterialTheme.colorScheme.error
                    is HeartRateMonitorManager.ConnectionState.Connected,
                    is HeartRateMonitorManager.ConnectionState.Discovering ->
                        MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                }
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Connect/Disconnect Button
            Button(
                onClick = {
                    if (isConnected) {
                        viewModel.disconnect()
                    } else {
                        onNavigateToDeviceList()
                    }
                },
                enabled = permissionsGranted && bluetoothEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = if (isConnected) "Disconnect" else "Connect to HRM",
                    fontSize = 18.sp
                )
            }

            if (!permissionsGranted || !bluetoothEnabled) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = when {
                        !permissionsGranted -> "Bluetooth permissions required"
                        !bluetoothEnabled -> "Bluetooth must be enabled"
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
