package com.example.guardianhealth

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HealthTrackerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HealthTrackerScreen()
                }
            }
        }
    }
}

@Composable
fun HealthTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF6200EE),
            secondary = Color(0xFF03DAC6),
            background = Color(0xFFF5F5F5)
        ),
        content = content
    )
}

@Composable
fun HealthTrackerScreen() {
    // State variables - these will be updated by BLE data later
    var isConnected by remember { mutableStateOf(false) }
    var steps by remember { mutableStateOf(0) }
    var heartRate by remember { mutableStateOf(0) }
    var bloodOxygen by remember { mutableStateOf(0) }
    var fallDetected by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header with connection status
        ConnectionStatusCard(isConnected = isConnected)

        Spacer(modifier = Modifier.height(16.dp))

        // Fall Alert Banner (only shows when fall detected)
        if (fallDetected) {
            FallAlertBanner(
                onDismiss = { fallDetected = false }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Health metrics cards
        HealthMetricCard(
            title = "Steps",
            value = steps.toString(),
            unit = "steps",
            color = Color(0xFF4CAF50)
        )

        Spacer(modifier = Modifier.height(12.dp))

        HealthMetricCard(
            title = "Heart Rate",
            value = heartRate.toString(),
            unit = "bpm",
            color = Color(0xFFF44336)
        )

        Spacer(modifier = Modifier.height(12.dp))

        HealthMetricCard(
            title = "Blood Oxygen",
            value = bloodOxygen.toString(),
            unit = "%",
            color = Color(0xFF2196F3)
        )

        Spacer(modifier = Modifier.weight(1f))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = {
                    // TODO: Connect to BLE device
                    isConnected = !isConnected
                },
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            ) {
                Text(if (isConnected) "Disconnect" else "Connect Device")
            }

            Button(
                onClick = {
                    // TODO: Open emergency contacts settings
                },
                modifier = Modifier.weight(1f).padding(start = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF9800)
                )
            ) {
                Text("Emergency Setup")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Test buttons (for development - remove later)
        OutlinedButton(
            onClick = {
                // Simulate incoming data
                steps = (0..10000).random()
                heartRate = (60..100).random()
                bloodOxygen = (95..100).random()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Simulate Data (Test)")
        }

        OutlinedButton(
            onClick = { fallDetected = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Simulate Fall (Test)")
        }
    }
}

@Composable
fun ConnectionStatusCard(isConnected: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status indicator dot
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(
                        color = if (isConnected) Color(0xFF4CAF50) else Color(0xFFF44336),
                        shape = CircleShape
                    )
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = if (isConnected) "Device Connected" else "Device Disconnected",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = if (isConnected) "Receiving data..." else "Tap 'Connect Device' to start",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun HealthMetricCard(
    title: String,
    value: String,
    unit: String,
    color: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Color indicator bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(60.dp)
                    .background(color, RoundedCornerShape(2.dp))
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                Row(
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = value,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = unit,
                        fontSize = 16.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 4.dp)
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
            containerColor = Color(0xFFFF5252)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "⚠️ FALL DETECTED",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Emergency contacts have been notified",
                color = Color.White,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFFFF5252)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("I'm OK - Dismiss Alert")
            }
        }
    }
}
