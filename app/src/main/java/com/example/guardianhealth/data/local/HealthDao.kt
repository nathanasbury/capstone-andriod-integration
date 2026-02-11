package com.example.guardianhealth.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthDao {

    @Query("SELECT * FROM health_readings ORDER BY timestamp DESC")
    fun getAllReadings(): Flow<List<HealthReading>>

    @Query("SELECT * FROM health_readings ORDER BY timestamp DESC LIMIT 1")
    fun getLatestReading(): Flow<HealthReading?>

    @Query("SELECT * FROM health_readings WHERE isFallDetected = 1 ORDER BY timestamp DESC")
    fun getFallHistory(): Flow<List<HealthReading>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReading(reading: HealthReading)

    @Query("DELETE FROM health_readings")
    suspend fun clearAll()
}
