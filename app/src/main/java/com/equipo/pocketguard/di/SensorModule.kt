package com.equipo.pocketguard.di

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.SensorManager
import com.equipo.pocketguard.capture.AndroidSensorDataSource
import com.equipo.pocketguard.capture.SensorDataSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SensorModule {

    @Binds
    @Singleton
    abstract fun bindSensorDataSource(impl: AndroidSensorDataSource): SensorDataSource

    companion object {
        @Provides
        fun provideSensorManager(@ApplicationContext context: Context): SensorManager =
            context.getSystemService(SensorManager::class.java)

        @Provides
        fun providePackageManager(@ApplicationContext context: Context): PackageManager = context.packageManager
    }
}
