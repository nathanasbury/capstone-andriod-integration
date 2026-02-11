package com.example.guardianhealth

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dagger.hilt.android.AndroidEntryPoint

// Forest theme colors
object ForestTheme {
    val DeepForest = Color(0xFF1B4332)
    val MossGreen = Color(0xFF2D6A4F)
    val LeafGreen = Color(0xFF40916C)
    val SageGreen = Color(0xFF52B788)
    val MintGreen = Color(0xFF74C69D)
    val LightMint = Color(0xFF95D5B2)
    val Cream = Color(0xFFF8F9FA)
    val SkyBlue = Color(0xFF4A90A4)
    val SunsetOrange = Color(0xFFE76F51)
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private lateinit var bleManager: BLEManager

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            bleManager.startScan()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bleManager = BLEManager(this)

        setContent {
            HealthTrackerTheme {
                var currentScreen by remember { mutableStateOf("home") }

                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        ForestTheme.DeepForest,
                                        ForestTheme.MossGreen,
                                        ForestTheme.LeafGreen
                                    )
                                )
                            )
                    )

                    when (currentScreen) {
                        "home" -> HomeScreen(
                            bleManager = bleManager,
                            onNavigateToSettings = { currentScreen = "settings" },
                            onRequestPermissions = { requestBluetoothPermissions() }
                        )
                        "settings" -> SettingsScreen(
                            bleManager = bleManager,
                            onNavigateBack = { currentScreen = "home" },
                            onRequestPermissions = { requestBluetoothPermissions() }
                        )
                    }
                }
            }
        }
    }

    private fun requestBluetoothPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
        requestPermissionLauncher.launch(permissions)
    }

    override fun onDestroy() {
        super.onDestroy()
        bleManager.disconnect()
    }
}

@Composable
fun HealthTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = ForestTheme.LeafGreen,
            secondary = ForestTheme.SageGreen,
            background = ForestTheme.Cream
        ),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    bleManager: BLEManager,
    onNavigateToSettings: () -> Unit,
    onRequestPermissions: () -> Unit
) {
    val isConnected by bleManager.isConnected.collectAsState()
    val steps by bleManager.steps.collectAsState()
    val heartRate by bleManager.heartRate.collectAsState()
    val bloodOxygen by bleManager.bloodOxygen.collectAsState()
    val fallDetected by bleManager.fallDetected.collectAsState()
    val connectionStatus by bleManager.connectionStatus.collectAsState()
    val connectedDeviceName by bleManager.connectedDeviceName.collectAsState()

    var showDeviceDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = null,
                        tint = ForestTheme.Cream,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Vitality Tracker",
                        color = ForestTheme.Cream,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                }
            },
            actions = {
                IconButton(onClick = onNavigateToSettings) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = ForestTheme.Cream
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ConnectionStatusCard(
                isConnected = isConnected,
                statusText = connectionStatus,
                deviceName = connectedDeviceName
            )

            Spacer(modifier = Modifier.height(20.dp))

            AnimatedVisibility(
                visible = fallDetected,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                Column {
                    FallAlertBanner(onDismiss = {})
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }

            Text(
                text = "Your Vitals",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = ForestTheme.Cream,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(16.dp))

            HealthMetricCard(
                icon = Icons.Default.Favorite,
                title = "Steps",
                value = steps.toString(),
                unit = "steps",
                gradient = listOf(ForestTheme.SageGreen, ForestTheme.MintGreen)
            )

            Spacer(modifier = Modifier.height(12.dp))

            HealthMetricCard(
                icon = Icons.Default.Favorite,
                title = "Heart Rate",
                value = heartRate.toString(),
                unit = "bpm",
                gradient = listOf(ForestTheme.SunsetOrange, Color(0xFFFF8A65))
            )

            Spacer(modifier = Modifier.height(12.dp))

            HealthMetricCard(
                icon = Icons.Default.Favorite,
                title = "Blood Oxygen",
                value = bloodOxygen.toString(),
                unit = "%",
                gradient = listOf(ForestTheme.SkyBlue, Color(0xFF64B5F6))
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ActionButton(
                    icon = if (isConnected) Icons.Default.CheckCircle else Icons.Default.Info,
                    text = if (isConnected) "Connected" else "Connect",
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
                    modifier = Modifier.weight(1f),
                    containerColor = if (isConnected) ForestTheme.LeafGreen else ForestTheme.MossGreen
                )

                ActionButton(
                    icon = Icons.Default.Warning,
                    text = "Emergency",
                    onClick = {},
                    modifier = Modifier.weight(1f),
                    containerColor = ForestTheme.SunsetOrange
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        if (showDeviceDialog) {
            DeviceSelectionDialog(
                bleManager = bleManager,
                onDismiss = {
                    showDeviceDialog = false
                    bleManager.stopScan()
                },
                onDeviceSelected = { device ->
                    bleManager.connectToDevice(device)
                    showDeviceDialog = false
                }
            )
        }
    }
}

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
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Select Device")
                if (isScanning) {
                    Spacer(Modifier.width(12.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        },
        text = {
            Column {
                if (devices.isEmpty()) {
                    Text(
                        if (isScanning) "Searching for devices..." else "No devices found",
                        color = Color.Gray
                    )
                } else {
                    Text("Found ${devices.size} device(s):", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    devices.forEach { device ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onDeviceSelected(device) },
                            colors = CardDefaults.cardColors(
                                containerColor = ForestTheme.LightMint.copy(alpha = 0.2f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    device.name ?: "Unknown Device",
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    device.address,
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    bleManager: BLEManager,
    onNavigateBack: () -> Unit,
    onRequestPermissions: () -> Unit
) {
    val bleEnabled by bleManager.isConnected.collectAsState()
    var healthConnectEnabled by remember { mutableStateOf(false) }
    var notificationsEnabled by remember { mutableStateOf(true) }
    var emergencyContact by remember { mutableStateOf("") }
    var showDeviceDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        TopAppBar(
            title = {
                Text(
                    "Settings",
                    color = ForestTheme.Cream,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = ForestTheme.Cream
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsSection(title = "Device Connection") {
                SettingToggle(
                    icon = Icons.Default.Info,
                    title = "BLE Device",
                    subtitle = if (bleEnabled) "Connected to health tracker" else "Not connected",
                    checked = bleEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            if (bleManager.hasBluetoothPermissions()) {
                                showDeviceDialog = true
                                bleManager.startScan()
                            } else {
                                onRequestPermissions()
                            }
                        } else {
                            bleManager.disconnect()
                        }
                    }
                )
            }

            SettingsSection(title = "Health Connect") {
                SettingToggle(
                    icon = Icons.Default.Favorite,
                    title = "Health Connect Sync",
                    subtitle = if (healthConnectEnabled) "Syncing with Health Connect" else "Tap to enable sync",
                    checked = healthConnectEnabled,
                    onCheckedChange = { healthConnectEnabled = it }
                )
            }

            SettingsSection(title = "Notifications") {
                SettingToggle(
                    icon = Icons.Default.Notifications,
                    title = "Fall Alerts",
                    subtitle = "Get notified when a fall is detected",
                    checked = notificationsEnabled,
                    onCheckedChange = { notificationsEnabled = it }
                )
            }

            SettingsSection(title = "Emergency Contact") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.15f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Phone,
                                contentDescription = null,
                                tint = ForestTheme.SunsetOrange,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Emergency Phone Number",
                                color = ForestTheme.Cream,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        OutlinedTextField(
                            value = emergencyContact,
                            onValueChange = { emergencyContact = it },
                            placeholder = { Text("Enter phone number") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = ForestTheme.Cream,
                                unfocusedTextColor = ForestTheme.Cream,
                                focusedBorderColor = ForestTheme.MintGreen,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                                focusedPlaceholderColor = Color.White.copy(alpha = 0.5f),
                                unfocusedPlaceholderColor = Color.White.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Text(
                            "This contact will be notified via SMS when a fall is detected",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            SettingsSection(title = "About") {
                SettingItem(
                    icon = Icons.Default.Info,
                    title = "App Version",
                    subtitle = "1.0.0 (Capstone Project)"
                )
            }
        }

        if (showDeviceDialog) {
            DeviceSelectionDialog(
                bleManager = bleManager,
                onDismiss = {
                    showDeviceDialog = false
                    bleManager.stopScan()
                },
                onDeviceSelected = { device ->
                    bleManager.connectToDevice(device)
                    showDeviceDialog = false
                }
            )
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = ForestTheme.LightMint,
            modifier = Modifier.padding(start = 4.dp)
        )
        content()
    }
}

@Composable
fun SettingToggle(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = ForestTheme.MintGreen,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    color = ForestTheme.Cream,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
                )
                Text(
                    subtitle,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ForestTheme.Cream,
                    checkedTrackColor = ForestTheme.LeafGreen,
                    uncheckedThumbColor = Color.White.copy(alpha = 0.5f),
                    uncheckedTrackColor = Color.White.copy(alpha = 0.2f)
                )
            )
        }
    }
}

@Composable
fun SettingItem(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = ForestTheme.MintGreen,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    title,
                    color = ForestTheme.Cream,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
                )
                Text(
                    subtitle,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
fun ConnectionStatusCard(isConnected: Boolean, statusText: String, deviceName: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected)
                Color.White.copy(alpha = 0.2f)
            else
                Color(0xFFD32F2F).copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(
                        if (isConnected) ForestTheme.MintGreen else ForestTheme.SunsetOrange
                    )
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isConnected) "Connected to ${deviceName ?: "Device"}" else "Device Disconnected",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = ForestTheme.Cream
                )
                Text(
                    statusText,
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
            Icon(
                if (isConnected) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isConnected) ForestTheme.MintGreen else ForestTheme.SunsetOrange,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
fun HealthMetricCard(
    icon: ImageVector,
    title: String,
    value: String,
    unit: String,
    gradient: List<Color>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(gradient)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 15.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Medium
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        value,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                        color = ForestTheme.Cream
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        unit,
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun FallAlertBanner(onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = ForestTheme.SunsetOrange
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "FALL DETECTED",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Emergency contacts have been notified via SMS",
                color = Color.White.copy(alpha = 0.95f),
                fontSize = 14.sp
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = ForestTheme.SunsetOrange
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("I'm OK - Dismiss Alert", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ActionButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = ForestTheme.LeafGreen
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontWeight = FontWeight.Bold)
    }
}