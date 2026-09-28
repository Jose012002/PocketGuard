package com.equipo.pocketguard.service

import com.equipo.pocketguard.capture.SensorCapabilities
import com.equipo.pocketguard.capture.SensorDataSource
import com.equipo.pocketguard.data.ArmedStateStore
import com.equipo.pocketguard.data.GuardStateRepository
import com.equipo.pocketguard.data.eventlog.EventRecorder
import com.equipo.pocketguard.decision.DetectionConfig
import com.equipo.pocketguard.decision.DetectionConfigProvider
import com.equipo.pocketguard.decision.Effect
import com.equipo.pocketguard.decision.EffectExecutor
import com.equipo.pocketguard.decision.EventType
import com.equipo.pocketguard.decision.GuardState
import com.equipo.pocketguard.decision.MessageCode
import com.equipo.pocketguard.decision.SamplingMode
import com.equipo.pocketguard.decision.VibrationPattern
import com.equipo.pocketguard.processing.SensorReading
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GuardPipelineTest {

    // ------------------------------------------------------------------ dobles

    private class FakeSensors : SensorDataSource {
        val prox = MutableSharedFlow<SensorReading.Proximity>(extraBufferCapacity = 64)
        val light = MutableSharedFlow<SensorReading.Light>(extraBufferCapacity = 64)
        val accel = MutableSharedFlow<SensorReading.Acceleration>(extraBufferCapacity = 64)
        val accelModes = mutableListOf<SamplingMode>()
        var failProximity = false

        override fun proximity(): Flow<SensorReading.Proximity> =
            if (failProximity) flow { throw IllegalStateException("no se pudo registrar") } else prox

        override fun light(): Flow<SensorReading.Light> = light

        override fun acceleration(mode: SamplingMode): Flow<SensorReading.Acceleration> =
            accel.onStart { accelModes += mode }
    }

    private class FakeExecutor : EffectExecutor {
        val effects = mutableListOf<Effect>()
        override fun execute(effects: List<Effect>, state: GuardState) {
            this.effects += effects
        }
    }

    private class FakeWakeLock : WakeLockController {
        var held = false
        var acquisitions = 0
        override fun acquire() {
            held = true
            acquisitions++
        }

        override fun release() {
            held = false
        }
    }

    private class FakeRecorder : EventRecorder {
        val events = mutableListOf<Pair<EventType, String>>()
        override fun record(type: EventType, detail: String) {
            events += type to detail
        }

        val types get() = events.map { it.first }
    }

    private class FakeArmedStore(var armed: Boolean = false) : ArmedStateStore {
        override suspend fun isArmed() = armed
        override suspend fun setArmed(armed: Boolean) {
            this.armed = armed
        }
    }

    private class Harness(
        scope: TestScope,
        caps: SensorCapabilities = FULL,
        config: DetectionConfig = DetectionConfig.Default,
        armed: Boolean = false,
    ) {
        val sensors = FakeSensors()
        val executor = FakeExecutor()
        val wakeLock = FakeWakeLock()
        val repository = GuardStateRepository()
        val recorder = FakeRecorder()
        val armedStore = FakeArmedStore(armed)
        var stopped = 0
        val messages = mutableListOf<MessageCode>()
        private val clock = { scope.testScheduler.currentTime * 1_000_000L }

        val pipeline = GuardPipeline(
            scope = scope.backgroundScope,
            sensors = sensors,
            capabilities = { caps },
            configProvider = DetectionConfigProvider { config },
            executor = executor,
            wakeLock = wakeLock,
            repository = repository,
            recorder = recorder,
            armedStore = armedStore,
            onStopped = { stopped++ },
            nowNs = clock,
        ).also { it.start() }

        init {
            scope.backgroundScope.launch { repository.messages.collect { messages += it } }
        }

        val state get() = repository.uiState.value.state

        fun near(near: Boolean) {
            sensors.prox.tryEmit(SensorReading.Proximity(if (near) 0f else 5f, clock()))
        }

        fun lux(lux: Float) {
            sensors.light.tryEmit(SensorReading.Light(lux, clock()))
        }

        fun motion(magnitude: Float) {
            sensors.accel.tryEmit(SensorReading.Acceleration(magnitude, 0f, 0f, clock()))
        }

        val effectList get() = executor.effects.toList()
    }

    companion object {
        val FULL = SensorCapabilities(
            proximityMaxRangeCm = 5f,
            hasLight = true,
            hasAccelerometer = true,
            hasLinearAcceleration = true,
            hasFlash = true,
        )
    }

    // ------------------------------------------------------------------ utilidades de tiempo

    /** Avanza el tiempo virtual en pasos de 50 ms, enviando una lectura de acelerómetro en reposo en cada uno. */
    private suspend fun TestScope.rest(h: Harness, ms: Long) {
        repeat((ms / 50).toInt()) {
            advanceTimeBy(50)
            runCurrent()
            h.motion(0f)
            runCurrent()
        }
    }

    /** Arma y guarda el teléfono en el bolsillo hasta llegar a `Stored`. */
    private suspend fun TestScope.armAndStore(h: Harness) {
        h.pipeline.arm()
        runCurrent()
        h.near(true)
        h.lux(5f)
        runCurrent()
        rest(h, 3_500)
        assertTrue("debería estar Stored y está ${h.state}", h.state is GuardState.Stored)
    }

    /** Los tres indicadores a la vez: teléfono lejos, con luz y en movimiento. */
    private fun TestScope.extract(h: Harness) {
        h.near(false)
        h.lux(200f)
        h.motion(5f)
    }

    // ------------------------------------------------------------------ armado

    @Test
    fun `armar inicia el monitoreo, toma el wake lock y actualiza la notificacion`() = runTest {
        val h = Harness(this)

        h.pipeline.arm()
        runCurrent()

        assertTrue(h.state is GuardState.Arming)
        assertTrue(h.wakeLock.held)
        assertEquals(1, h.sensors.prox.subscriptionCount.value)
        assertEquals(1, h.sensors.light.subscriptionCount.value)
        assertEquals(listOf(SamplingMode.NORMAL), h.sensors.accelModes)
        assertTrue(Effect.UpdateStatusNotification in h.effectList)
        assertEquals(listOf(EventType.ARMED), h.recorder.types)
        assertTrue(h.armedStore.armed)
        assertEquals(0, h.stopped)
    }

    @Test
    fun `con el bolsillo estable pasa a Stored y confirma con una vibracion`() = runTest {
        val h = Harness(this)
        armAndStore(h)

        assertTrue(Effect.Vibrate(VibrationPattern.CONFIRM) in h.effectList)
        assertTrue(EventType.STORED in h.recorder.types)
    }

    @Test
    fun `sin bolsillo dentro de armingTimeoutMs se desarma y avisa`() = runTest {
        val h = Harness(this)
        h.pipeline.arm()
        runCurrent()

        rest(h, DetectionConfig.Default.armingTimeoutMs + 500)

        assertEquals(GuardState.Disarmed, h.state)
        assertEquals(listOf(MessageCode.ARMING_TIMEOUT), h.messages)
        assertFalse(h.wakeLock.held)
        assertTrue(EventType.TIMEOUT in h.recorder.types)
        assertFalse(h.armedStore.armed)
        assertTrue(h.stopped >= 1)
        assertEquals(0, h.sensors.prox.subscriptionCount.value) // RNF-05: sin listeners al desarmar
    }

    @Test
    fun `con sensores insuficientes no arma, avisa y cierra el servicio`() = runTest {
        val noSensors = FULL.copy(proximityMaxRangeCm = null, hasLight = false)
        val h = Harness(this, caps = noSensors)
        runCurrent() // deja que el colector de mensajes se suscriba antes del aviso

        h.pipeline.arm()
        runCurrent()

        assertEquals(GuardState.Disarmed, h.state)
        assertEquals(listOf(MessageCode.INSUFFICIENT_SENSORS), h.messages)
        assertEquals(0, h.wakeLock.acquisitions)
        assertEquals(0, h.sensors.prox.subscriptionCount.value)
        assertEquals(1, h.stopped)
        assertFalse(h.armedStore.armed)
    }

    // ------------------------------------------------------------------ detección

    @Test
    fun `E1 la extraccion lleva a pre-alarma y luego a alarma con sirena, flash y vibracion`() = runTest {
        val h = Harness(this)
        armAndStore(h)

        extract(h)
        runCurrent()
        assertTrue("estado ${h.state}", h.state is GuardState.PreAlarm)
        assertTrue("el acelerómetro debe subir a muestreo alto", SamplingMode.HIGH in h.sensors.accelModes)
        assertTrue(Effect.ShowPreAlarmUi in h.effectList)
        assertFalse(Effect.StartSiren in h.effectList)

        advanceTimeBy(DetectionConfig.Default.preAlarmGraceMs + 300)
        runCurrent()
        assertTrue("estado ${h.state}", h.state is GuardState.Alarm)
        assertTrue(Effect.StartSiren in h.effectList)
        assertTrue(Effect.StartStrobe in h.effectList)
        assertTrue(Effect.Vibrate(VibrationPattern.ALARM) in h.effectList)
        assertTrue(Effect.ShowAlarmUi in h.effectList)
        assertTrue(EventType.ALARM in h.recorder.types)
    }

    @Test
    fun `con gracia cero la alarma suena de inmediato y se registra la latencia`() = runTest {
        val h = Harness(this, config = DetectionConfig.Default.copy(preAlarmGraceMs = 0))
        armAndStore(h)

        extract(h)
        runCurrent()

        assertTrue("estado ${h.state}", h.state is GuardState.Alarm)
        val alarmEvent = h.recorder.events.single { it.first == EventType.ALARM }
        assertTrue("detalle: ${alarmEvent.second}", alarmEvent.second.contains("latencyMs="))
    }

    @Test
    fun `E6 desarmar durante la pre-alarma no hace sonar la sirena`() = runTest {
        val h = Harness(this)
        armAndStore(h)
        extract(h)
        runCurrent()
        assertTrue(h.state is GuardState.PreAlarm)

        h.pipeline.disarm(authOk = true)
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(GuardState.Disarmed, h.state)
        assertFalse(Effect.StartSiren in h.effectList)
        assertTrue(Effect.StopAll in h.effectList)
        assertTrue(Effect.ClearAlarmUi in h.effectList)
    }

    @Test
    fun `desarmar la alarma libera sensores y wake lock y cierra el servicio`() = runTest {
        val h = Harness(this)
        armAndStore(h)
        extract(h)
        runCurrent()
        advanceTimeBy(DetectionConfig.Default.preAlarmGraceMs + 300)
        runCurrent()
        assertTrue(h.state is GuardState.Alarm)

        h.pipeline.disarm(authOk = true)
        runCurrent()

        assertEquals(GuardState.Disarmed, h.state)
        assertFalse(h.wakeLock.held)
        assertFalse(h.armedStore.armed)
        assertTrue(h.stopped >= 1)
        assertEquals(0, h.sensors.prox.subscriptionCount.value)
        assertEquals(0, h.sensors.light.subscriptionCount.value)
        assertEquals(0, h.sensors.accel.subscriptionCount.value)
    }

    @Test
    fun `un desarme con autenticacion fallida no detiene la alarma`() = runTest {
        val h = Harness(this, config = DetectionConfig.Default.copy(preAlarmGraceMs = 0))
        armAndStore(h)
        extract(h)
        runCurrent()
        assertTrue(h.state is GuardState.Alarm)

        h.pipeline.disarm(authOk = false)
        runCurrent()

        assertTrue(h.state is GuardState.Alarm)
        assertTrue(EventType.AUTH_FAILED in h.recorder.types)
        assertFalse(Effect.StopAll in h.effectList)
        assertEquals(0, h.stopped)
    }

    @Test
    fun `una sospecha que no se completa vuelve a Stored y baja el muestreo`() = runTest {
        val h = Harness(this)
        armAndStore(h)

        h.near(false) // se despega un instante...
        runCurrent()
        assertTrue(h.state is GuardState.Suspicion)
        h.near(true) // ...y vuelve al bolsillo
        runCurrent()
        rest(h, DetectionConfig.Default.coincidenceWindowMs + 300)

        assertTrue("estado ${h.state}", h.state is GuardState.Stored)
        assertEquals(SamplingMode.NORMAL, h.sensors.accelModes.last())
        assertTrue(SamplingMode.HIGH in h.sensors.accelModes)
        assertFalse(Effect.StartSiren in h.effectList)
    }

    @Test
    fun `caminar con el telefono guardado durante 30 s no genera alarma`() = runTest {
        val h = Harness(this)
        armAndStore(h)

        repeat(600) {
            advanceTimeBy(50)
            runCurrent()
            h.motion(if (it % 4 < 2) 6f else 0f) // pasos: movimiento fuerte y reposo
            runCurrent()
        }

        assertTrue("estado ${h.state}", h.state is GuardState.Stored || h.state is GuardState.Suspicion)
        assertFalse(Effect.StartSiren in h.effectList)
    }

    // ------------------------------------------------------------------ robustez y ciclo de vida

    @Test
    fun `si falla la captura de sensores se desarma en vez de aparentar que vigila`() = runTest {
        val h = Harness(this)
        h.sensors.failProximity = true

        h.pipeline.arm()
        runCurrent()

        assertEquals(GuardState.Disarmed, h.state)
        assertFalse(h.wakeLock.held)
        assertTrue(h.stopped >= 1)
    }

    @Test
    fun `desarmar un servicio que no estaba armado lo cierra`() = runTest {
        val h = Harness(this)

        h.pipeline.disarm(authOk = true)
        runCurrent()

        assertEquals(GuardState.Disarmed, h.state)
        assertEquals(1, h.stopped)
        assertEquals(0, h.wakeLock.acquisitions)
    }

    @Test
    fun `tras un reinicio del sistema se rearma si estaba armado`() = runTest {
        val h = Harness(this, armed = true)

        h.pipeline.restoreOrStop()
        runCurrent()

        assertTrue(h.state is GuardState.Arming)
        assertTrue(h.wakeLock.held)
        assertEquals(0, h.stopped)
    }

    @Test
    fun `tras un reinicio del sistema se cierra si no estaba armado`() = runTest {
        val h = Harness(this, armed = false)

        h.pipeline.restoreOrStop()
        runCurrent()

        assertEquals(GuardState.Disarmed, h.state)
        assertEquals(1, h.stopped)
    }

    @Test
    fun `shutdown suelta el wake lock y los sensores`() = runTest {
        val h = Harness(this)
        h.pipeline.arm()
        runCurrent()
        assertTrue(h.wakeLock.held)

        h.pipeline.shutdown()
        runCurrent()

        assertFalse(h.wakeLock.held)
        assertEquals(0, h.sensors.prox.subscriptionCount.value)
    }

    @Test
    fun `armar de nuevo tras desarmar funciona`() = runTest {
        val h = Harness(this)
        h.pipeline.arm()
        runCurrent()
        h.pipeline.disarm(authOk = true)
        runCurrent()
        assertEquals(GuardState.Disarmed, h.state)

        h.pipeline.arm()
        runCurrent()

        assertTrue(h.state is GuardState.Arming)
        assertTrue(h.wakeLock.held)
        assertEquals(1, h.sensors.prox.subscriptionCount.value)
    }
}
