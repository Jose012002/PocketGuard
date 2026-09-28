package com.equipo.pocketguard.ui.onboarding

import com.equipo.pocketguard.data.InMemoryDataStore
import com.equipo.pocketguard.data.PinRepository
import com.equipo.pocketguard.security.PinHasher
import com.equipo.pocketguard.ui.MainDispatcherRule
import com.equipo.pocketguard.ui.awaitCondition
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OnboardingViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repository = PinRepository(InMemoryDataStore(), PinHasher(iterations = 10))
    private val vm = OnboardingViewModel(repository)

    private fun type(pin: String) = pin.forEach(vm::onDigit)

    private fun goToCreatePin() = repeat(OnboardingViewModel.INTRO_PAGES) { vm.nextIntroPage() }

    @Test
    fun `empieza en la introduccion, pagina 0`() {
        assertEquals(OnboardingStep.INTRO, vm.state.value.step)
        assertEquals(0, vm.state.value.introPage)
    }

    @Test
    fun `la introduccion tiene 3 paginas y luego pide el PIN`() {
        vm.nextIntroPage()
        assertEquals(1, vm.state.value.introPage)
        vm.nextIntroPage()
        assertEquals(2, vm.state.value.introPage)
        assertEquals(OnboardingStep.INTRO, vm.state.value.step)
        vm.nextIntroPage()
        assertEquals(OnboardingStep.CREATE_PIN, vm.state.value.step)
    }

    @Test
    fun `se puede volver a la pagina anterior pero no por debajo de la primera`() {
        vm.nextIntroPage()
        vm.previousIntroPage()
        assertEquals(0, vm.state.value.introPage)
        vm.previousIntroPage()
        assertEquals(0, vm.state.value.introPage)
    }

    @Test
    fun `los digitos se acumulan hasta 6 y se ignoran los demas caracteres`() {
        goToCreatePin()
        type("12a3456789")
        assertEquals("123456", vm.state.value.pin)
    }

    @Test
    fun `borrar quita el ultimo digito`() {
        goToCreatePin()
        type("123")
        vm.onDelete()
        assertEquals("12", vm.state.value.pin)
        vm.onDelete()
        vm.onDelete()
        vm.onDelete()
        assertEquals("", vm.state.value.pin)
    }

    @Test
    fun `un PIN de menos de 4 digitos muestra error y no avanza`() {
        goToCreatePin()
        type("123")
        vm.submitPin()
        assertEquals(OnboardingStep.CREATE_PIN, vm.state.value.step)
        assertEquals(OnboardingError.PIN_TOO_SHORT, vm.state.value.error)
    }

    @Test
    fun `escribir otro digito limpia el error`() {
        goToCreatePin()
        type("123")
        vm.submitPin()
        vm.onDigit('4')
        assertNull(vm.state.value.error)
    }

    @Test
    fun `un PIN valido pasa a la confirmacion y limpia el campo`() {
        goToCreatePin()
        type("1234")
        vm.submitPin()
        assertEquals(OnboardingStep.CONFIRM_PIN, vm.state.value.step)
        assertEquals("", vm.state.value.pin)
    }

    @Test
    fun `si los PIN no coinciden vuelve a crear el PIN con error (3a)`() = runTest {
        goToCreatePin()
        type("1234")
        vm.submitPin()
        type("1235")
        vm.submitPin()

        assertEquals(OnboardingStep.CREATE_PIN, vm.state.value.step)
        assertEquals(OnboardingError.PIN_MISMATCH, vm.state.value.error)
        assertEquals("", vm.state.value.pin)
        assertFalse(repository.hasPin.first())
    }

    @Test
    fun `tras un desajuste se puede crear un PIN nuevo y confirmarlo`() = runTest {
        goToCreatePin()
        type("1234")
        vm.submitPin()
        type("9999")
        vm.submitPin()

        type("5678")
        vm.submitPin()
        type("5678")
        vm.submitPin()
        awaitCondition { !vm.state.value.saving }

        assertEquals(OnboardingStep.PERMISSIONS, vm.state.value.step)
        assertTrue(repository.verify("5678"))
        assertFalse(repository.verify("1234"))
    }

    @Test
    fun `confirmar el PIN lo guarda como hash y pasa a los permisos`() = runTest {
        goToCreatePin()
        type("482913")
        vm.submitPin()
        type("482913")
        vm.submitPin()
        awaitCondition { !vm.state.value.saving }

        assertEquals(OnboardingStep.PERMISSIONS, vm.state.value.step)
        assertTrue(repository.hasPin.first())
        assertTrue(repository.verify("482913"))
        assertEquals("", vm.state.value.pin)
        assertFalse(vm.state.value.saving)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `el estado de la pantalla nunca contiene el PIN de la primera entrada`() {
        goToCreatePin()
        type("482913")
        vm.submitPin()
        assertFalse(vm.state.value.toString().contains("482913"))
    }

    @Test
    fun `denegar el permiso lo registra y no impide terminar`() {
        vm.onPermissionResult(false)
        assertEquals(false, vm.state.value.notificationsGranted)
        assertFalse(vm.state.value.finished)
        vm.finish()
        assertTrue(vm.state.value.finished)
    }

    @Test
    fun `conceder el permiso se registra`() {
        vm.onPermissionResult(true)
        assertEquals(true, vm.state.value.notificationsGranted)
    }

    @Test
    fun `enviar el PIN fuera de los pasos de PIN no hace nada`() {
        vm.submitPin()
        assertEquals(OnboardingStep.INTRO, vm.state.value.step)
        assertNull(vm.state.value.error)
    }
}
