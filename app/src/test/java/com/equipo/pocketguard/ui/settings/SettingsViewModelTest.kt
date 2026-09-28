package com.equipo.pocketguard.ui.settings

import com.equipo.pocketguard.data.GuardStateRepository
import com.equipo.pocketguard.data.InMemoryDataStore
import com.equipo.pocketguard.data.SettingsRepository
import com.equipo.pocketguard.decision.AvailableSensors
import com.equipo.pocketguard.decision.ConfigParam
import com.equipo.pocketguard.decision.DetectionConfig
import com.equipo.pocketguard.decision.GuardState
import com.equipo.pocketguard.decision.SensitivityProfile
import com.equipo.pocketguard.security.BiometricAuthenticator
import com.equipo.pocketguard.ui.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val settings = SettingsRepository(InMemoryDataStore())
    private val repository = GuardStateRepository()
    private val biometric = mockk<BiometricAuthenticator> { every { isAvailable() } returns true }
    private val sensors = AvailableSensors(proximity = true, light = true, accelerometer = true)

    private fun viewModel(): SettingsViewModel = SettingsViewModel(settings, repository, biometric)

    private fun kotlinx.coroutines.test.TestScope.observe(vm: SettingsViewModel) {
        backgroundScope.launch(mainRule.dispatcher) { vm.state.collect { } }
    }

    private fun field(param: ConfigParam) = SettingFields.all.single { it.param == param }

    @Test
    fun `arranca con los valores por defecto y el perfil Media`() = runTest {
        val vm = viewModel()
        observe(vm)

        assertEquals(DetectionConfig.Default, vm.state.value.config)
        assertEquals(SensitivityProfile.MEDIUM, vm.state.value.profile)
        assertFalse(vm.state.value.locked)
    }

    @Test
    fun `cambiar un deslizador guarda el valor y pasa a Personalizado`() = runTest {
        val vm = viewModel()
        observe(vm)

        vm.onFieldChanged(field(ConfigParam.MOTION_THRESHOLD), 3.3f)

        assertEquals(3.3f, settings.config.first().motionThreshold, 1e-4f)
        assertEquals(3.3f, vm.state.value.config.motionThreshold, 1e-4f)
        assertNull(vm.state.value.profile)
    }

    @Test
    fun `un perfil ajusta los tres umbrales a la vez`() = runTest {
        val vm = viewModel()
        observe(vm)

        vm.selectProfile(SensitivityProfile.HIGH)

        val config = vm.state.value.config
        assertEquals(1.5f, config.motionThreshold)
        assertEquals(2.5f, config.lightJumpFactor)
        assertEquals(1_500L, config.coincidenceWindowMs)
        assertEquals(SensitivityProfile.HIGH, vm.state.value.profile)
    }

    @Test
    fun `un perfil conserva los demas parametros personalizados`() = runTest {
        val vm = viewModel()
        observe(vm)
        vm.onFieldChanged(field(ConfigParam.PRE_ALARM_GRACE_MS), 0f)

        vm.selectProfile(SensitivityProfile.LOW)

        assertEquals(0L, vm.state.value.config.preAlarmGraceMs)
        assertEquals(SensitivityProfile.LOW, vm.state.value.profile)
    }

    @Test
    fun `restablecer vuelve a los valores por defecto`() = runTest {
        val vm = viewModel()
        observe(vm)
        vm.selectProfile(SensitivityProfile.HIGH)
        vm.onFieldChanged(field(ConfigParam.STROBE_INTERVAL_MS), 500f)

        vm.reset()

        assertEquals(DetectionConfig.Default, vm.state.value.config)
        assertEquals(DetectionConfig.Default, settings.current())
    }

    @Test
    fun `con la alarma armada los ajustes estan bloqueados y no cambian (CU-09)`() = runTest {
        repository.updateState(GuardState.Stored(5f, sensors))
        val vm = viewModel()
        observe(vm)
        assertTrue(vm.state.value.locked)

        vm.onFieldChanged(field(ConfigParam.MOTION_THRESHOLD), 5f)
        vm.selectProfile(SensitivityProfile.LOW)
        vm.reset()

        assertEquals(DetectionConfig.Default, settings.current())
    }

    @Test
    fun `al desarmar se desbloquean`() = runTest {
        repository.updateState(GuardState.Arming(0, null, sensors))
        val vm = viewModel()
        observe(vm)
        assertTrue(vm.state.value.locked)

        repository.updateState(GuardState.Disarmed)

        assertFalse(vm.state.value.locked)
        vm.onFieldChanged(field(ConfigParam.MOTION_THRESHOLD), 5f)
        assertEquals(5f, settings.current().motionThreshold, 1e-4f)
    }

    @Test
    fun `la biometria se puede habilitar si el telefono la soporta`() = runTest {
        val vm = viewModel()
        observe(vm)
        assertTrue(vm.state.value.biometricSupported)
        assertFalse(vm.state.value.biometricEnabled)

        vm.setBiometricEnabled(true)
        assertTrue(vm.state.value.biometricEnabled)

        vm.setBiometricEnabled(false)
        assertFalse(vm.state.value.biometricEnabled)
    }

    @Test
    fun `sin biometria soportada no se puede habilitar`() = runTest {
        every { biometric.isAvailable() } returns false
        val vm = viewModel()
        observe(vm)

        vm.setBiometricEnabled(true)

        assertFalse(vm.state.value.biometricSupported)
        assertFalse(vm.state.value.biometricEnabled)
    }

    @Test
    fun `la biometria se puede cambiar aunque la alarma este armada solo para deshabilitarla`() = runTest {
        val vm = viewModel()
        observe(vm)
        vm.setBiometricEnabled(true)

        vm.setBiometricEnabled(false)

        assertFalse(settings.biometricEnabled.first())
    }
}
