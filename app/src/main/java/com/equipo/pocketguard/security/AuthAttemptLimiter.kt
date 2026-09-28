package com.equipo.pocketguard.security

import android.os.SystemClock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tras [maxAttempts] intentos fallidos seguidos bloquea el ingreso durante [lockDurationMs] (RF-05).
 * Es un singleton compartido por todas las pantallas, así el bloqueo no se evade cambiando de pantalla.
 * Vive en memoria: reiniciar el proceso lo restablece.
 */
@Singleton
class AuthAttemptLimiter(
    private val maxAttempts: Int,
    private val lockDurationMs: Long,
    private val clock: () -> Long,
) {
    @Inject
    constructor() : this(MAX_ATTEMPTS, LOCK_DURATION_MS, SystemClock::elapsedRealtime)

    private var failures = 0
    private var lockedUntil = 0L

    /** Milisegundos que faltan para poder intentar de nuevo; 0 si no hay bloqueo. */
    @Synchronized
    fun remainingLockMs(): Long = maxOf(0L, lockedUntil - clock())

    @Synchronized
    fun isLocked(): Boolean = remainingLockMs() > 0

    /** Intentos que quedan antes del bloqueo. */
    @Synchronized
    fun attemptsLeft(): Int = maxAttempts - failures

    /** Registra un intento fallido. Al llegar al máximo inicia el bloqueo y reinicia el contador. */
    @Synchronized
    fun registerFailure() {
        if (isLocked()) return
        failures++
        if (failures >= maxAttempts) {
            lockedUntil = clock() + lockDurationMs
            failures = 0
        }
    }

    @Synchronized
    fun registerSuccess() {
        failures = 0
        lockedUntil = 0L
    }

    companion object {
        const val MAX_ATTEMPTS = 5
        const val LOCK_DURATION_MS = 30_000L
    }
}
