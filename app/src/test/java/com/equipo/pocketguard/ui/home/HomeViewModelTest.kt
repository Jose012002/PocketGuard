package com.equipo.pocketguard.ui.home

import com.equipo.pocketguard.capture.SensorCapabilities
import com.equipo.pocketguard.capture.SensorCapabilityChecker
import com.equipo.pocketguard.data.GuardStateRepository
import com.equipo.pocketguard.decision.AvailableSensors
import com.equipo.pocketguard.decision.GuardState
import com.equipo.pocketguard.decision.MessageCode
import com.equipo.pocketguard.ui.FakeGuardController
import com.equipo.pocketguard.ui.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val full = SensorCapabilities(
        proximityMaxRangeCm = 5f,
        hasLight = true,
        hasAccelerometer = true,
        hasLinearAcceleration = true,
        hasFlash = true,
    )
    private val repository = GuardStateRepository()
    private val guard = FakeGuardController()

    private fun viewModel(capabilities: SensorCapabilities = full): HomeViewModel {
        val checker = mockk<SensorCapabilityChecker> { every { check() } returns capabilities }
        return HomeViewModel(repository, checker, guard)
    }

    private val sensors = AvailableSensors(proximity = true, light = true, accelerometer = true)

    @Test
    fun `desarmado y con sensores completos se puede armar`() = runTest {
        val vm = viewModel()
        backgroundScope.launch(mainRule.dispatcher) { vm.state.collect { } }

        assertEquals(GuardState.Disarmed, vm.state.value.guardState)
        assertTrue(vm.state.value.canArm)
        assertFalse(vm.state.value.isArmed)
    }

    @Test
    fun `armar envia la orden al servicio con un solo toque`() = runTest {
        val vm = viewModel()
        backgroundScope.launch(mainRule.dispatcher) { vm.state.collect { } }

        vm.arm()

        assertEquals(1, guard.armCalls)
    }

    @Test
    fun `sin los sensores minimos no se puede armar y arm no hace nada (RF-08)`() = runTest {
        val vm = viewModel(full.copy(proximityMaxRangeCm = null, hasLight = false))
        backgroundScope.launch(mainRule.dispatcher) { vm.state.collect { } }

        assertFalse(vm.state.value.canArm)
        vm.arm()

        assertEquals(0, guard.armCalls)
    }

    @Test
    fun `en modo degradado sigue pudiendo armar`() = runTest {
        val vm = viewModel(full.copy(hasLight = false))
        backgroundScope.launch(mainRule.dispatcher) { vm.state.collect { } }

        assertTrue(vm.state.value.canArm)
        vm.arm()
        assertEquals(1, guard.armCalls)
    }

    @Test
    fun `el estado de la interfaz sigue al repositorio`() = runTest {
        val vm = viewModel()
        backgroundScope.launch(mainRule.dispatcher) { vm.state.collect { } }

        repository.updateState(GuardState.Arming(0, null, sensors))
        assertTrue(vm.state.value.isArmed)
        assertFalse(vm.state.value.canArm)

        repository.updateState(GuardState.Stored(5f, sensors))
        assertEquals(GuardState.Stored(5f, sensors), vm.state.value.guardState)

        repository.updateState(GuardState.Disarmed)
        assertFalse(vm.state.value.isArmed)
        assertTrue(vm.state.value.canArm)
    }

    @Test
    fun `estando armado arm no envia otra orden`() = runTest {
        val vm = viewModel()
        backgroundScope.launch(mainRule.dispatcher) { vm.state.collect { } }
        repository.updateState(GuardState.Stored(5f, sensors))

        vm.arm()

        assertEquals(0, guard.armCalls)
    }

    @Test
    fun `si la app abre con la alarma ya armada lo refleja desde el inicio`() = runTest {
        repository.updateState(GuardState.Stored(5f, sensors))
        val vm = viewModel()

        assertTrue(vm.state.value.isArmed)
    }

    @Test
    fun `los avisos del servicio llegan a la pantalla`() = runTest {
        val vm = viewModel()
        val received = mutableListOf<MessageCode>()
        backgroundScope.launch(mainRule.dispatcher) { vm.messages.collect { received += it } }

        repository.emitMessage(MessageCode.ARMING_TIMEOUT)

        assertEquals(listOf(MessageCode.ARMING_TIMEOUT), received)
    }
}
