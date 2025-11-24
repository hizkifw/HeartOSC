package red.kitsu.heartosc

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*

class HeartRateMonitorManager(private val context: Context) {

    companion object {
        private const val TAG = "HeartRateMonitorManager"

        // Standard Bluetooth SIG UUIDs for Heart Rate Service
        val HEART_RATE_SERVICE_UUID: UUID = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb")
        val HEART_RATE_MEASUREMENT_CHAR_UUID: UUID = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb")
        val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        // Required permissions
        val REQUIRED_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
    }

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private var bluetoothLeScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
    private var bluetoothGatt: BluetoothGatt? = null
    private var heartRateCharacteristic: BluetoothGattCharacteristic? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _heartRate = MutableStateFlow<Int?>(null)
    val heartRate: StateFlow<Int?> = _heartRate.asStateFlow()

    private val _energyExpended = MutableStateFlow<Int?>(null)
    val energyExpended: StateFlow<Int?> = _energyExpended.asStateFlow()

    private val _rrIntervals = MutableStateFlow<List<Int>>(emptyList())
    val rrIntervals: StateFlow<List<Int>> = _rrIntervals.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices.asStateFlow()

    private val _scanningState = MutableStateFlow(false)
    val scanningState: StateFlow<Boolean> = _scanningState.asStateFlow()

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        object Connected : ConnectionState()
        object Discovering : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    data class HeartRateMeasurement(
        val heartRate: Int,
        val energyExpended: Int? = null,
        val rrIntervals: List<Int> = emptyList()
    )

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { device ->
                if (!_discoveredDevices.value.contains(device)) {
                    Log.d(TAG, "Found device: ${device.name ?: "Unknown"} - ${device.address}")
                    _discoveredDevices.value = _discoveredDevices.value + device
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with error code: $errorCode")
            _scanningState.value = false
            _connectionState.value = ConnectionState.Error("Scan failed: $errorCode")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Connected to GATT server")
                    _connectionState.value = ConnectionState.Connected
                    gatt?.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected from GATT server")
                    _connectionState.value = ConnectionState.Disconnected
                    _heartRate.value = null
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered")
                _connectionState.value = ConnectionState.Discovering

                val heartRateService = gatt?.getService(HEART_RATE_SERVICE_UUID)
                if (heartRateService != null) {
                    heartRateCharacteristic =
                        heartRateService.getCharacteristic(HEART_RATE_MEASUREMENT_CHAR_UUID)

                    if (heartRateCharacteristic != null) {
                        enableNotifications(gatt, heartRateCharacteristic!!)
                    } else {
                        Log.e(TAG, "Heart rate measurement characteristic not found")
                        _connectionState.value = ConnectionState.Error("Heart rate characteristic not found")
                    }
                } else {
                    Log.e(TAG, "Heart rate service not found")
                    _connectionState.value = ConnectionState.Error("Heart rate service not found")
                }
            } else {
                Log.e(TAG, "Service discovery failed with status: $status")
                _connectionState.value = ConnectionState.Error("Service discovery failed")
            }
        }

        @Deprecated("Deprecated in API level 33")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            if (characteristic?.uuid == HEART_RATE_MEASUREMENT_CHAR_UUID) {
                val data = parseHeartRateMeasurement(characteristic)
                Log.d(TAG, "Heart rate: ${data.heartRate} bpm")
                _heartRate.value = data.heartRate
                _energyExpended.value = data.energyExpended
                _rrIntervals.value = data.rrIntervals
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == HEART_RATE_MEASUREMENT_CHAR_UUID) {
                val data = parseHeartRateMeasurement(value)
                Log.d(TAG, "Heart rate: ${data.heartRate} bpm" +
                        (data.energyExpended?.let { ", Energy: $it kJ" } ?: "") +
                        (if (data.rrIntervals.isNotEmpty()) ", RR: ${data.rrIntervals.size} intervals" else ""))
                _heartRate.value = data.heartRate
                _energyExpended.value = data.energyExpended
                _rrIntervals.value = data.rrIntervals
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (descriptor.uuid == CLIENT_CHARACTERISTIC_CONFIG_UUID) {
                    Log.d(TAG, "Descriptor write successful - notifications enabled")
                    _connectionState.value = ConnectionState.Connected
                }
            } else {
                Log.e(TAG, "Descriptor write failed with status: $status")
                _connectionState.value = ConnectionState.Error("Failed to enable notifications")
            }
        }
    }

    fun checkPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all { permission ->
            ActivityCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!checkPermissions()) {
            Log.e(TAG, "Missing Bluetooth permissions")
            _connectionState.value = ConnectionState.Error("Missing Bluetooth permissions")
            return
        }

        if (!isBluetoothEnabled()) {
            Log.e(TAG, "Bluetooth is not enabled")
            _connectionState.value = ConnectionState.Error("Bluetooth is not enabled")
            return
        }

        _discoveredDevices.value = emptyList()
        _scanningState.value = true

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(android.os.ParcelUuid(HEART_RATE_SERVICE_UUID))
            .build()

        bluetoothLeScanner?.startScan(listOf(scanFilter), scanSettings, scanCallback)
        Log.d(TAG, "Started scanning for heart rate devices")
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!checkPermissions()) return

        bluetoothLeScanner?.stopScan(scanCallback)
        _scanningState.value = false
        Log.d(TAG, "Stopped scanning")
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        if (!checkPermissions()) {
            Log.e(TAG, "Missing Bluetooth permissions")
            _connectionState.value = ConnectionState.Error("Missing Bluetooth permissions")
            return
        }

        stopScan()
        _connectionState.value = ConnectionState.Connecting

        bluetoothGatt?.close()
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
        Log.d(TAG, "Connecting to device: ${device.name ?: "Unknown"}")
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        if (!checkPermissions()) return

        // Disable notifications before disconnecting
        heartRateCharacteristic?.let { characteristic ->
            bluetoothGatt?.let { gatt ->
                disableNotifications(gatt, characteristic)
            }
        }

        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        heartRateCharacteristic = null
        _connectionState.value = ConnectionState.Disconnected
        _heartRate.value = null
        _energyExpended.value = null
        _rrIntervals.value = emptyList()
        Log.d(TAG, "Disconnected")
    }

    @SuppressLint("MissingPermission")
    private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        if (!checkPermissions()) return

        gatt.setCharacteristicNotification(characteristic, true)

        val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
        if (descriptor != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(
                    descriptor,
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                )
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
            Log.d(TAG, "Enabled notifications for heart rate characteristic")
        } else {
            Log.e(TAG, "Client characteristic configuration descriptor not found")
            _connectionState.value = ConnectionState.Error("Could not enable notifications")
        }
    }

    @SuppressLint("MissingPermission")
    private fun disableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        if (!checkPermissions()) return

        gatt.setCharacteristicNotification(characteristic, false)

        val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
        if (descriptor != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(
                    descriptor,
                    BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                )
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
            Log.d(TAG, "Disabled notifications for heart rate characteristic")
        } else {
            Log.e(TAG, "Client characteristic configuration descriptor not found")
        }
    }

    private fun parseHeartRateMeasurement(characteristic: BluetoothGattCharacteristic): HeartRateMeasurement {
        val value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            characteristic.value
        } else {
            @Suppress("DEPRECATION")
            characteristic.value
        }
        return parseHeartRateMeasurement(value)
    }

    private fun parseHeartRateMeasurement(value: ByteArray): HeartRateMeasurement {
        if (value.isEmpty()) {
            return HeartRateMeasurement(0)
        }

        var offset = 0
        val flags = value[offset++].toInt() and 0xFF

        // Bit 0: Heart Rate Value Format (0 = UINT8, 1 = UINT16)
        val hrFormat = (flags and 0x01) != 0

        // Bit 1-2: Sensor Contact Status
        // Bit 3: Energy Expended Status (0 = not present, 1 = present)
        val energyExpendedPresent = (flags and 0x08) != 0

        // Bit 4: RR-Interval (0 = not present, 1 = present)
        val rrIntervalsPresent = (flags and 0x10) != 0

        // Parse Heart Rate Value
        val heartRate = if (hrFormat) {
            // UINT16 format
            if (offset + 1 < value.size) {
                val hr = ((value[offset].toInt() and 0xFF) or
                         ((value[offset + 1].toInt() and 0xFF) shl 8))
                offset += 2
                hr
            } else {
                0
            }
        } else {
            // UINT8 format
            if (offset < value.size) {
                val hr = value[offset++].toInt() and 0xFF
                hr
            } else {
                0
            }
        }

        // Parse Energy Expended (UINT16)
        val energyExpended = if (energyExpendedPresent && offset + 1 < value.size) {
            val energy = ((value[offset].toInt() and 0xFF) or
                         ((value[offset + 1].toInt() and 0xFF) shl 8))
            offset += 2
            energy
        } else {
            null
        }

        // Parse RR-Intervals (each is UINT16 in 1/1024 second resolution)
        val rrIntervals = mutableListOf<Int>()
        if (rrIntervalsPresent) {
            while (offset + 1 < value.size) {
                val rr = ((value[offset].toInt() and 0xFF) or
                         ((value[offset + 1].toInt() and 0xFF) shl 8))
                rrIntervals.add(rr)
                offset += 2
            }
        }

        return HeartRateMeasurement(
            heartRate = heartRate,
            energyExpended = energyExpended,
            rrIntervals = rrIntervals
        )
    }

    fun cleanup() {
        stopScan()
        disconnect()
    }
}
