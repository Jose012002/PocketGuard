package com.equipo.pocketguard.security

import com.equipo.pocketguard.data.PinRepository
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AuthResult {
    data object Success : AuthResult

    data class WrongPin(val attemptsLeft: Int) : AuthResult

    /** Hay que esperar [remainingMs] antes de volver a intentar (RF-05). */
    data class Locked(val remainingMs: Long) : AuthResult
}

/** Verifica el PIN aplicando el límite de intentos. Es la única vía de autenticación por PIN para las pantallas. */
@Singleton
class PinAuthenticator @Inject constructor(
    private val pinRepository: PinRepository,
    private val limiter: AuthAttemptLimiter,
) {
    suspend fun verify(pin: String): AuthResult {
        val locked = limiter.remainingLockMs()
        if (locked > 0) return AuthResult.Locked(locked)

        if (pinRepository.verify(pin)) {
            limiter.registerSuccess()
            return AuthResult.Success
        }
        limiter.registerFailure()
        val lockedNow = limiter.remainingLockMs()
        return if (lockedNow > 0) AuthResult.Locked(lockedNow) else AuthResult.WrongPin(limiter.attemptsLeft())
    }

    /** Una biometría correcta también restablece el contador de intentos. */
    fun registerBiometricSuccess() = limiter.registerSuccess()

    fun remainingLockMs(): Long = limiter.remainingLockMs()
}
