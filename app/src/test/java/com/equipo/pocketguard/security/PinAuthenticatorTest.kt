package com.equipo.pocketguard.security

import com.equipo.pocketguard.data.InMemoryDataStore
import com.equipo.pocketguard.data.PinRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinAuthenticatorTest {

    private var now = 0L
    private val limiter = AuthAttemptLimiter(maxAttempts = 5, lockDurationMs = 30_000, clock = { now })
    private val repository = PinRepository(InMemoryDataStore(), PinHasher(iterations = 10))
    private val auth = PinAuthenticator(repository, limiter)

    @Test
    fun `el PIN correcto autentica`() = runTest {
        repository.setPin("1234")
        assertEquals(AuthResult.Success, auth.verify("1234"))
    }

    @Test
    fun `un PIN incorrecto informa los intentos que quedan`() = runTest {
        repository.setPin("1234")
        assertEquals(AuthResult.WrongPin(attemptsLeft = 4), auth.verify("0000"))
        assertEquals(AuthResult.WrongPin(attemptsLeft = 3), auth.verify("0000"))
    }

    @Test
    fun `el quinto fallo bloquea el ingreso 30 segundos`() = runTest {
        repository.setPin("1234")
        repeat(4) { auth.verify("0000") }
        assertEquals(AuthResult.Locked(30_000), auth.verify("0000"))
    }

    @Test
    fun `durante el bloqueo ni el PIN correcto entra`() = runTest {
        repository.setPin("1234")
        repeat(5) { auth.verify("0000") }
        now += 10_000
        assertEquals(AuthResult.Locked(20_000), auth.verify("1234"))
    }

    @Test
    fun `pasado el bloqueo el PIN correcto vuelve a funcionar`() = runTest {
        repository.setPin("1234")
        repeat(5) { auth.verify("0000") }
        now += 30_000
        assertEquals(AuthResult.Success, auth.verify("1234"))
    }

    @Test
    fun `un acierto reinicia el contador de fallos`() = runTest {
        repository.setPin("1234")
        repeat(4) { auth.verify("0000") }
        auth.verify("1234")
        assertEquals(AuthResult.WrongPin(attemptsLeft = 4), auth.verify("0000"))
    }

    @Test
    fun `sin PIN configurado nada autentica`() = runTest {
        assertTrue(auth.verify("1234") is AuthResult.WrongPin)
    }

    @Test
    fun `una biometria correcta reinicia el contador y levanta el bloqueo`() = runTest {
        repository.setPin("1234")
        repeat(5) { auth.verify("0000") }
        assertTrue(auth.remainingLockMs() > 0)

        auth.registerBiometricSuccess()

        assertFalse(auth.remainingLockMs() > 0)
        assertEquals(AuthResult.WrongPin(attemptsLeft = 4), auth.verify("0000"))
    }
}
