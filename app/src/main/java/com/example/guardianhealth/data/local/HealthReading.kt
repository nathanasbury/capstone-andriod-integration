package com.example.guardianhealth.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "health_readings")
data class HealthReading(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val heartRate: Int,
    val spO2: Int,
    val steps: Int,
    val isFallDetected: Boolean = false
) {
    // Helper to get Instant
    val time: Instant
        get() = Instant.ofEpochMilli(timestamp)
}
