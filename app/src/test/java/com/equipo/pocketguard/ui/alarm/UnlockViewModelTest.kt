package com.equipo.pocketguard.ui.alarm

import com.equipo.pocketguard.data.InMemoryDataStore
import com.equipo.pocketguard.data.PinRepository
import com.equipo.pocketguard.data.SettingsRepository
import com.equipo.pocketguard.security.AuthAttemptLimiter
import com.equipo.pocketguard.security.BiometricAuthenticator
import com.equipo.pocketguard.security.PinAuthenticator
import com.equipo.pocketguard.security.PinHasher
import com.equipo.pocketguard.ui.FakeGuardController
import com.equipo.pocketguard.ui.MainDispatcherRule
import com.equipo.pocketguard.ui.awaitCondition
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

// Se usa runBlocking y no runTest: runTest avanza solo el tiempo virtual mientras espera al hilo real del hash,
// y aquí el tiempo virtual es el reloj del bloqueo, que debe avanzar únicamente cuando la prueba lo pide.
@OptIn(ExperimentalCoroutinesApi::class)
class UnlockViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val dataStore = InMemoryDataStore()
    private val pinRepository = PinRepository(dataStore, PinHasher(iterations = 10))
    private val settings = SettingsRepository(dataStore)
    // El reloj del limitador es el tiempo virtual del hilo principal: así la cuenta regresiva del bloqueo termina sola.
    private val limiter = AuthAttemptLimiter(
        maxAttempts = 5,
        lockDurationMs = 30_000,
        clock = { mainRule.dispatcher.scheduler.currentTime },
    )
    private val guard = FakeGuardController()
    private val biometric = mockk<BiometricAuthenticator> { every { isAvailable() } returns true }

    private fun viewModel() = UnlockViewModel(PinAuthenticator(pinRepository, limiter), guard, biometric, settings)

    private suspend fun UnlockViewModel.enter(pin: String) {
        pin.forEach(::onDigit)
        submit()
        awaitCondition { !state.value.submitting }
    }

    @Test
    fun `los digitos se acumulan hasta 6 y se ignoran otros caracteres`() = runBlocking {
        pinRepository.setPin("1234")
        val vm = viewModel()
        "12x3".forEach(vm::onDigit)
        assertEquals("123", vm.state.value.pin)
        vm.onDelete()
        assertEquals("12", vm.state.value.pin)
    }

    @Test
    fun `no se puede enviar un PIN de menos de 4 digitos`() = runBlocking {
        pinRepository.setPin("1234")
        val vm = viewModel()
        "123".forEach(vm::onDigit)
        assertFalse(vm.state.value.canSubmit)
        vm.submit()
        assertTrue(guard.disarmCalls.isEmpty())
    }

    @Test
    fun `el PIN correcto desarma y avisa que se desbloqueo`() = runBlocking {
        pinRepository.setPin("1234")
        val vm = viewModel()
        var unlocked = 0
        val collector = launch(mainRule.dispatcher) { vm.unlocked.collect { unlocked++ } }

        vm.enter("1234")
        collector.cancel()

        assertEquals(listOf(true), guard.disarmCalls)
        assertEquals(1, unlocked)
        assertEquals(UnlockUiState(), vm.state.value)
    }

    @Test
    fun `un PIN incorrecto informa el intento fallido, muestra los intentos restantes y limpia el campo`() = runBlocking {
        pinRepository.setPin("1234")
        val vm = viewModel()

        vm.enter("0000")

        assertEquals(listOf(false), guard.disarmCalls)
        assertEquals(4, vm.state.value.wrongPinAttemptsLeft)
        assertEquals("", vm.state.value.pin)
        assertFalse(vm.state.value.locked)
    }

    @Test
    fun `escribir tras un error borra el mensaje`() = runBlocking {
        pinRepository.setPin("1234")
        val vm = viewModel()
        vm.enter("0000")
        vm.onDigit('1')
        assertNull(vm.state.value.wrongPinAttemptsLeft)
    }

    @Test
    fun `al quinto fallo se bloquea 30 segundos y se ignoran los digitos`() = runBlocking {
        pinRepository.setPin("1234")
        val vm = viewModel()

        repeat(5) { vm.enter("0000") }

        assertTrue(vm.state.value.locked)
        assertEquals(30_000L, vm.state.value.lockedMs)
        assertEquals(List(5) { false }, guard.disarmCalls) // los 5 intentos quedan registrados
        vm.onDigit('1')
        assertEquals("", vm.state.value.pin)
    }

    @Test
    fun `durante el bloqueo ni el PIN correcto desarma`() = runBlocking {
        pinRepository.setPin("1234")
        val vm = viewModel()
        repeat(5) { vm.enter("0000") }
        guard.disarmCalls.clear()

        vm.enter("1234")

        assertTrue(guard.disarmCalls.isEmpty())
        assertTrue(vm.state.value.locked)
    }

    @Test
    fun `pasados los 30 segundos el bloqueo termina y el PIN correcto funciona`() = runBlocking {
        pinRepository.setPin("1234")
        val vm = viewModel()
        repeat(5) { vm.enter("0000") }
        assertTrue(vm.state.value.locked)

        mainRule.dispatcher.scheduler.advanceTimeBy(30_001) // deja correr la cuenta regresiva hasta el final
        assertFalse(vm.state.value.locked)

        guard.disarmCalls.clear()
        vm.enter("1234")
        assertEquals(listOf(true), guard.disarmCalls)
    }

    @Test
    fun `reset limpia el PIN y los mensajes`() = runBlocking {
        pinRepository.setPin("1234")
        val vm = viewModel()
        vm.enter("0000")
        vm.onDigit('1')

        vm.reset()

        assertEquals(UnlockUiState(), vm.state.value)
    }

    @Test
    fun `reset conserva un bloqueo que sigue vigente`() = runBlocking {
        pinRepository.setPin("1234")
        val vm = viewModel()
        repeat(5) { vm.enter("0000") }

        vm.reset()

        assertTrue(vm.state.value.locked)
    }

    @Test
    fun `una biometria correcta desarma y levanta el bloqueo`() = runBlocking {
        pinRepository.setPin("1234")
        val vm = viewModel()
        repeat(5) { vm.enter("0000") }
        guard.disarmCalls.clear()

        vm.onBiometricSuccess()

        assertEquals(listOf(true), guard.disarmCalls)
        assertFalse(limiter.isLocked())
        assertEquals(UnlockUiState(), vm.state.value)
    }

    @Test
    fun `la biometria solo esta disponible si el dueno la habilito y el telefono la soporta`() = runBlocking {
        pinRepository.setPin("1234")
        assertFalse(viewModel().biometricAvailable.value) // no habilitada

        settings.setBiometricEnabled(true)
        assertTrue(viewModel().biometricAvailable.value)

        every { biometric.isAvailable() } returns false
        assertFalse(viewModel().biometricAvailable.value)
    }
}
