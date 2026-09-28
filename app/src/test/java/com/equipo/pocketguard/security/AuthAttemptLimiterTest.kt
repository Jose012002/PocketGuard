package com.equipo.pocketguard.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthAttemptLimiterTest {

    private var now = 1_000L
    private val limiter = AuthAttemptLimiter(maxAttempts = 5, lockDurationMs = 30_000, clock = { now })

    private fun fail(times: Int) = repeat(times) { limiter.registerFailure() }

    @Test
    fun `al inicio no hay bloqueo y quedan 5 intentos`() {
        assertFalse(limiter.isLocked())
        assertEquals(0L, limiter.remainingLockMs())
        assertEquals(5, limiter.attemptsLeft())
    }

    @Test
    fun `cuatro fallos no bloquean`() {
        fail(4)
        assertFalse(limiter.isLocked())
        assertEquals(1, limiter.attemptsLeft())
    }

    @Test
    fun `el quinto fallo bloquea 30 segundos`() {
        fail(5)
        assertTrue(limiter.isLocked())
        assertEquals(30_000L, limiter.remainingLockMs())
    }

    @Test
    fun `el bloqueo baja con el tiempo y termina a los 30 segundos`() {
        fail(5)
        now += 12_000
        assertEquals(18_000L, limiter.remainingLockMs())
        now += 17_999
        assertTrue(limiter.isLocked())
        now += 1
        assertFalse(limiter.isLocked())
        assertEquals(0L, limiter.remainingLockMs())
    }

    @Test
    fun `tras el bloqueo se recuperan los 5 intentos`() {
        fail(5)
        now += 30_000
        assertEquals(5, limiter.attemptsLeft())
        fail(4)
        assertFalse(limiter.isLocked())
        limiter.registerFailure()
        assertTrue(limiter.isLocked())
    }

    @Test
    fun `un fallo durante el bloqueo no lo extiende`() {
        fail(5)
        now += 10_000
        limiter.registerFailure()
        assertEquals(20_000L, limiter.remainingLockMs())
        assertEquals(5, limiter.attemptsLeft())
    }

    @Test
    fun `un acierto reinicia los fallos`() {
        fail(4)
        limiter.registerSuccess()
        assertEquals(5, limiter.attemptsLeft())
        fail(4)
        assertFalse(limiter.isLocked())
    }

    @Test
    fun `un acierto levanta el bloqueo`() {
        fail(5)
        limiter.registerSuccess()
        assertFalse(limiter.isLocked())
    }

    @Test
    fun `los valores por defecto son 5 intentos y 30 segundos`() {
        assertEquals(5, AuthAttemptLimiter.MAX_ATTEMPTS)
        assertEquals(30_000L, AuthAttemptLimiter.LOCK_DURATION_MS)
    }
}
