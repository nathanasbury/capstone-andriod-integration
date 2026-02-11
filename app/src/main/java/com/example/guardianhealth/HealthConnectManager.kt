package com.example.guardianhealth

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.units.HeartRate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthConnectManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val healthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    // List of permissions we need
    val permissions = setOf(
        HealthPermission.getWritePermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getWritePermission(StepsRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class)
    )

    private val _hasPermissions = MutableStateFlow(false)
    val hasPermissions: StateFlow<Boolean> = _hasPermissions

    suspend fun checkPermissions() {
        val granted = healthConnectClient.permissionController.getGrantedPermissions()
        _hasPermissions.value = granted.containsAll(permissions)
    }

    suspend fun writeHeartRate(bpm: Int, time: Instant = Instant.now()) {
        if (!_hasPermissions.value) return

        try {
            val record = HeartRateRecord(
                startTime = time,
                startZoneOffset = ZoneOffset.UTC,
                endTime = time.plusSeconds(1), // Point in time (approx)
                endZoneOffset = ZoneOffset.UTC,
                samples = listOf(
                    HeartRateRecord.Sample(
                        time = time,
                        beatsPerMinute = bpm.toLong()
                    )
                )
            )
            healthConnectClient.insertRecords(listOf(record))
        } catch (e: Exception) {
            // Handle error (e.g., permission revoked)
        }
    }

    suspend fun writeSteps(count: Int, startTime: Instant, endTime: Instant) {
        if (!_hasPermissions.value) return
        
        try {
            val record = StepsRecord(
                startTime = startTime,
                startZoneOffset = ZoneOffset.UTC,
                endTime = endTime,
                endZoneOffset = ZoneOffset.UTC,
                count = count.toLong()
            )
            healthConnectClient.insertRecords(listOf(record))
        } catch (e: Exception) {
            // Handle error
        }
    }
}
