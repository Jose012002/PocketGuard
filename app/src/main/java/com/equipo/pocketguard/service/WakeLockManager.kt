package com.equipo.pocketguard.service

import android.os.PowerManager
import javax.inject.Inject
import javax.inject.Singleton

/** Mantiene el CPU despierto mientras la alarma vigila (RF-18). Solo se toma armado (RNF-05). */
interface WakeLockController {
    fun acquire()

    fun release()
}

@Singleton
class WakeLockManager @Inject constructor(
    private val powerManager: PowerManager,
) : WakeLockController {

    private val wakeLock: PowerManager.WakeLock by lazy {
        powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG).apply { setReferenceCounted(false) }
    }

    // Sin tiempo límite a propósito: el sistema lo libera si el proceso muere y el servicio lo suelta al desarmar.
    @Synchronized
    @Suppress("WakelockTimeout")
    override fun acquire() {
        if (!wakeLock.isHeld) wakeLock.acquire()
    }

    @Synchronized
    override fun release() {
        if (wakeLock.isHeld) wakeLock.release()
    }

    private companion object {
        const val TAG = "PocketGuard:guard"
    }
}
