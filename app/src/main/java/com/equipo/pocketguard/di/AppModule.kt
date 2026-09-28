package com.equipo.pocketguard.di

import com.equipo.pocketguard.decision.DetectionConfig
import com.equipo.pocketguard.decision.DetectionConfigProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Dependencias globales de la aplicación. Se irá poblando fase a fase. */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // Provisional hasta la fase 7, que lo reemplaza por la configuración guardada en DataStore.
    @Provides
    fun provideDetectionConfigProvider(): DetectionConfigProvider = DetectionConfigProvider { DetectionConfig.Default }
}
