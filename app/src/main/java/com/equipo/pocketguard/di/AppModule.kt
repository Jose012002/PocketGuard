package com.equipo.pocketguard.di

import android.content.Context
import android.os.PowerManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.equipo.pocketguard.actuation.AlarmController
import com.equipo.pocketguard.data.ArmedStateStore
import com.equipo.pocketguard.data.DataStoreArmedStateStore
import com.equipo.pocketguard.data.SettingsRepository
import com.equipo.pocketguard.data.eventlog.EventRecorder
import com.equipo.pocketguard.data.eventlog.LogcatEventRecorder
import com.equipo.pocketguard.decision.DetectionConfigProvider
import com.equipo.pocketguard.decision.EffectExecutor
import com.equipo.pocketguard.service.GuardController
import com.equipo.pocketguard.service.ServiceGuardController
import com.equipo.pocketguard.service.WakeLockController
import com.equipo.pocketguard.service.WakeLockManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Dependencias globales de la aplicación. Se irá poblando fase a fase. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    abstract fun bindDetectionConfigProvider(impl: SettingsRepository): DetectionConfigProvider

    @Binds
    abstract fun bindGuardController(impl: ServiceGuardController): GuardController

    @Binds
    abstract fun bindEffectExecutor(impl: AlarmController): EffectExecutor

    @Binds
    abstract fun bindWakeLockController(impl: WakeLockManager): WakeLockController

    @Binds
    abstract fun bindArmedStateStore(impl: DataStoreArmedStateStore): ArmedStateStore

    // Provisional hasta la fase 10, que lo reemplaza por el registro en Room.
    @Binds
    abstract fun bindEventRecorder(impl: LogcatEventRecorder): EventRecorder

    companion object {
        /** Un solo DataStore para toda la app: dos instancias sobre el mismo archivo no están permitidas. */
        @Provides
        @Singleton
        fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
                produceFile = { context.preferencesDataStoreFile("pocketguard") },
            )

        @Provides
        fun providePowerManager(@ApplicationContext context: Context): PowerManager =
            context.getSystemService(PowerManager::class.java)
    }
}
