package com.example.guardianhealth.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [HealthReading::class, Contact::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun healthDao(): HealthDao
    abstract fun contactDao(): ContactDao
}
