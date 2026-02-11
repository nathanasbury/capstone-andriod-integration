package com.example.guardianhealth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*

import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext

import javax.inject.Singleton

@Singleton
class BLEManager @Inject constructor(@ApplicationContext private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val bleScanner by lazy {
        bluetoothAdapter?.bluetoothLeScanner
    }

    private var bluetoothGatt: BluetoothGatt? = null

    // State flows for UI
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices

    private val _connectionStatus = MutableStateFlow("Disconnected")
    val connectionStatus: StateFlow<String> = _connectionStatus

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName

    // Health data flows
    private val _steps = MutableStateFlow(0)
    val steps: StateFlow<Int> = _steps

    private val _heartRate = MutableStateFlow(0)
    val heartRate: StateFlow<Int> = _heartRate

    private val _bloodOxygen = MutableStateFlow(0)
    val bloodOxygen: StateFlow<Int> = _bloodOxygen

    private val _fallDetected = MutableStateFlow(false)
    val fallDetected: StateFlow<Boolean> = _fallDetected

    companion object {
        private const val TAG = "BleManager"

        // TODO: Replace these UUIDs with your actual device's UUIDs
        // You'll get these from your microcontroller team
        val SERVICE_UUID: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HEART_RATE_UUID: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val STEPS_UUID: UUID = UUID.fromString("00002a38-0000-1000-8000-00805f9b34fb")
        val BLOOD_OXYGEN_UUID: UUID = UUID.fromString("00002a39-0000-1000-8000-00805f9b34fb")
        val FALL_DETECTION_UUID: UUID = UUID.fromString("00002a3a-0000-1000-8000-00805f9b34fb")
    }

    // Check if we have necessary permissions
    fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            // Android 11 and below - need both FINE and COARSE
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    // Start scanning for BLE devices
    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!hasBluetoothPermissions()) {
            Log.e(TAG, "Missing Bluetooth permissions")
            return
        }

        if (bluetoothAdapter?.isEnabled != true) {
            Log.e(TAG, "Bluetooth is not enabled")
            _connectionStatus.value = "Bluetooth is disabled"
            return
        }

        _isScanning.value = true
        _connectionStatus.value = "Scanning for devices..."
        _discoveredDevices.value = emptyList()

        bleScanner?.startScan(scanCallback)

        Log.d(TAG, "Started BLE scan")
    }

    // Stop scanning
    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!hasBluetoothPermissions()) return

        _isScanning.value = false
        bleScanner?.stopScan(scanCallback)
        Log.d(TAG, "Stopped BLE scan")
    }

    // Scan callback
    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device

            // Only add devices with names (filters out many random BLE devices)
            if (device.name != null && !_discoveredDevices.value.contains(device)) {
                _discoveredDevices.value = _discoveredDevices.value + device
                Log.d(TAG, "Found device: ${device.name} - ${device.address}")
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with error code: $errorCode")
            _isScanning.value = false
            _connectionStatus.value = "Scan failed"
        }
    }

    // Connect to a specific device
    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        if (!hasBluetoothPermissions()) {
            Log.e(TAG, "Missing Bluetooth permissions for connection")
            _connectionStatus.value = "Permission denied"
            return
        }

        stopScan()
        _connectionStatus.value = "Connecting to ${device.name ?: "device"}..."
        _connectedDeviceName.value = device.name ?: "Unknown Device"

        Log.d(TAG, "Attempting to connect to ${device.name} (${device.address})")

        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    // GATT callback for connection and data
    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Connected to GATT server")
                    _isConnected.value = true
                    _connectionStatus.value = "Connected - discovering services..."

                    // Discover services
                    val discoverSuccess = gatt.discoverServices()
                    if (!discoverSuccess) {
                        Log.e(TAG, "Failed to start service discovery")
                        _connectionStatus.value = "Connected (service discovery failed)"
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected from GATT server (status: $status)")
                    _isConnected.value = false
                    _connectionStatus.value = if (status == 0) "Disconnected" else "Connection lost (error: $status)"
                    _connectedDeviceName.value = null
                }
                BluetoothProfile.STATE_CONNECTING -> {
                    Log.d(TAG, "Connecting...")
                    _connectionStatus.value = "Connecting..."
                }
                BluetoothProfile.STATE_DISCONNECTING -> {
                    Log.d(TAG, "Disconnecting...")
                    _connectionStatus.value = "Disconnecting..."
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered: ${gatt.services.size} services found")
                _connectionStatus.value = "Connected"

                // List all services found
                gatt.services.forEach { service ->
                    Log.d(TAG, "Service found: ${service.uuid}")
                    service.characteristics.forEach { char ->
                        Log.d(TAG, "  Characteristic: ${char.uuid}")
                    }
                }

                // Find your service
                val service = gatt.getService(SERVICE_UUID)
                if (service != null) {
                    Log.d(TAG, "Found health service")

                    // Enable notifications for each characteristic
                    enableNotifications(gatt, service, HEART_RATE_UUID)
                    enableNotifications(gatt, service, STEPS_UUID)
                    enableNotifications(gatt, service, BLOOD_OXYGEN_UUID)
                    enableNotifications(gatt, service, FALL_DETECTION_UUID)
                } else {
                    Log.w(TAG, "Health service not found. Looking for service UUID: $SERVICE_UUID")
                    _connectionStatus.value = "Connected (no health data service)"
                }
            } else {
                Log.e(TAG, "Service discovery failed with status: $status")
                _connectionStatus.value = "Connected (service discovery failed: $status)"
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            // Parse data based on characteristic UUID
            when (characteristic.uuid) {
                HEART_RATE_UUID -> {
                    val heartRate = value[0].toInt() and 0xFF
                    _heartRate.value = heartRate
                    Log.d(TAG, "Heart Rate: $heartRate bpm")
                }
                STEPS_UUID -> {
                    val steps = bytesToInt(value)
                    _steps.value = steps
                    Log.d(TAG, "Steps: $steps")
                }
                BLOOD_OXYGEN_UUID -> {
                    val oxygen = value[0].toInt() and 0xFF
                    _bloodOxygen.value = oxygen
                    Log.d(TAG, "Blood Oxygen: $oxygen%")
                }
                FALL_DETECTION_UUID -> {
                    val fallDetected = value[0].toInt() == 1
                    _fallDetected.value = fallDetected
                    if (fallDetected) {
                        Log.w(TAG, "FALL DETECTED!")
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableNotifications(
        gatt: BluetoothGatt,
        service: BluetoothGattService,
        characteristicUuid: UUID
    ) {
        val characteristic = service.getCharacteristic(characteristicUuid)
        if (characteristic != null) {
            gatt.setCharacteristicNotification(characteristic, true)

            // Enable notifications on the device
            val descriptor = characteristic.getDescriptor(
                UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
            )
            if (descriptor != null) {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
            }
        }
    }

    // Disconnect from device
    @SuppressLint("MissingPermission")
    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        _isConnected.value = false
        _connectionStatus.value = "Disconnected"
        _connectedDeviceName.value = null
    }

    // Helper function to convert byte array to int
    private fun bytesToInt(bytes: ByteArray): Int {
        var result = 0
        for (i in bytes.indices) {
            result = result or ((bytes[i].toInt() and 0xFF) shl (8 * i))
        }
        return result
    }
}