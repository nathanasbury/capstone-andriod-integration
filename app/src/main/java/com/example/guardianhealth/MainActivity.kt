package com.example.guardianhealth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.guardianhealth.data.local.Contact
import com.example.guardianhealth.data.local.ContactDao
import com.example.guardianhealth.ui.theme.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var bleManager: BLEManager
    @Inject lateinit var contactDao: ContactDao

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.entries.all { it.value }) {
            bleManager.startScan()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            GuardianHealthTheme {
                var currentScreen by remember { mutableStateOf("home") }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (currentScreen) {
                        "home" -> HomeScreen(
                            bleManager = bleManager,
                            onNavigateToSettings = { currentScreen = "settings" },
                            onRequestPermissions = { requestPermissions() }
                        )
                        "settings" -> SettingsScreen(
                            bleManager = bleManager,
                            contactDao = contactDao,
                            onNavigateBack = { currentScreen = "home" },
                            onRequestPermissions = { requestPermissions() }
                        )
                    }
                }
            }
        }
    }

    private fun requestPermissions() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_SCAN)
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
            perms.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        perms.add(Manifest.permission.SEND_SMS)
        requestPermissionLauncher.launch(perms.toTypedArray())
    }

    override fun onDestroy() {
        super.onDestroy()
        bleManager.disconnect()
    }
}

// ════════════════════════════════════════════════════════════════
//  HOME SCREEN
// ════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    bleManager: BLEManager,
    onNavigateToSettings: () -> Unit,
    onRequestPermissions: () -> Unit
) {
    val isConnected by bleManager.isConnected.collectAsState()
    val heartRate by bleManager.heartRate.collectAsState()
    val bloodOxygen by bleManager.bloodOxygen.collectAsState()
    val steps by bleManager.steps.collectAsState()
    val fallDetected by bleManager.fallDetected.collectAsState()
    val connectionStatus by bleManager.connectionStatus.collectAsState()
    val connectedDeviceName by bleManager.connectedDeviceName.collectAsState()
    var showDeviceDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // ── Header ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Guardian Health",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            IconButton(onClick = onNavigateToSettings) {
                Icon(Icons.Default.Settings, "Settings")
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Connection Status Pill ──
        Surface(
            shape = RoundedCornerShape(50),
            color = if (isConnected) GreenContainer else AlertRedBg,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (isConnected) ConnectedGreen else AlertRed)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (isConnected) connectedDeviceName ?: "Connected" else "Not Connected",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        connectionStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Fall Alert ──
        AnimatedVisibility(
            visible = fallDetected,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = AlertRed)
                ) {
                    Column(Modifier.padding(24.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Warning, null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Fall Detected",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Emergency contacts are being notified.",
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { bleManager.dismissFall() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = AlertRed
                            )
                        ) {
                            Icon(Icons.Default.Check, null)
                            Spacer(Modifier.width(8.dp))
                            Text("I'm OK — Dismiss", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }

        // ── Vitals Header ──
        Text(
            "Vitals",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // ── Vitals Grid ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            VitalCard(
                label = "Heart Rate",
                value = if (heartRate > 0) heartRate.toString() else "--",
                unit = "bpm",
                accentColor = HeartRed,
                bgColor = HeartRedBg,
                modifier = Modifier.weight(1f)
            )
            VitalCard(
                label = "SpO2",
                value = if (bloodOxygen > 0) bloodOxygen.toString() else "--",
                unit = "%",
                accentColor = OxygenBlue,
                bgColor = OxygenBlueBg,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(12.dp))

        VitalCard(
            label = "Steps",
            value = steps.toString(),
            unit = "steps",
            accentColor = StepsAmber,
            bgColor = StepsAmberBg,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(24.dp))

        // ── Action Buttons ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    if (isConnected) {
                        bleManager.disconnect()
                    } else {
                        if (bleManager.hasBluetoothPermissions()) {
                            showDeviceDialog = true
                            bleManager.startScan()
                        } else {
                            onRequestPermissions()
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isConnected) GreenPrimary else GreenLight
                )
            ) {
                Icon(
                    if (isConnected) Icons.Default.CheckCircle else Icons.Default.Search,
                    null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isConnected) "Connected" else "Connect",
                    fontWeight = FontWeight.SemiBold
                )
            }

            OutlinedButton(
                onClick = { /* Emergency action — could auto-trigger SMS */ },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(2.dp, AlertRed),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertRed)
            ) {
                Icon(Icons.Default.Warning, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Emergency", fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Simulate Fall (for testing with nRF Connect or demo) ──
        OutlinedButton(
            onClick = { bleManager.simulateFall() },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, TextSecondary.copy(alpha = 0.3f))
        ) {
            Text(
                "\u26A1 Simulate Fall (Testing)",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(Modifier.height(32.dp))
    }

    // ── Device Selection Dialog ──
    if (showDeviceDialog) {
        DeviceSelectionDialog(
            bleManager = bleManager,
            onDismiss = { showDeviceDialog = false; bleManager.stopScan() },
            onDeviceSelected = { bleManager.connectToDevice(it); showDeviceDialog = false }
        )
    }
}

// ════════════════════════════════════════════════════════════════
//  VITAL CARD
// ════════════════════════════════════════════════════════════════

@Composable
fun VitalCard(
    label: String,
    value: String,
    unit: String,
    accentColor: Color,
    bgColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    color = accentColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    unit,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════
//  DEVICE SELECTION DIALOG
// ════════════════════════════════════════════════════════════════

@SuppressLint("MissingPermission")
@Composable
fun DeviceSelectionDialog(
    bleManager: BLEManager,
    onDismiss: () -> Unit,
    onDeviceSelected: (BluetoothDevice) -> Unit
) {
    val devices by bleManager.discoveredDevices.collectAsState()
    val isScanning by bleManager.isScanning.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Select Device", fontWeight = FontWeight.Bold)
                if (isScanning) {
                    Spacer(Modifier.width(12.dp))
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                }
            }
        },
        text = {
            Column {
                if (devices.isEmpty()) {
                    Text(
                        if (isScanning) "Searching for nearby devices..."
                        else "No devices found. Ensure your band is advertising.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    devices.forEach { device ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onDeviceSelected(device) },
                            shape = RoundedCornerShape(16.dp),
                            color = GreenContainer.copy(alpha = 0.5f)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    device.name ?: "Unknown",
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    device.address,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ════════════════════════════════════════════════════════════════
//  SETTINGS SCREEN
// ════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    bleManager: BLEManager,
    contactDao: ContactDao,
    onNavigateBack: () -> Unit,
    onRequestPermissions: () -> Unit
) {
    val isConnected by bleManager.isConnected.collectAsState()
    val connectionStatus by bleManager.connectionStatus.collectAsState()
    val contacts by contactDao.getAllContacts().collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()
    var showAddContactDialog by remember { mutableStateOf(false) }
    var showDeviceDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // ── Top Bar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Default.ArrowBack, "Back")
            }
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // ── Device Connection ──
            SettingsSection("Device") {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isConnected) {
                                    bleManager.disconnect()
                                } else {
                                    if (bleManager.hasBluetoothPermissions()) {
                                        showDeviceDialog = true
                                        bleManager.startScan()
                                    } else {
                                        onRequestPermissions()
                                    }
                                }
                            }
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isConnected) ConnectedGreen else DisconnectedGray)
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Bluetooth", fontWeight = FontWeight.Medium)
                            Text(
                                connectionStatus,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = isConnected,
                            onCheckedChange = null
                        )
                    }
                }
            }

            // ── Emergency Contacts ──
            SettingsSection("Emergency Contacts") {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column {
                        if (contacts.isEmpty()) {
                            Text(
                                "No contacts added yet.",
                                modifier = Modifier.padding(20.dp),
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            contacts.forEachIndexed { index, contact ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Person, null,
                                        tint = GreenPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(contact.name, fontWeight = FontWeight.Medium)
                                        Text(
                                            contact.phoneNumber,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            coroutineScope.launch { contactDao.deleteContact(contact) }
                                        }
                                    ) {
                                        Icon(Icons.Default.Close, "Remove", tint = TextSecondary)
                                    }
                                }
                                if (index < contacts.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 20.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                }
                            }
                        }

                        HorizontalDivider(
                            Modifier.padding(horizontal = 20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showAddContactDialog = true }
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Add, null, tint = GreenPrimary)
                            Spacer(Modifier.width(12.dp))
                            Text("Add Contact", color = GreenPrimary, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            // ── nRF Connect Testing Info ──
            SettingsSection("nRF Connect Testing") {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            "Service UUIDs",
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        BleUuidRow("Service", "0000FFF0-0000-1000-8000-00805f9b34fb")
                        BleUuidRow("Heart Rate", "0000FFF1-0000-1000-8000-00805f9b34fb")
                        BleUuidRow("SpO2", "0000FFF2-0000-1000-8000-00805f9b34fb")
                        BleUuidRow("Steps", "0000FFF3-0000-1000-8000-00805f9b34fb")
                        BleUuidRow("Fall Detect", "0000FFF4-0000-1000-8000-00805f9b34fb")
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Set up these UUIDs in nRF Connect's Server tab. " +
                                    "Add NOTIFY property to each characteristic. " +
                                    "Send 0x01 on Fall Detect to trigger a fall alert.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            // ── About ──
            SettingsSection("About") {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Guardian Health", fontWeight = FontWeight.Bold)
                        Text(
                            "Version 1.0.0",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Capstone Project — Health Band Integration",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    // ── Add Contact Dialog ──
    if (showAddContactDialog) {
        AddContactDialog(
            onDismiss = { showAddContactDialog = false },
            onAdd = { name, phone ->
                coroutineScope.launch {
                    contactDao.insertContact(Contact(name = name, phoneNumber = phone))
                }
                showAddContactDialog = false
            }
        )
    }

    // ── Device Dialog ──
    if (showDeviceDialog) {
        DeviceSelectionDialog(
            bleManager = bleManager,
            onDismiss = { showDeviceDialog = false; bleManager.stopScan() },
            onDeviceSelected = { bleManager.connectToDevice(it); showDeviceDialog = false }
        )
    }
}

// ════════════════════════════════════════════════════════════════
//  HELPER COMPOSABLES
// ════════════════════════════════════════════════════════════════

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = TextSecondary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        content()
    }
}

@Composable
fun BleUuidRow(label: String, uuid: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(
            "$label: ",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = TextSecondary
        )
        Text(
            uuid,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun AddContactDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Add Emergency Contact", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank() && phone.isNotBlank()) onAdd(name.trim(), phone.trim()) },
                shape = RoundedCornerShape(12.dp),
                enabled = name.isNotBlank() && phone.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}