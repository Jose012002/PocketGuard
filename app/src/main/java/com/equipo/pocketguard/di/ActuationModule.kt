package com.equipo.pocketguard.di

import android.content.Context
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import com.equipo.pocketguard.actuation.AndroidTorchDriver
import com.equipo.pocketguard.actuation.TorchDriver
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Ámbito de corrutinas de los actuadores: vive lo que la aplicación y un fallo no cancela a los demás. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ActuationScope

@Module
@InstallIn(SingletonComponent::class)
abstract class ActuationModule {

    @Binds
    @Singleton
    abstract fun bindTorchDriver(impl: AndroidTorchDriver): TorchDriver

    companion object {
        @Provides
        @Singleton
        @ActuationScope
        fun provideActuationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        @Provides
        fun provideAudioManager(@ApplicationContext context: Context): AudioManager =
            context.getSystemService(AudioManager::class.java)

        @Provides
        fun provideCameraManager(@ApplicationContext context: Context): CameraManager =
            context.getSystemService(CameraManager::class.java)

        @Provides
        fun provideVibrator(@ApplicationContext context: Context): Vibrator =
            // API 31 deprecó el Vibrator global a favor de VibratorManager.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
    }
}
