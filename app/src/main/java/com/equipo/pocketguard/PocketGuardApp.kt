package com.equipo.pocketguard

import android.app.Application
import com.equipo.pocketguard.actuation.AlarmNotifier
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/** Punto de entrada de Hilt. Crea los canales de notificación al iniciar el proceso. */
@HiltAndroidApp
class PocketGuardApp : Application() {

    @Inject
    lateinit var alarmNotifier: AlarmNotifier

    override fun onCreate() {
        super.onCreate()
        alarmNotifier.createChannels()
    }
}
