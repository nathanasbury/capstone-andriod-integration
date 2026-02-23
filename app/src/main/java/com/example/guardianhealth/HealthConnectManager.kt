package com.example.guardianhealth

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A single data point for displaying trends.
 */
data class TrendPoint(
    val timestamp: Instant,
    val value: Long
)

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

    // ── Read Trend Data from Health Connect ───────────────────────

    /**
     * Read heart rate samples from Health Connect for the given time range.
     * Returns a list of TrendPoints sorted by time ascending.
     */
    suspend fun readHeartRateTrend(
        startTime: Instant = Instant.now().minusSeconds(24 * 3600),
        endTime: Instant = Instant.now()
    ): List<TrendPoint> {
        if (!_hasPermissions.value) return emptyList()

        return try {
            val request = ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
            val response = healthConnectClient.readRecords(request)
            response.records
                .flatMap { record ->
                    record.samples.map { sample ->
                        TrendPoint(
                            timestamp = sample.time,
                            value = sample.beatsPerMinute
                        )
                    }
                }
                .sortedBy { it.timestamp }
        } catch (e: Exception) {
            Log.e("HealthConnect", "Failed to read HR trend: ${e.message}")
            emptyList()
        }
    }

    /**
     * Read step records from Health Connect for the given time range.
     * Each record covers a time window; we return the start time + count.
     */
    suspend fun readStepsTrend(
        startTime: Instant = Instant.now().minusSeconds(24 * 3600),
        endTime: Instant = Instant.now()
    ): List<TrendPoint> {
        if (!_hasPermissions.value) return emptyList()

        return try {
            val request = ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
            val response = healthConnectClient.readRecords(request)
            response.records
                .map { record ->
                    TrendPoint(
                        timestamp = record.startTime,
                        value = record.count
                    )
                }
                .sortedBy { it.timestamp }
        } catch (e: Exception) {
            Log.e("HealthConnect", "Failed to read steps trend: ${e.message}")
            emptyList()
        }
    }
}
