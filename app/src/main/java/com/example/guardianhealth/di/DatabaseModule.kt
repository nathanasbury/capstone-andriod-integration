package com.example.guardianhealth.di

import android.content.Context
import androidx.room.Room
import com.example.guardianhealth.data.local.AppDatabase
import com.example.guardianhealth.data.local.HealthDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "guardian_health.db"
        ).fallbackToDestructiveMigration() // For development only
         .build()
    }

    @Provides
    fun provideHealthDao(database: AppDatabase): HealthDao {
        return database.healthDao()
    }
}
