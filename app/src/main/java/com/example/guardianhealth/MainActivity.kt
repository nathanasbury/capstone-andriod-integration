package com.example.guardianhealth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.health.connect.client.PermissionController
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.guardianhealth.data.local.Contact
import com.example.guardianhealth.data.local.ContactDao
import com.example.guardianhealth.data.local.HealthDao
import com.example.guardianhealth.data.local.HealthReading
import com.example.guardianhealth.ui.theme.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var bleManager: BLEManager
    @Inject lateinit var contactDao: ContactDao
    @Inject lateinit var healthDao: HealthDao
    @Inject lateinit var healthConnectManager: HealthConnectManager

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
            // Health Connect permission launcher
            val scope = rememberCoroutineScope()
            val hcPermissionLauncher = rememberLauncherForActivityResult(
                contract = PermissionController.createRequestPermissionResultContract()
            ) { grantedPermissions ->
                scope.launch {
                    healthConnectManager.checkPermissions()
                    Log.d("HealthConnect", "Permissions granted: $grantedPermissions")
                }
            }

            // Check HC permissions on launch
            LaunchedEffect(Unit) {
                healthConnectManager.checkPermissions()
            }

            GuardianHealthTheme {
                MainNavigation(
                    bleManager = bleManager,
                    contactDao = contactDao,
                    healthDao = healthDao,
                    healthConnectManager = healthConnectManager,
                    onRequestPermissions = { requestPermissions() },
                    onRequestHealthConnectPermissions = {
                        hcPermissionLauncher.launch(healthConnectManager.permissions)
                    }
                )
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
//  MAIN NAVIGATION (Bottom Navigation Bar)
// ════════════════════════════════════════════════════════════════

enum class Screen(val label: String, val icon: ImageVector, val outlinedIcon: ImageVector) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home),
    TRENDS("Trends", Icons.Filled.ShowChart, Icons.Outlined.ShowChart),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

@Composable
fun MainNavigation(
    bleManager: BLEManager,
    contactDao: ContactDao,
    healthDao: HealthDao,
    healthConnectManager: HealthConnectManager,
    onRequestPermissions: () -> Unit,
    onRequestHealthConnectPermissions: () -> Unit
) {
    var currentScreen by remember { mutableStateOf(Screen.HOME) }
    var trendMetric by remember { mutableStateOf("heartrate") }
    var showTrendDetail by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!showTrendDetail) {
                NavigationBar(
                    containerColor = Surface,
                    tonalElevation = 0.dp,
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    Screen.entries.forEach { screen ->
                        val selected = currentScreen == screen
                        NavigationBarItem(
                            selected = selected,
                            onClick = { currentScreen = screen },
                            icon = {
                                Icon(
                                    if (selected) screen.icon else screen.outlinedIcon,
                                    contentDescription = screen.label
                                )
                            },
                            label = {
                                Text(
                                    screen.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = GreenPrimary,
                                selectedTextColor = GreenPrimary,
                                indicatorColor = GreenContainer,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding)) {
            if (showTrendDetail) {
                TrendDetailScreen(
                    metric = trendMetric,
                    healthDao = healthDao,
                    healthConnectManager = healthConnectManager,
                    onNavigateBack = { showTrendDetail = false }
                )
            } else {
                when (currentScreen) {
                    Screen.HOME -> HomeScreen(
                        bleManager = bleManager,
                        onRequestPermissions = onRequestPermissions,
                        onMetricClick = { metric ->
                            trendMetric = metric
                            showTrendDetail = true
                        }
                    )
                    Screen.TRENDS -> TrendsOverviewScreen(
                        healthDao = healthDao,
                        onMetricClick = { metric ->
                            trendMetric = metric
                            showTrendDetail = true
                        }
                    )
                    Screen.SETTINGS -> SettingsScreen(
                        bleManager = bleManager,
                        contactDao = contactDao,
                        healthConnectManager = healthConnectManager,
                        onRequestPermissions = onRequestPermissions,
                        onRequestHealthConnectPermissions = onRequestHealthConnectPermissions
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════
//  HOME SCREEN
// ════════════════════════════════════════════════════════════════

@Composable
fun HomeScreen(
    bleManager: BLEManager,
    onRequestPermissions: () -> Unit,
    onMetricClick: (String) -> Unit = {}
) {
    val isConnected by bleManager.isConnected.collectAsState()
    val isSimulating by bleManager.isSimulating.collectAsState()
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
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        // ── Header: Greeting + Avatar ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Hello,",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )
                Text(
                    "Guardian",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = TextPrimary
                )
            }

            // Connection indicator + avatar
            Surface(
                shape = CircleShape,
                color = if (isConnected || isSimulating) GreenContainer else SurfaceDim,
                modifier = Modifier
                    .size(48.dp)
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
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (isConnected || isSimulating) Icons.Filled.BluetoothConnected
                        else Icons.Filled.BluetoothSearching,
                        contentDescription = connectionStatus,
                        tint = if (isConnected || isSimulating) GreenPrimary else TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // ── Connection Status Chip ──
        Spacer(Modifier.height(12.dp))
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (isConnected || isSimulating) GreenContainer else SurfaceDim,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (isConnected) ConnectedGreen
                            else if (isSimulating) StepsAmber
                            else DisconnectedGray
                        )
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        isSimulating -> "Simulation Active"
                        isConnected -> connectedDeviceName ?: "Connected"
                        else -> connectionStatus
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isConnected || isSimulating) GreenPrimary else TextSecondary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Fall Alert ──
        AnimatedVisibility(
            visible = fallDetected,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = AlertRed)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Warning, null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Fall Detected!",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Emergency contacts are being notified.",
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { bleManager.dismissFall() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = AlertRed
                        )
                    ) {
                        Text("I'm OK — Dismiss", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // ── Section: Indexes ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Indexes",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                "Today",
                style = MaterialTheme.typography.bodySmall,
                color = GreenPrimary,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(Modifier.height(12.dp))

        // ── 2×2 Vital Cards Grid ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IndexCard(
                icon = Icons.Filled.FavoriteBorder,
                label = "Pulse",
                value = if (heartRate > 0) heartRate.toString() else "--",
                unit = "BPM",
                accentColor = HeartRed,
                modifier = Modifier.weight(1f),
                onClick = { onMetricClick("heartrate") }
            )
            IndexCard(
                icon = Icons.Filled.WaterDrop,
                label = "SpO\u2082",
                value = if (bloodOxygen > 0) bloodOxygen.toString() else "--",
                unit = "%",
                accentColor = OxygenBlue,
                modifier = Modifier.weight(1f),
                onClick = { onMetricClick("spo2") }
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IndexCard(
                icon = Icons.Filled.DirectionsWalk,
                label = "Steps",
                value = if (steps > 0) formatNumber(steps) else "--",
                unit = "steps",
                accentColor = StepsAmber,
                modifier = Modifier.weight(1f),
                onClick = { onMetricClick("steps") }
            )
            IndexCard(
                icon = Icons.Filled.Shield,
                label = "Fall Detect",
                value = if (isConnected || isSimulating) "Active" else "Off",
                unit = "",
                accentColor = GreenPrimary,
                modifier = Modifier.weight(1f),
                onClick = {}
            )
        }

        Spacer(Modifier.height(24.dp))

        // ── Steps Chart Section ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Activity",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                "Today",
                style = MaterialTheme.typography.bodySmall,
                color = GreenPrimary,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(Modifier.height(12.dp))

        // Activity summary card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Surface),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (steps > 0) formatNumber(steps) else "--",
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = TextPrimary
                        )
                        Text(
                            "steps today",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    // Mini activity ring
                    Box(
                        modifier = Modifier.size(56.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val progress = (steps.coerceAtMost(10000).toFloat() / 10000f)
                        Canvas(Modifier.size(56.dp)) {
                            // Background track
                            drawArc(
                                color = GreenContainer,
                                startAngle = -90f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = 8f, cap = StrokeCap.Round)
                            )
                            // Progress arc
                            drawArc(
                                color = GreenPrimary,
                                startAngle = -90f,
                                sweepAngle = 360f * progress,
                                useCenter = false,
                                style = Stroke(width = 8f, cap = StrokeCap.Round)
                            )
                        }
                        Text(
                            "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = GreenPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Mini bar chart placeholder (last 7 readings visualized)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    val bars = listOf(0.3f, 0.5f, 0.7f, 0.4f, 0.8f, 0.6f, 1f)
                    val days = listOf("M", "T", "W", "T", "F", "S", "S")
                    bars.forEachIndexed { index, height ->
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.6f)
                                    .height((height * 32).dp)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(
                                        if (index == bars.lastIndex) GreenPrimary
                                        else GreenLight.copy(alpha = 0.4f)
                                    )
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                days[index],
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── Simulation Mode Toggle ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isSimulating) GreenContainer else SurfaceDim
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Science,
                    contentDescription = null,
                    tint = if (isSimulating) GreenPrimary else TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Simulation",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                    Text(
                        if (isSimulating) "Live data active" else "Test without hardware",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Switch(
                    checked = isSimulating,
                    onCheckedChange = {
                        if (it) bleManager.startSimulation()
                        else bleManager.stopSimulation()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = GreenPrimary,
                        checkedTrackColor = GreenSoft
                    )
                )
            }
        }

        // Simulate fall button
        AnimatedVisibility(visible = isSimulating) {
            OutlinedButton(
                onClick = { bleManager.simulateFall() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.4f))
            ) {
                Icon(Icons.Default.Warning, null, tint = AlertRed, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Simulate Fall", color = AlertRed, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(Modifier.height(24.dp))
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
//  INDEX CARD (2x2 grid cards)
// ════════════════════════════════════════════════════════════════

@Composable
fun IndexCard(
    icon: ImageVector,
    label: String,
    value: String,
    unit: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = accentColor.copy(alpha = 0.1f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            icon, null,
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Icon(
                    Icons.Default.ArrowForward,
                    contentDescription = "View details",
                    tint = TextSecondary.copy(alpha = 0.4f),
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = TextPrimary
                )
                if (unit.isNotEmpty()) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        unit,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════
//  VITAL CARD (kept for backward compatibility)
// ════════════════════════════════════════════════════════════════

@Composable
fun VitalCard(
    label: String,
    value: String,
    unit: String,
    accentColor: Color,
    bgColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable { onClick() } else Modifier
        ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                if (onClick != null) {
                    Spacer(Modifier.weight(1f))
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = "View trends",
                        tint = accentColor.copy(alpha = 0.5f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    unit,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════
//  TRENDS OVERVIEW SCREEN (New — Tab 2)
// ════════════════════════════════════════════════════════════════

@Composable
fun TrendsOverviewScreen(
    healthDao: HealthDao,
    onMetricClick: (String) -> Unit
) {
    val last24h = remember { System.currentTimeMillis() - 24 * 3600 * 1000 }
    val recentReadings by healthDao.getReadingsSince(last24h).collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        Text(
            "Trends",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = TextPrimary
        )
        Text(
            "Last 24 hours",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(Modifier.height(20.dp))

        // HR Mini-trend card
        TrendSummaryCard(
            title = "Heart Rate",
            icon = Icons.Filled.FavoriteBorder,
            accentColor = HeartRed,
            readings = recentReadings,
            valueExtractor = { it.heartRate.toFloat() },
            unit = "BPM",
            onClick = { onMetricClick("heartrate") }
        )

        Spacer(Modifier.height(12.dp))

        // SpO2 Mini-trend card
        TrendSummaryCard(
            title = "Blood Oxygen",
            icon = Icons.Filled.WaterDrop,
            accentColor = OxygenBlue,
            readings = recentReadings,
            valueExtractor = { it.spO2.toFloat() },
            unit = "%",
            onClick = { onMetricClick("spo2") }
        )

        Spacer(Modifier.height(12.dp))

        // Steps Mini-trend card
        TrendSummaryCard(
            title = "Steps",
            icon = Icons.Filled.DirectionsWalk,
            accentColor = StepsAmber,
            readings = recentReadings,
            valueExtractor = { it.steps.toFloat() },
            unit = "steps",
            onClick = { onMetricClick("steps") }
        )

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun TrendSummaryCard(
    title: String,
    icon: ImageVector,
    accentColor: Color,
    readings: List<HealthReading>,
    valueExtractor: (HealthReading) -> Float,
    unit: String,
    onClick: () -> Unit
) {
    val values = readings.map { valueExtractor(it) }.filter { it > 0f }
    val latest = values.lastOrNull()
    val avg = if (values.isNotEmpty()) values.average().toFloat() else null
    val chartPoints = readings.mapNotNull { r ->
        val v = valueExtractor(r)
        if (v > 0f) r.timestamp to v else null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = accentColor.copy(alpha = 0.1f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = accentColor, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        if (avg != null) "Avg: ${avg.toInt()} $unit" else "No data",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        if (latest != null) latest.toInt().toString() else "--",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Text(unit, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
            }

            if (chartPoints.size >= 2) {
                Spacer(Modifier.height(12.dp))
                TrendChart(
                    points = chartPoints,
                    lineColor = accentColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
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
        shape = RoundedCornerShape(20.dp),
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
                            shape = RoundedCornerShape(12.dp),
                            color = GreenContainer.copy(alpha = 0.5f)
                        ) {
                            Column(Modifier.padding(14.dp)) {
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

@Composable
fun SettingsScreen(
    bleManager: BLEManager,
    contactDao: ContactDao,
    healthConnectManager: HealthConnectManager,
    onRequestPermissions: () -> Unit,
    onRequestHealthConnectPermissions: () -> Unit
) {
    val isConnected by bleManager.isConnected.collectAsState()
    val connectionStatus by bleManager.connectionStatus.collectAsState()
    val contacts by contactDao.getAllContacts().collectAsState(initial = emptyList())
    val hasHcPermissions by healthConnectManager.hasPermissions.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var showAddContactDialog by remember { mutableStateOf(false) }
    var showDeviceDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        Text(
            "Settings",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = TextPrimary
        )

        Spacer(Modifier.height(24.dp))

        // ── Device Connection ──
        SettingsSection("Device") {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Surface),
                border = BorderStroke(1.dp, CardBorder)
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
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isConnected) GreenContainer else SurfaceDim,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Bluetooth,
                                null,
                                tint = if (isConnected) GreenPrimary else TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Bluetooth",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            connectionStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = isConnected,
                        onCheckedChange = null,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = GreenPrimary,
                            checkedTrackColor = GreenSoft
                        )
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Health Connect ──
        SettingsSection("Health Connect") {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Surface),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (!hasHcPermissions) {
                                onRequestHealthConnectPermissions()
                            }
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (hasHcPermissions) GreenContainer else SurfaceDim,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.FavoriteBorder,
                                null,
                                tint = if (hasHcPermissions) GreenPrimary else TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Health Connect",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            if (hasHcPermissions) "Connected — permissions granted"
                            else "Tap to grant permissions",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hasHcPermissions) GreenPrimary else TextSecondary
                        )
                    }
                    Switch(
                        checked = hasHcPermissions,
                        onCheckedChange = {
                            if (!hasHcPermissions) {
                                onRequestHealthConnectPermissions()
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = GreenPrimary,
                            checkedTrackColor = GreenSoft
                        )
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Emergency Contacts ──
        SettingsSection("Emergency Contacts") {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Surface),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column {
                    if (contacts.isEmpty()) {
                        Text(
                            "No contacts added yet.",
                            modifier = Modifier.padding(16.dp),
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        contacts.forEachIndexed { index, contact ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = GreenContainer,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            contact.name.first().uppercase(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = GreenPrimary
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        contact.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
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
                                    Icon(
                                        Icons.Default.Close, "Remove",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            if (index < contacts.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = BorderLight
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        Modifier.padding(horizontal = 16.dp),
                        color = BorderLight
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAddContactDialog = true }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Add, null,
                            tint = GreenPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Add Contact",
                            color = GreenPrimary,
                            fontWeight = FontWeight.Medium,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Testing & Simulation ──
        SettingsSection("Testing & Simulation") {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Surface),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(Modifier.padding(16.dp)) {
                    val isSimulating by bleManager.isSimulating.collectAsState()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Simulation Mode",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "Generate fake health data without BLE",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = isSimulating,
                            onCheckedChange = {
                                if (it) bleManager.startSimulation()
                                else bleManager.stopSimulation()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = GreenPrimary,
                                checkedTrackColor = GreenSoft
                            )
                        )
                    }

                    HorizontalDivider(
                        Modifier.padding(vertical = 12.dp),
                        color = BorderLight
                    )

                    Text(
                        "BLE Service UUIDs",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    BleUuidRow("Service", "0000FFF0-0000-1000-8000-00805f9b34fb")
                    BleUuidRow("Heart Rate", "0000FFF1-0000-1000-8000-00805f9b34fb")
                    BleUuidRow("SpO2", "0000FFF2-0000-1000-8000-00805f9b34fb")
                    BleUuidRow("Steps", "0000FFF3-0000-1000-8000-00805f9b34fb")
                    BleUuidRow("Fall Detect", "0000FFF4-0000-1000-8000-00805f9b34fb")

                    HorizontalDivider(
                        Modifier.padding(vertical = 12.dp),
                        color = BorderLight
                    )

                    Text(
                        "Flipper Zero (UART over BLE)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        "The app auto-detects Flipper Zero via Nordic UART Service. " +
                                "On Flipper: GPIO \u2192 USB-UART Bridge \u2192 Channel: BLE, " +
                                "or use a BLE Serial app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Send newline-terminated commands:",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                    BleUuidRow("Heart Rate", "HR:72")
                    BleUuidRow("SpO2", "SPO2:98")
                    BleUuidRow("Steps", "STEPS:2500")
                    BleUuidRow("Fall", "FALL:1  (FALL:0 to clear)")
                    BleUuidRow("All-in-one", "ALL:72,98,2500")

                    Spacer(Modifier.height(8.dp))
                    Text(
                        "nRF Connect: use Server tab, add service with NOTIFY property.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── About ──
        SettingsSection("About") {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Surface),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Guardian Health",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
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

    // ── Dialogs ──
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

    if (showDeviceDialog) {
        DeviceSelectionDialog(
            bleManager = bleManager,
            onDismiss = { showDeviceDialog = false; bleManager.stopScan() },
            onDeviceSelected = { bleManager.connectToDevice(it); showDeviceDialog = false }
        )
    }
}

// ════════════════════════════════════════════════════════════════
//  TREND DETAIL SCREEN
// ════════════════════════════════════════════════════════════════

enum class TrendTimeRange(val label: String, val hours: Long) {
    ONE_HOUR("1H", 1),
    SIX_HOURS("6H", 6),
    ONE_DAY("24H", 24),
    ONE_WEEK("7D", 24 * 7)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendDetailScreen(
    metric: String,
    healthDao: HealthDao,
    healthConnectManager: HealthConnectManager,
    onNavigateBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedRange by remember { mutableStateOf(TrendTimeRange.ONE_DAY) }

    // Data from Room (local readings)
    val sinceMillis = remember(selectedRange) {
        System.currentTimeMillis() - selectedRange.hours * 3600 * 1000
    }
    val roomReadings by healthDao.getReadingsSince(sinceMillis).collectAsState(initial = emptyList())

    // Data from Health Connect
    var hcPoints by remember { mutableStateOf<List<TrendPoint>>(emptyList()) }
    var isLoadingHc by remember { mutableStateOf(false) }

    // Load Health Connect data when range or metric changes
    LaunchedEffect(selectedRange, metric) {
        isLoadingHc = true
        val start = Instant.now().minusSeconds(selectedRange.hours * 3600)
        val end = Instant.now()
        hcPoints = when (metric) {
            "heartrate" -> healthConnectManager.readHeartRateTrend(start, end)
            "steps" -> healthConnectManager.readStepsTrend(start, end)
            else -> emptyList() // SpO2 not in Health Connect standard
        }
        isLoadingHc = false
    }

    // Derive display data points from Room readings
    val chartPoints: List<Pair<Long, Float>> = remember(roomReadings, metric) {
        roomReadings.mapNotNull { reading ->
            val value = when (metric) {
                "heartrate" -> reading.heartRate.toFloat()
                "spo2" -> reading.spO2.toFloat()
                "steps" -> reading.steps.toFloat()
                else -> null
            }
            value?.let { reading.timestamp to it }
        }
    }

    // Stats
    val values = chartPoints.map { it.second }
    val currentValue = values.lastOrNull() ?: 0f
    val minValue = values.minOrNull() ?: 0f
    val maxValue = values.maxOrNull() ?: 0f
    val avgValue = if (values.isNotEmpty()) values.average().toFloat() else 0f

    // Config per metric
    val (title, unit, accentColor, bgColor) = when (metric) {
        "heartrate" -> listOf("Heart Rate", "bpm", HeartRed, HeartRedBg)
        "spo2" -> listOf("Blood Oxygen", "%", OxygenBlue, OxygenBlueBg)
        "steps" -> listOf("Steps", "steps", StepsAmber, StepsAmberBg)
        else -> listOf("Unknown", "", TextSecondary, Surface)
    }

    @Suppress("UNCHECKED_CAST")
    val accent = accentColor as Color
    @Suppress("UNCHECKED_CAST")
    val bg = bgColor as Color

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
                title as String,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            // ── Current Value Card ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Surface),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = accent.copy(alpha = 0.1f),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            val icon = when (metric) {
                                "heartrate" -> Icons.Filled.FavoriteBorder
                                "spo2" -> Icons.Filled.WaterDrop
                                "steps" -> Icons.Filled.DirectionsWalk
                                else -> Icons.Filled.Info
                            }
                            Icon(icon, null, tint = accent, modifier = Modifier.size(24.dp))
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            "Current",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                if (currentValue > 0f) currentValue.toInt().toString() else "--",
                                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                unit as String,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Time Range Selector ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TrendTimeRange.entries.forEach { range ->
                    val selected = selectedRange == range
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedRange = range },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) GreenPrimary else SurfaceDim
                    ) {
                        Text(
                            range.label,
                            modifier = Modifier.padding(vertical = 10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) Color.White else TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Chart ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Surface),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Trend",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    if (chartPoints.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No data for this time range.\nConnect a device or start simulation.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        TrendChart(
                            points = chartPoints,
                            lineColor = accent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Stats Cards ──
            Text(
                "Statistics",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard("Min", minValue.toInt().toString(), unit as String, accent, Modifier.weight(1f))
                StatCard("Avg", avgValue.toInt().toString(), unit as String, accent, Modifier.weight(1f))
                StatCard("Max", maxValue.toInt().toString(), unit as String, accent, Modifier.weight(1f))
            }

            Spacer(Modifier.height(16.dp))

            // ── Health Connect Data ──
            if (metric != "spo2") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Health Connect",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.width(8.dp))
                            if (isLoadingHc) {
                                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                            }
                        }
                        Spacer(Modifier.height(8.dp))

                        if (hcPoints.isEmpty() && !isLoadingHc) {
                            Text(
                                "No Health Connect data available. " +
                                        "Grant permissions and ensure data has been synced.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        } else {
                            Text(
                                "${hcPoints.size} records in Health Connect",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            if (hcPoints.isNotEmpty()) {
                                val hcMin = hcPoints.minOf { it.value }
                                val hcMax = hcPoints.maxOf { it.value }
                                val hcAvg = hcPoints.map { it.value }.average().toLong()
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    StatCard("Min", hcMin.toString(), unit as String, accent, Modifier.weight(1f))
                                    StatCard("Avg", hcAvg.toString(), unit as String, accent, Modifier.weight(1f))
                                    StatCard("Max", hcMax.toString(), unit as String, accent, Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Recent Readings Table ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Surface),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Recent Readings",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    val recentReadings = roomReadings.takeLast(20).reversed()
                    if (recentReadings.isEmpty()) {
                        Text(
                            "No readings yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    } else {
                        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
                            .withZone(ZoneId.systemDefault())

                        recentReadings.forEachIndexed { index, reading ->
                            val displayValue = when (metric) {
                                "heartrate" -> "${reading.heartRate} bpm"
                                "spo2" -> "${reading.spO2}%"
                                "steps" -> "${reading.steps} steps"
                                else -> "--"
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    timeFormatter.format(Instant.ofEpochMilli(reading.timestamp)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    displayValue,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = TextPrimary
                                )
                            }
                            if (index < recentReadings.lastIndex) {
                                HorizontalDivider(
                                    color = BorderLight,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── Trend Line Chart (Canvas-based, no external dependency) ──
@Composable
fun TrendChart(
    points: List<Pair<Long, Float>>,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    if (points.size < 2) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("Not enough data points", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        return
    }

    val minTime = points.first().first.toFloat()
    val maxTime = points.last().first.toFloat()
    val minVal = points.minOf { it.second }
    val maxVal = points.maxOf { it.second }
    val valRange = if (maxVal - minVal > 0f) maxVal - minVal else 1f
    val timeRange = if (maxTime - minTime > 0f) maxTime - minTime else 1f

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val padTop = 8f
        val padBottom = 8f
        val chartH = h - padTop - padBottom

        // Draw horizontal grid lines
        for (i in 0..4) {
            val y = padTop + chartH * (1f - i / 4f)
            drawLine(
                color = lineColor.copy(alpha = 0.08f),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1f
            )
        }

        // Build the line path
        val path = Path()
        val fillPath = Path()

        points.forEachIndexed { idx, (t, v) ->
            val x = ((t.toFloat() - minTime) / timeRange) * w
            val y = padTop + chartH * (1f - (v - minVal) / valRange)

            if (idx == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, h)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }

        // Fill under the curve
        fillPath.lineTo(
            ((points.last().first.toFloat() - minTime) / timeRange) * w,
            h
        )
        fillPath.close()
        drawPath(
            path = fillPath,
            color = lineColor.copy(alpha = 0.1f)
        )

        // Draw the line
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(
                width = 3f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // Draw dot on last point
        val lastX = ((points.last().first.toFloat() - minTime) / timeRange) * w
        val lastY = padTop + chartH * (1f - (points.last().second - minVal) / valRange)
        drawCircle(color = lineColor, radius = 5f, center = Offset(lastX, lastY))
        drawCircle(color = Color.White, radius = 3f, center = Offset(lastX, lastY))
    }
}

@Composable
fun StatCard(
    label: String,
    value: String,
    unit: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (value == "0") "--" else value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )
            Text(
                unit,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
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
        shape = RoundedCornerShape(20.dp),
        title = { Text("Add Emergency Contact", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank() && phone.isNotBlank()) onAdd(name.trim(), phone.trim()) },
                shape = RoundedCornerShape(10.dp),
                enabled = name.isNotBlank() && phone.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ── Utility ──

fun formatNumber(n: Int): String = when {
    n >= 1_000_000 -> String.format("%.1fM", n / 1_000_000f)
    n >= 10_000 -> String.format("%.1fk", n / 1_000f)
    n >= 1_000 -> String.format("%,d", n)
    else -> n.toString()
}