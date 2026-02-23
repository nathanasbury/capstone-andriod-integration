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
import com.example.guardianhealth.data.local.HealthDao
import com.example.guardianhealth.data.local.HealthReading
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class BLEManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fallAlertManager: FallAlertManager,
    private val healthConnectManager: HealthConnectManager,
    private val healthDao: HealthDao
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val bleScanner by lazy { bluetoothAdapter?.bluetoothLeScanner }

    private var bluetoothGatt: BluetoothGatt? = null

    // GATT operation queue — BLE only allows ONE pending operation at a time
    private val gattQueue = ConcurrentLinkedQueue<Runnable>()
    private var gattBusy = false

    // Throttle Health Connect writes to once per minute
    private var lastHealthConnectWrite = 0L
    private val HC_WRITE_INTERVAL = 60_000L

    // ── State Flows (UI observes these) ────────────────────────────────
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

    // ── Health Data Flows ─────────────────────────────────────────
    private val _heartRate = MutableStateFlow(0)
    val heartRate: StateFlow<Int> = _heartRate

    private val _bloodOxygen = MutableStateFlow(0)
    val bloodOxygen: StateFlow<Int> = _bloodOxygen

    private val _steps = MutableStateFlow(0)
    val steps: StateFlow<Int> = _steps

    private val _fallDetected = MutableStateFlow(false)
    val fallDetected: StateFlow<Boolean> = _fallDetected

    // ── Simulation Mode ───────────────────────────────────────────
    private val _isSimulating = MutableStateFlow(false)
    val isSimulating: StateFlow<Boolean> = _isSimulating

    private var simulationJob: Job? = null
    private var simulatedStepAccumulator = 0

    // Track which mode the current connection is using
    private var isUartMode = false
    // Buffer for incomplete UART messages (data may arrive in fragments)
    private val uartBuffer = StringBuilder()

    companion object {
        private const val TAG = "BLEManager"

        // ┌────────────────────────────────────────────────────────────────┐
        // │  CUSTOM BLE UUIDs — for nRF Connect testing                 │
        // │                                                                │
        // │  nRF Connect setup:                                            │
        // │  1. Go to the “Server” (peripheral) tab in nRF Connect          │
        // │  2. Add a new service with SERVICE_UUID below                   │
        // │  3. Add each characteristic UUID with NOTIFY property           │
        // │  4. Start advertising, then connect from this app               │
        // │  5. Update characteristic values to test:                       │
        // │     - Heart Rate: 1 byte  (e.g. 0x48 = 72 bpm)                │
        // │     - SpO2:       1 byte  (e.g. 0x62 = 98%)                   │
        // │     - Steps:      4 bytes LE (e.g. 0xC4090000 = 2500)         │
        // │     - Fall:       1 byte  (0x01 = fall, 0x00 = clear)         │
        // └────────────────────────────────────────────────────────────────┘
        val SERVICE_UUID: UUID      = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
        val HEART_RATE_UUID: UUID   = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
        val SPO2_UUID: UUID         = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")
        val STEPS_UUID: UUID        = UUID.fromString("0000fff3-0000-1000-8000-00805f9b34fb")
        val FALL_DETECT_UUID: UUID  = UUID.fromString("0000fff4-0000-1000-8000-00805f9b34fb")

        // Standard BLE Client Characteristic Configuration Descriptor
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        // Nordic UART Service (NUS) — for Flipper Zero
        // Flipper: GPIO > USB-UART Bridge > Channel: BLE
        // Or use a Flipper app like "BLE UART" / "Serial over BLE"
        //
        // Text protocol (send from Flipper serial terminal):
        //   HR:72       -> sets heart rate to 72 bpm
        //   SPO2:98     -> sets blood oxygen to 98%
        //   STEPS:2500  -> sets step count to 2500
        //   FALL:1      -> triggers fall alert (FALL:0 to clear)
        //   ALL:72,98,2500 -> sets HR, SpO2, Steps at once
        //
        // Each command is newline-terminated (\n).
        val NUS_SERVICE_UUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        val NUS_RX_UUID: UUID      = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e") // Write to Flipper
        val NUS_TX_UUID: UUID      = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e") // Notify from Flipper
    }

    // Check if we have necessary permissions
    fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    // ── Scanning ────────────────────────────────────────────────
    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!hasBluetoothPermissions()) {
            Log.e(TAG, "Missing Bluetooth permissions")
            return
        }
        if (bluetoothAdapter?.isEnabled != true) {
            _connectionStatus.value = "Bluetooth is disabled"
            return
        }
        _isScanning.value = true
        _connectionStatus.value = "Scanning..."
        _discoveredDevices.value = emptyList()
        bleScanner?.startScan(scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!hasBluetoothPermissions()) return
        _isScanning.value = false
        bleScanner?.stopScan(scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            if (device.name != null && !_discoveredDevices.value.contains(device)) {
                _discoveredDevices.value = _discoveredDevices.value + device
                Log.d(TAG, "Found: ${device.name} [${device.address}]")
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed: $errorCode")
            _isScanning.value = false
            _connectionStatus.value = "Scan failed (error $errorCode)"
        }
    }

    // ── Connection ────────────────────────────────────────────────
    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        if (!hasBluetoothPermissions()) {
            _connectionStatus.value = "Permission denied"
            return
        }
        stopScan()
        _connectionStatus.value = "Connecting..."
        _connectedDeviceName.value = device.name ?: "Unknown"
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        gattQueue.clear()
        gattBusy = false
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        isUartMode = false
        uartBuffer.clear()
        _isConnected.value = false
        _connectionStatus.value = "Disconnected"
        _connectedDeviceName.value = null
    }

    // ── GATT Callback ─────────────────────────────────────────────
    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Connected to GATT server")
                    _isConnected.value = true
                    _connectionStatus.value = "Discovering services..."
                    gatt.discoverServices()
                    // Check Health Connect permissions in background
                    scope.launch { healthConnectManager.checkPermissions() }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected (status: $status)")
                    _isConnected.value = false
                    _connectionStatus.value = if (status == 0) "Disconnected" else "Connection lost"
                    _connectedDeviceName.value = null
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _connectionStatus.value = "Service discovery failed"
                return
            }

            Log.d(TAG, "Services discovered: ${gatt.services.size}")
            gatt.services.forEach { svc ->
                Log.d(TAG, "  Service: ${svc.uuid}")
                svc.characteristics.forEach { c ->
                    Log.d(TAG, "    Char: ${c.uuid} props=0x${c.properties.toString(16)}")
                }
            }

            // Try custom health service first, then fall back to Nordic UART (Flipper Zero)
            val customService = gatt.getService(SERVICE_UUID)
            val nusService = gatt.getService(NUS_SERVICE_UUID)

            if (customService != null) {
                // Custom Service Mode (nRF Connect / CC2674 band)
                isUartMode = false
                _connectionStatus.value = "Connected — subscribing..."
                val uuids = listOf(HEART_RATE_UUID, SPO2_UUID, STEPS_UUID, FALL_DETECT_UUID)
                uuids.forEach { uuid ->
                    enqueueNotificationSubscription(gatt, customService, uuid)
                }
                uuids.forEach { uuid ->
                    enqueueReadIfSupported(gatt, customService, uuid)
                }
                processNextGattOperation()
            } else if (nusService != null) {
                // Nordic UART Service Mode (Flipper Zero)
                isUartMode = true
                uartBuffer.clear()
                _connectionStatus.value = "Connected (Flipper UART)"
                Log.d(TAG, "Flipper Zero detected — using Nordic UART Service")
                enqueueNotificationSubscription(gatt, nusService, NUS_TX_UUID)
                processNextGattOperation()
            } else {
                _connectionStatus.value = "Connected (no supported service)"
                Log.w(TAG, "Neither custom service nor NUS found. Available services:")
                gatt.services.forEach { Log.w(TAG, "  ${it.uuid}") }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Descriptor write OK for ${descriptor.characteristic.uuid}")
            } else {
                Log.e(TAG, "Descriptor write FAILED for ${descriptor.characteristic.uuid} status=$status")
            }
            gattBusy = false
            processNextGattOperation()
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            gattBusy = false
            if (status == BluetoothGatt.GATT_SUCCESS) {
                @Suppress("DEPRECATION")
                val value = characteristic.value
                if (value != null && value.isNotEmpty()) {
                    Log.d(TAG, "Read initial value for ${characteristic.uuid}: ${value.joinToString { "0x%02X".format(it) }}")
                    handleCharacteristicData(characteristic.uuid, value)
                }
            } else {
                Log.e(TAG, "Read FAILED for ${characteristic.uuid} status=$status")
            }
            processNextGattOperation()
        }

        // API < 33 callback (Android 12 and below)
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            @Suppress("DEPRECATION")
            val value = characteristic.value ?: return
            handleCharacteristicData(characteristic.uuid, value)
        }

        // API 33+ callback (Android 13+)
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleCharacteristicData(characteristic.uuid, value)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "UART write OK")
            } else {
                Log.e(TAG, "UART write FAILED status=$status")
            }
            gattBusy = false
            processNextGattOperation()
        }
    }

    // ── Data Parsing ──────────────────────────────────────────────
    private fun handleCharacteristicData(uuid: UUID, value: ByteArray) {
        if (value.isEmpty()) return

        // Route UART (Flipper Zero) data through the text protocol parser
        if (uuid == NUS_TX_UUID) {
            handleUartData(value)
            return
        }

        val hexStr = value.joinToString(" ") { "0x%02X".format(it) }
        Log.d(TAG, "Received data from $uuid: $hexStr")

        when (uuid) {
            HEART_RATE_UUID -> {
                val hr = value[0].toInt() and 0xFF
                _heartRate.value = hr
                Log.d(TAG, "Heart Rate: $hr bpm")
            }
            SPO2_UUID -> {
                val spo2 = value[0].toInt() and 0xFF
                _bloodOxygen.value = spo2
                Log.d(TAG, "SpO2: $spo2%")
            }
            STEPS_UUID -> {
                val s = bytesToInt(value)
                _steps.value = s
                Log.d(TAG, "Steps: $s")
            }
            FALL_DETECT_UUID -> {
                val fell = value[0].toInt() == 1
                if (fell && !_fallDetected.value) {
                    _fallDetected.value = true
                    Log.w(TAG, "FALL DETECTED via BLE")
                    fallAlertManager.onFallDetected()
                } else if (!fell && _fallDetected.value) {
                    _fallDetected.value = false
                    Log.d(TAG, "Fall cleared")
                }
            }
        }

        persistAndSync()
    }

    // ── Nordic UART (Flipper Zero) Text Protocol ──────────────────
    //
    // Parses newline-delimited text commands received over BLE UART:
    //   HR:72        -> heart rate 72 bpm
    //   SPO2:98      -> blood oxygen 98%
    //   STEPS:2500   -> step count 2500
    //   FALL:1       -> fall detected  (FALL:0 to clear)
    //   ALL:72,98,2500 -> sets HR, SpO2, Steps in one message
    //
    // Data may arrive fragmented across multiple BLE packets,
    // so we buffer until we see a newline.

    private fun handleUartData(value: ByteArray) {
        val chunk = String(value, Charsets.UTF_8)
        Log.d(TAG, "UART chunk: $chunk")
        uartBuffer.append(chunk)

        // Process all complete lines in the buffer
        while (true) {
            val newlineIdx = uartBuffer.indexOf('\n')
            if (newlineIdx == -1) break

            val line = uartBuffer.substring(0, newlineIdx).trim()
            uartBuffer.delete(0, newlineIdx + 1)

            if (line.isNotEmpty()) {
                parseUartCommand(line)
            }
        }

        // Safety: if buffer grows too large without a newline, treat it as a single command
        if (uartBuffer.length > 256) {
            val line = uartBuffer.toString().trim()
            uartBuffer.clear()
            if (line.isNotEmpty()) parseUartCommand(line)
        }
    }

    private fun parseUartCommand(line: String) {
        Log.d(TAG, "UART command: $line")
        val parts = line.uppercase().split(":", limit = 2)
        if (parts.size != 2) {
            Log.w(TAG, "Ignoring malformed UART line: $line")
            return
        }

        val key = parts[0].trim()
        val raw = parts[1].trim()

        try {
            when (key) {
                "HR" -> {
                    val hr = raw.toInt().coerceIn(0, 300)
                    _heartRate.value = hr
                    Log.d(TAG, "UART Heart Rate: $hr bpm")
                }
                "SPO2" -> {
                    val spo2 = raw.toInt().coerceIn(0, 100)
                    _bloodOxygen.value = spo2
                    Log.d(TAG, "UART SpO2: $spo2%")
                }
                "STEPS" -> {
                    val s = raw.toInt().coerceIn(0, 999_999)
                    _steps.value = s
                    Log.d(TAG, "UART Steps: $s")
                }
                "FALL" -> {
                    val fell = raw == "1"
                    if (fell && !_fallDetected.value) {
                        _fallDetected.value = true
                        Log.w(TAG, "FALL DETECTED via Flipper")
                        fallAlertManager.onFallDetected()
                    } else if (!fell && _fallDetected.value) {
                        _fallDetected.value = false
                        Log.d(TAG, "Fall cleared via Flipper")
                    }
                }
                "ALL" -> {
                    // ALL:72,98,2500  -> HR, SpO2, Steps
                    val vals = raw.split(",")
                    if (vals.size >= 2) {
                        _heartRate.value = vals[0].trim().toInt().coerceIn(0, 300)
                        _bloodOxygen.value = vals[1].trim().toInt().coerceIn(0, 100)
                    }
                    if (vals.size >= 3) {
                        _steps.value = vals[2].trim().toInt().coerceIn(0, 999_999)
                    }
                    Log.d(TAG, "UART ALL -> HR=${_heartRate.value} SpO2=${_bloodOxygen.value} Steps=${_steps.value}")
                }
                else -> Log.w(TAG, "Unknown UART command key: $key")
            }
        } catch (e: NumberFormatException) {
            Log.e(TAG, "Bad numeric value in '$line': ${e.message}")
        }

        persistAndSync()
    }

    /**
     * Send a text command to the Flipper Zero over BLE UART.
     * Example: sendUartCommand("READ\n")
     */
    @SuppressLint("MissingPermission")
    fun sendUartCommand(command: String) {
        val gatt = bluetoothGatt ?: return
        if (!isUartMode) {
            Log.w(TAG, "sendUartCommand called but not in UART mode")
            return
        }
        val nusService = gatt.getService(NUS_SERVICE_UUID) ?: return
        val rxChar = nusService.getCharacteristic(NUS_RX_UUID) ?: return

        gattQueue.add(Runnable {
            val data = command.toByteArray(Charsets.UTF_8)
            @Suppress("DEPRECATION")
            rxChar.value = data
            rxChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            val ok = gatt.writeCharacteristic(rxChar)
            Log.d(TAG, "UART TX: $command (ok=$ok)")
            if (!ok) {
                gattBusy = false
                processNextGattOperation()
            }
        })
        processNextGattOperation()
    }

    // ── Persist & Sync helper ─────────────────────────────────────
    private fun persistAndSync() {
        scope.launch {
            try {
                healthDao.insertReading(
                    HealthReading(
                        heartRate = _heartRate.value,
                        spO2 = _bloodOxygen.value,
                        steps = _steps.value,
                        isFallDetected = _fallDetected.value
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Room insert failed: ${e.message}")
            }
        }
        maybeWriteToHealthConnect()
    }

    private fun maybeWriteToHealthConnect() {
        val now = System.currentTimeMillis()
        if (now - lastHealthConnectWrite < HC_WRITE_INTERVAL) return
        lastHealthConnectWrite = now

        scope.launch {
            try {
                healthConnectManager.writeHeartRate(_heartRate.value)
                healthConnectManager.writeSteps(
                    _steps.value,
                    Instant.ofEpochMilli(now - HC_WRITE_INTERVAL),
                    Instant.now()
                )
            } catch (e: Exception) {
                Log.e(TAG, "Health Connect write failed: ${e.message}")
            }
        }
    }

    // ── GATT Operation Queue ──────────────────────────────────────
    // BLE allows only ONE pending GATT operation at a time.
    // Each operation (writeDescriptor, readCharacteristic) is wrapped
    // in a Runnable and processed sequentially.

    @SuppressLint("MissingPermission")
    private fun enqueueNotificationSubscription(
        gatt: BluetoothGatt,
        service: BluetoothGattService,
        charUuid: UUID
    ) {
        gattQueue.add(Runnable {
            val char = service.getCharacteristic(charUuid)
            if (char == null) {
                Log.w(TAG, "Characteristic $charUuid not found — skipping")
                gattBusy = false
                processNextGattOperation()
                return@Runnable
            }
            gatt.setCharacteristicNotification(char, true)
            val desc = char.getDescriptor(CCCD_UUID)
            if (desc != null) {
                @Suppress("DEPRECATION")
                desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                val ok = gatt.writeDescriptor(desc)
                Log.d(TAG, "writeDescriptor for $charUuid: $ok")
                if (!ok) {
                    gattBusy = false
                    processNextGattOperation()
                }
            } else {
                Log.w(TAG, "No CCCD for $charUuid — notification may still work")
                gattBusy = false
                processNextGattOperation()
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun enqueueReadIfSupported(
        gatt: BluetoothGatt,
        service: BluetoothGattService,
        charUuid: UUID
    ) {
        gattQueue.add(Runnable {
            val char = service.getCharacteristic(charUuid)
            if (char == null) {
                gattBusy = false
                processNextGattOperation()
                return@Runnable
            }
            // Check if READ is supported
            if ((char.properties and BluetoothGattCharacteristic.PROPERTY_READ) == 0) {
                Log.d(TAG, "Skipping read for $charUuid (READ not supported)")
                gattBusy = false
                processNextGattOperation()
                return@Runnable
            }
            val ok = gatt.readCharacteristic(char)
            Log.d(TAG, "readCharacteristic for $charUuid: $ok")
            if (!ok) {
                gattBusy = false
                processNextGattOperation()
            }
        })
    }

    private fun processNextGattOperation() {
        if (gattBusy) return
        val next = gattQueue.poll() ?: run {
            Log.d(TAG, "GATT queue empty — all subscriptions done")
            _connectionStatus.value = "Connected"
            return
        }
        gattBusy = true
        next.run()
    }

    // ── Fall Detection Controls ───────────────────────────────────

    /**
     * Simulate a fall event for testing without BLE hardware.
     * Call from the UI or trigger via nRF Connect by writing 0x01
     * to the Fall Detection characteristic (0000FFF4-...).
     */
    fun simulateFall() {
        _fallDetected.value = true
        fallAlertManager.onFallDetected()
    }

    /** Dismiss the current fall alert and clear the notification. */
    fun dismissFall() {
        _fallDetected.value = false
        fallAlertManager.dismissAlert()
    }

    // ── Simulation Mode ───────────────────────────────────────────
    // Generates realistic health data without any BLE hardware.
    // Data flows through the same pipeline (Room DB, Health Connect,
    // Fall Detection) so it validates the full integration.

    fun startSimulation() {
        if (_isSimulating.value) return
        _isSimulating.value = true
        _isConnected.value = true
        _connectionStatus.value = "Simulating"
        _connectedDeviceName.value = "Simulator"
        simulatedStepAccumulator = _steps.value

        simulationJob = scope.launch {
            Log.d(TAG, "\uD83C\uDFAE Simulation started")
            while (isActive) {
                // ── Heart Rate: resting 60-100, with occasional spikes ──
                val baseHr = 72
                val drift = Random.nextInt(-8, 12)
                val spike = if (Random.nextFloat() < 0.05f) Random.nextInt(10, 30) else 0
                val hr = (baseHr + drift + spike).coerceIn(55, 130)
                _heartRate.value = hr

                // ── SpO2: normal range 95-99 ──
                val spo2 = (97 + Random.nextInt(-2, 3)).coerceIn(93, 100)
                _bloodOxygen.value = spo2

                // ── Steps: gradual increase (0-5 steps every 2s) ──
                simulatedStepAccumulator += Random.nextInt(0, 6)
                _steps.value = simulatedStepAccumulator

                // Persist to Room
                try {
                    healthDao.insertReading(
                        HealthReading(
                            heartRate = _heartRate.value,
                            spO2 = _bloodOxygen.value,
                            steps = _steps.value,
                            isFallDetected = _fallDetected.value
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Sim: Room insert failed: ${e.message}")
                }

                // Throttled Health Connect write
                maybeWriteToHealthConnect()

                delay(2_000L) // Update every 2 seconds
            }
        }
    }

    fun stopSimulation() {
        simulationJob?.cancel()
        simulationJob = null
        _isSimulating.value = false
        _isConnected.value = false
        _connectionStatus.value = "Disconnected"
        _connectedDeviceName.value = null
        Log.d(TAG, "\uD83C\uDFAE Simulation stopped")
    }

    // ── Utility ───────────────────────────────────────────────────
    private fun bytesToInt(bytes: ByteArray): Int {
        var result = 0
        for (i in bytes.indices) {
            result = result or ((bytes[i].toInt() and 0xFF) shl (8 * i))
        }
        return result
    }
}