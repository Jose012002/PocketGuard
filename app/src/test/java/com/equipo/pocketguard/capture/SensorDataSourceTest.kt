package com.equipo.pocketguard.capture

import com.equipo.pocketguard.decision.SamplingMode
import com.equipo.pocketguard.processing.SensorReading
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SensorDataSourceTest {

    /** Origen falso que registra cuántas veces y con qué modo se suscribe cada sensor. */
    private class FakeSource : SensorDataSource {
        val proximityFlow = MutableSharedFlow<SensorReading.Proximity>()
        val lightFlow = MutableSharedFlow<SensorReading.Light>()
        val accelerationFlow = MutableSharedFlow<SensorReading.Acceleration>()

        val accelerationSubscriptions = mutableListOf<SamplingMode>()
        var activeAccelerationSubscriptions = 0
        var proximityStarts = 0
        var lightStarts = 0

        override fun proximity(): Flow<SensorReading.Proximity> = proximityFlow.onStart { proximityStarts++ }

        override fun light(): Flow<SensorReading.Light> = lightFlow.onStart { lightStarts++ }

        override fun acceleration(mode: SamplingMode): Flow<SensorReading.Acceleration> =
            accelerationFlow
                .onStart {
                    accelerationSubscriptions += mode
                    activeAccelerationSubscriptions++
                }
                .onCompletion { activeAccelerationSubscriptions-- }
    }

    @Test
    fun `readings fusiona proximidad, luz y aceleracion`() = runTest(UnconfinedTestDispatcher()) {
        val source = FakeSource()
        val received = mutableListOf<SensorReading>()
        backgroundScope.launch { source.readings(MutableStateFlow(SamplingMode.NORMAL)).collect { received += it } }

        val p = SensorReading.Proximity(0f, 1)
        val l = SensorReading.Light(3f, 2)
        val a = SensorReading.Acceleration(0f, 0f, 1f, 3)
        source.proximityFlow.emit(p)
        source.lightFlow.emit(l)
        source.accelerationFlow.emit(a)

        assertEquals(listOf(p, l, a), received)
    }

    @Test
    fun `al cambiar el modo solo se vuelve a registrar el acelerometro`() = runTest(UnconfinedTestDispatcher()) {
        val source = FakeSource()
        val mode = MutableStateFlow(SamplingMode.NORMAL)
        backgroundScope.launch { source.readings(mode).collect { } }

        assertEquals(listOf(SamplingMode.NORMAL), source.accelerationSubscriptions)

        mode.value = SamplingMode.HIGH
        assertEquals(listOf(SamplingMode.NORMAL, SamplingMode.HIGH), source.accelerationSubscriptions)
        assertEquals("el listener anterior debe liberarse", 1, source.activeAccelerationSubscriptions)

        mode.value = SamplingMode.NORMAL
        assertEquals(
            listOf(SamplingMode.NORMAL, SamplingMode.HIGH, SamplingMode.NORMAL),
            source.accelerationSubscriptions,
        )
        assertEquals(1, source.activeAccelerationSubscriptions)

        // Proximidad y luz nunca se reiniciaron.
        assertEquals(1, source.proximityStarts)
        assertEquals(1, source.lightStarts)
    }

    @Test
    fun `repetir el mismo modo no vuelve a registrar el acelerometro`() = runTest(UnconfinedTestDispatcher()) {
        val source = FakeSource()
        // Un SharedFlow sí deja pasar valores repetidos (un StateFlow los descartaría por sí solo).
        val repeated = MutableSharedFlow<SamplingMode>(replay = 1)
        repeated.emit(SamplingMode.NORMAL)
        backgroundScope.launch { source.readings(repeated).collect { } }

        repeated.emit(SamplingMode.NORMAL)
        repeated.emit(SamplingMode.NORMAL)

        assertEquals(listOf(SamplingMode.NORMAL), source.accelerationSubscriptions)
    }

    @Test
    fun `tras el cambio de modo las lecturas siguen llegando`() = runTest(UnconfinedTestDispatcher()) {
        val source = FakeSource()
        val mode = MutableStateFlow(SamplingMode.NORMAL)
        val received = mutableListOf<SensorReading>()
        backgroundScope.launch { source.readings(mode).collect { received += it } }

        mode.value = SamplingMode.HIGH
        val a = SensorReading.Acceleration(1f, 2f, 3f, 10)
        source.accelerationFlow.emit(a)
        assertEquals(listOf<SensorReading>(a), received)
    }

    @Test
    fun `al cancelar la colecta se libera todo`() = runTest(UnconfinedTestDispatcher()) {
        val source = FakeSource()
        val job = launch { source.readings(MutableStateFlow(SamplingMode.NORMAL)).collect { } }
        assertEquals(1, source.activeAccelerationSubscriptions)
        assertEquals(1, source.proximityFlow.subscriptionCount.value)
        assertEquals(1, source.lightFlow.subscriptionCount.value)

        job.cancel()
        assertEquals(0, source.activeAccelerationSubscriptions)
        assertEquals(0, source.proximityFlow.subscriptionCount.value)
        assertEquals(0, source.lightFlow.subscriptionCount.value)
    }
}
