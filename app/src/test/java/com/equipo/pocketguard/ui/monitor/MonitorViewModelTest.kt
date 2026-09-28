package com.equipo.pocketguard.ui.monitor

import com.equipo.pocketguard.capture.FakeSensorDataSource
import com.equipo.pocketguard.capture.SensorCapabilities
import com.equipo.pocketguard.capture.SensorCapabilityChecker
import com.equipo.pocketguard.capture.TraceRecorder
import com.equipo.pocketguard.data.GuardStateRepository
import com.equipo.pocketguard.data.InMemoryDataStore
import com.equipo.pocketguard.data.SettingsRepository
import com.equipo.pocketguard.decision.AvailableSensors
import com.equipo.pocketguard.decision.DetectionConfig
import com.equipo.pocketguard.decision.GuardState
import com.equipo.pocketguard.decision.Indicators
import com.equipo.pocketguard.processing.SensorReading
import com.equipo.pocketguard.processing.SensorSnapshot
import com.equipo.pocketguard.ui.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class MonitorViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val full = SensorCapabilities(
        proximityMaxRangeCm = 5f,
        hasLight = true,
        hasAccelerometer = true,
        hasLinearAcceleration = true,
        hasFlash = true,
    )
    private val sensors = FakeSensorDataSource()
    private val repository = GuardStateRepository()
    private val settings = SettingsRepository(InMemoryDataStore())
    private val all = AvailableSensors(proximity = true, light = true, accelerometer = true)

    private fun viewModel(caps: SensorCapabilities = full): MonitorViewModel {
        val checker = mockk<SensorCapabilityChecker> { every { check() } returns caps }
        val recorder = TraceRecorder(File(tmp.root, "traces")) { 0L }
        return MonitorViewModel(sensors, checker, repository, settings, recorder)
    }

    private fun ms(v: Long) = v * 1_000_000L

    // ---------------------------------------------------------------- indicadores (función pura)

    private fun snap(near: Boolean = true, lux: Float? = 5f, motion: Float = 0f) = SensorSnapshot(
        isNear = near, distanceCm = if (near) 0f else 5f, lux = lux, isDark = true, motion = motion, timestampNs = 0,
    )

    private val config = DetectionConfig.Default

    @Test
    fun `sin lectura no hay indicadores`() {
        assertEquals(Indicators.None, monitorIndicators(GuardState.Disarmed, null, config, full))
        assertEquals(Indicators.None, monitorIndicators(GuardState.Disarmed, snap(), config, null))
    }

    @Test
    fun `en Suspicion se muestran los indicadores acumulados por la ventana`() {
        val flags = Indicators(proximityFar = true, lightJump = true, motion = false)
        val state = GuardState.Suspicion(0, 5f, flags, all)
        // La lectura actual ya no cumple nada, pero la ventana recuerda lo acumulado.
        assertEquals(flags, monitorIndicators(state, snap(), config, full))
    }

    @Test
    fun `en Stored se evaluan contra la luz base`() {
        val stored = GuardState.Stored(baselineLux = 50f, sensors = all) // umbral de salto = max(200, 30)
        assertFalse(monitorIndicators(stored, snap(lux = 150f), config, full).lightJump)
        assertTrue(monitorIndicators(stored, snap(lux = 200f), config, full).lightJump)
    }

    @Test
    fun `sin luz base el salto de luz se mide contra el minimo`() {
        val disarmed = GuardState.Disarmed
        assertFalse(monitorIndicators(disarmed, snap(lux = 29f), config, full).lightJump)
        assertTrue(monitorIndicators(disarmed, snap(lux = 30f), config, full).lightJump)
    }

    @Test
    fun `cada indicador sigue a su sensor`() {
        val i = monitorIndicators(GuardState.Disarmed, snap(near = false, lux = 100f, motion = 3f), config, full)
        assertEquals(Indicators(proximityFar = true, lightJump = true, motion = true), i)
        assertEquals(Indicators.None, monitorIndicators(GuardState.Disarmed, snap(), config, full))
    }

    @Test
    fun `un sensor ausente nunca activa su indicador`() {
        val noLight = full.copy(hasLight = false)
        assertFalse(monitorIndicators(GuardState.Disarmed, snap(lux = 999f), config, noLight).lightJump)
        val noProximity = full.copy(proximityMaxRangeCm = null)
        assertFalse(monitorIndicators(GuardState.Disarmed, snap(near = false), config, noProximity).proximityFar)
    }

    // ---------------------------------------------------------------- fuente de datos

    private fun kotlinx.coroutines.test.TestScope.collect(vm: MonitorViewModel): Job =
        backgroundScope.launch(mainRule.dispatcher) { vm.state.collect { } }

    @Test
    fun `desarmado el monitor lee los sensores mientras la pantalla se ve`() = runTest {
        val vm = viewModel()
        assertEquals(0, sensors.activeListeners) // nada registrado hasta que la pantalla se observa

        collect(vm)
        assertEquals(3, sensors.activeListeners)

        sensors.prox.tryEmit(SensorReading.Proximity(0f, ms(1)))
        sensors.light.tryEmit(SensorReading.Light(7f, ms(2)))
        sensors.accel.tryEmit(SensorReading.Acceleration(3f, 0f, 0f, ms(3)))
        mainRule.dispatcher.scheduler.advanceTimeBy(60)

        val ui = vm.state.value
        assertEquals(MonitorSource.LIVE, ui.source)
        assertEquals(3f, ui.snapshot?.motion)
        assertEquals(7f, ui.snapshot?.rawLux)
        assertTrue(ui.indicators.motion)
    }

    @Test
    fun `al salir de la pantalla se sueltan los listeners`() = runTest {
        val vm = viewModel()
        val job = collect(vm)
        assertEquals(3, sensors.activeListeners)

        job.cancel()
        mainRule.dispatcher.scheduler.advanceTimeBy(10)

        assertEquals(0, sensors.activeListeners)
    }

    @Test
    fun `con la alarma armada muestra las lecturas del servicio y no registra listeners propios`() = runTest {
        repository.updateState(GuardState.Stored(5f, all))
        val vm = viewModel()
        collect(vm)
        assertEquals(0, sensors.activeListeners)

        repository.publishSnapshot(snap(near = false, motion = 4f))
        mainRule.dispatcher.scheduler.advanceTimeBy(60)

        val ui = vm.state.value
        assertEquals(MonitorSource.SERVICE, ui.source)
        assertEquals(4f, ui.snapshot?.motion)
        assertTrue(ui.indicators.proximityFar)
        assertTrue(ui.indicators.motion)
    }

    @Test
    fun `al armarse el monitor cambia de fuente y suelta sus listeners`() = runTest {
        val vm = viewModel()
        collect(vm)
        assertEquals(3, sensors.activeListeners)

        repository.updateState(GuardState.Arming(0, null, all))
        mainRule.dispatcher.scheduler.advanceTimeBy(10)

        assertEquals(MonitorSource.SERVICE, vm.state.value.source)
        assertEquals(0, sensors.activeListeners)
    }

    @Test
    fun `refleja el umbral de movimiento de la configuracion`() = runTest {
        settings.update(DetectionConfig.Default.copy(motionThreshold = 4f))
        val vm = viewModel()
        collect(vm)

        sensors.accel.tryEmit(SensorReading.Acceleration(3f, 0f, 0f, ms(1)))
        mainRule.dispatcher.scheduler.advanceTimeBy(60)

        assertEquals(4f, vm.state.value.config.motionThreshold)
        assertFalse(vm.state.value.indicators.motion) // 3 < 4
    }

    // ---------------------------------------------------------------- grabación de trazas

    @Test
    fun `grabar escribe las lecturas y al detener queda la traza para compartir`() = runTest {
        val vm = viewModel()
        collect(vm)
        assertFalse(vm.state.value.recording)

        vm.startRecording()
        assertTrue(vm.state.value.recording)
        sensors.accel.tryEmit(SensorReading.Acceleration(1f, 0f, 0f, ms(10)))
        sensors.accel.tryEmit(SensorReading.Acceleration(2f, 0f, 0f, ms(20)))
        mainRule.dispatcher.scheduler.advanceTimeBy(60)
        vm.stopRecording()

        val trace = vm.state.value.lastTrace
        assertNotNull(trace)
        assertFalse(vm.state.value.recording)
        val lines = trace!!.readLines()
        assertEquals(TraceRecorder.HEADER, lines.first())
        assertTrue("filas: ${lines.size}", lines.size >= 3) // cabecera + las dos lecturas
        assertTrue(lines.drop(1).all { it.endsWith(",Disarmed") })
    }

    @Test
    fun `empezar una grabacion nueva olvida la traza anterior`() = runTest {
        val vm = viewModel()
        collect(vm)
        vm.startRecording()
        vm.stopRecording()
        assertNotNull(vm.state.value.lastTrace)

        vm.startRecording()

        assertNull(vm.state.value.lastTrace)
        vm.stopRecording()
    }

    @Test
    fun `sin grabar no se escribe ningun archivo`() = runTest {
        val vm = viewModel()
        collect(vm)
        sensors.accel.tryEmit(SensorReading.Acceleration(1f, 0f, 0f, ms(10)))
        mainRule.dispatcher.scheduler.advanceTimeBy(60)
        vm.stopRecording()

        assertNull(vm.state.value.lastTrace)
        assertFalse(File(tmp.root, "traces").exists())
    }
}
