package red.kitsu.heartosc

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: HeartRateViewModel,
    onBackPressed: () -> Unit
) {
    val oscHost by viewModel.oscHost.collectAsState()
    val oscPort by viewModel.oscPort.collectAsState()
    val hrParam by viewModel.hrParam.collectAsState()
    val hrConnectedParam by viewModel.hrConnectedParam.collectAsState()
    val heartbeatToggleParam by viewModel.heartbeatToggleParam.collectAsState()
    val heartbeatPulseParam by viewModel.heartbeatPulseParam.collectAsState()

    var hostText by remember(oscHost) { mutableStateOf(oscHost) }
    var portText by remember(oscPort) { mutableStateOf(oscPort.toString()) }
    var hrParamText by remember(hrParam) { mutableStateOf(hrParam) }
    var hrConnectedParamText by remember(hrConnectedParam) { mutableStateOf(hrConnectedParam) }
    var heartbeatToggleParamText by remember(heartbeatToggleParam) { mutableStateOf(heartbeatToggleParam) }
    var heartbeatPulseParamText by remember(heartbeatPulseParam) { mutableStateOf(heartbeatPulseParam) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "OSC Configuration",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = hostText,
                onValueChange = { hostText = it },
                label = { Text("OSC Host") },
                placeholder = { Text(SettingsManager.DEFAULT_OSC_HOST) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
            )

            Text(
                text = "IP address or hostname of the OSC server",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 16.dp)
            )

            OutlinedTextField(
                value = portText,
                onValueChange = {
                    if (it.isEmpty() || it.all { char -> char.isDigit() }) {
                        portText = it
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

            Text(
                text = "Port number (1-65535)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "OSC Parameters",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = hrParamText,
                onValueChange = { hrParamText = it },
                label = { Text("Heart Rate Parameter") },
                placeholder = { Text(SettingsManager.DEFAULT_HR_PARAM) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
            )

            Text(
                text = "OSC path for heart rate value (integer)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 16.dp)
            )

            OutlinedTextField(
                value = hrConnectedParamText,
                onValueChange = { hrConnectedParamText = it },
                label = { Text("HR Connected Parameter") },
                placeholder = { Text(SettingsManager.DEFAULT_HR_CONNECTED_PARAM) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
            )

            Text(
                text = "OSC path for HR monitor connection status (bool)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 16.dp)
            )

            OutlinedTextField(
                value = heartbeatToggleParamText,
                onValueChange = { heartbeatToggleParamText = it },
                label = { Text("Heartbeat Toggle Parameter") },
                placeholder = { Text(SettingsManager.DEFAULT_HEARTBEAT_TOGGLE_PARAM) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
            )

            Text(
                text = "OSC path for heartbeat toggle state (bool)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 16.dp)
            )

            OutlinedTextField(
                value = heartbeatPulseParamText,
                onValueChange = { heartbeatPulseParamText = it },
                label = { Text("Heartbeat Pulse Parameter") },
                placeholder = { Text(SettingsManager.DEFAULT_HEARTBEAT_PULSE_PARAM) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
            )

            Text(
                text = "OSC path for heartbeat pulse state (bool)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    viewModel.setOscHost(hostText)
                    val port = portText.toIntOrNull()
                    if (port != null && port in 1..65535) {
                        viewModel.setOscPort(port)
                    }
                    viewModel.setHrParam(hrParamText)
                    viewModel.setHrConnectedParam(hrConnectedParamText)
                    viewModel.setHeartbeatToggleParam(heartbeatToggleParamText)
                    viewModel.setHeartbeatPulseParam(heartbeatPulseParamText)
                    onBackPressed()
                },
                enabled = hostText.isNotBlank() &&
                         portText.isNotEmpty() &&
                         portText.toIntOrNull()?.let { it in 1..65535 } == true &&
                         hrParamText.isNotBlank() &&
                         hrConnectedParamText.isNotBlank() &&
                         heartbeatToggleParamText.isNotBlank() &&
                         heartbeatPulseParamText.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }

            OutlinedButton(
                onClick = {
                    hostText = SettingsManager.DEFAULT_OSC_HOST
                    portText = SettingsManager.DEFAULT_OSC_PORT.toString()
                    hrParamText = SettingsManager.DEFAULT_HR_PARAM
                    hrConnectedParamText = SettingsManager.DEFAULT_HR_CONNECTED_PARAM
                    heartbeatToggleParamText = SettingsManager.DEFAULT_HEARTBEAT_TOGGLE_PARAM
                    heartbeatPulseParamText = SettingsManager.DEFAULT_HEARTBEAT_PULSE_PARAM
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Reset to Defaults")
            }
        }
    }
}
