package com.equipo.pocketguard.processing

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalProcessorTest {

    private val config = ProcessingConfig(darkEnterLux = 10f, darkExitLux = 20f, luxSmoothingAlpha = 0.5f)

    private val fullHardware = SensorHardware(
        proximityMaxRangeCm = 5f,
        hasLightSensor = true,
        hasLinearAcceleration = true,
    )

    private fun processor(hardware: SensorHardware = fullHardware, cfg: ProcessingConfig = config) =
        SignalProcessor(cfg, hardware)

    private fun prox(cm: Float, t: Long = 0) = SensorReading.Proximity(cm, t)
    private fun light(lux: Float, t: Long = 0) = SensorReading.Light(lux, t)
    private fun acc(x: Float, y: Float, z: Float, t: Long = 0) = SensorReading.Acceleration(x, y, z, t)

    // ---------------------------------------------------------------- fusión

    @Test
    fun `emite un snapshot por cada lectura conservando el ultimo valor de los demas sensores`() {
        val p = processor()
        p.onReading(prox(0f, 1))
        p.onReading(light(3f, 2))
        val s = p.onReading(acc(3f, 4f, 0f, 3))
        assertTrue(s.isNear)
        assertEquals(0f, s.distanceCm!!, 0f)
        assertEquals(3f, s.lux!!, 0f)
        assertTrue(s.isDark)
        assertEquals(5f, s.motion, 1e-5f)

        val after = p.onReading(prox(5f, 4))
        assertFalse(after.isNear)
        assertEquals(3f, after.lux!!, 0f) // la luz se conserva
        assertEquals(5f, after.motion, 1e-5f) // el movimiento se conserva
    }

    @Test
    fun `el timestamp del snapshot es el de la lectura que lo origino`() {
        val p = processor()
        assertEquals(111L, p.onReading(prox(0f, 111)).timestampNs)
        assertEquals(222L, p.onReading(light(1f, 222)).timestampNs)
        assertEquals(333L, p.onReading(acc(0f, 0f, 0f, 333)).timestampNs)
    }

    @Test
    fun `el estado inicial con todos los sensores presentes es conservador`() {
        val s = processor().onReading(acc(0f, 0f, 0f))
        assertFalse(s.isNear) // aún no llegó proximidad: no se adelanta el bolsillo
        assertNull(s.distanceCm)
        assertNull(s.lux)
        assertFalse(s.isDark)
        assertEquals(0f, s.motion, 0f)
    }

    // ---------------------------------------------------------------- proximidad

    @Test
    fun `sensor de proximidad continuo cerca por debajo de 5 cm`() {
        val p = processor(fullHardware.copy(proximityMaxRangeCm = 10f))
        assertTrue(p.onReading(prox(4.9f)).isNear)
        assertFalse(p.onReading(prox(5f)).isNear)
        assertFalse(p.onReading(prox(10f)).isNear)
    }

    @Test
    fun `sensor binario con rango de 5 cm reporta cerca en 0 y lejos en 5`() {
        val p = processor(fullHardware.copy(proximityMaxRangeCm = 5f))
        assertTrue(p.onReading(prox(0f)).isNear)
        assertFalse(p.onReading(prox(5f)).isNear)
    }

    @Test
    fun `sensor binario con rango menor a 5 cm usa el rango como limite`() {
        val p = processor(fullHardware.copy(proximityMaxRangeCm = 3f))
        assertTrue(p.onReading(prox(0f)).isNear)
        assertFalse(p.onReading(prox(3f)).isNear) // 3 < min(3, 5) es falso
    }

    @Test
    fun `sensor binario con rango mayor a 5 cm usa 5 cm como limite`() {
        val p = processor(fullHardware.copy(proximityMaxRangeCm = 8f))
        assertTrue(p.onReading(prox(0f)).isNear)
        assertFalse(p.onReading(prox(8f)).isNear)
    }

    @Test
    fun `sin sensor de proximidad isNear es verdadero y se ignoran sus lecturas`() {
        val p = processor(fullHardware.copy(proximityMaxRangeCm = null))
        assertTrue(p.onReading(acc(0f, 0f, 0f)).isNear)
        val s = p.onReading(prox(100f))
        assertTrue(s.isNear)
        assertNull(s.distanceCm)
    }

    // ---------------------------------------------------------------- luz

    @Test
    fun `la luz se suaviza con EMA antes de decidir oscuro o claro`() {
        val p = processor()
        assertEquals(100f, p.onReading(light(100f)).lux!!, 1e-4f)
        assertEquals(50f, p.onReading(light(0f)).lux!!, 1e-4f) // alpha = 0.5
        assertEquals(25f, p.onReading(light(0f)).lux!!, 1e-4f)
    }

    @Test
    fun `el snapshot conserva el lux crudo junto al suavizado`() {
        val p = processor()
        p.onReading(light(100f))
        val s = p.onReading(light(0f))
        assertEquals(0f, s.rawLux!!, 0f)
        assertEquals(50f, s.lux!!, 1e-4f)
    }

    @Test
    fun `sin sensor de luz no hay lux crudo`() {
        val p = processor(fullHardware.copy(hasLightSensor = false))
        assertNull(p.onReading(light(5000f)).rawLux)
    }

    @Test
    fun `un pico aislado de luz no cambia el estado por el suavizado`() {
        val p = processor()
        repeat(5) { p.onReading(light(2f)) }
        val s = p.onReading(light(30f)) // suavizado: 16, no supera darkExit = 20
        assertTrue(s.isDark)
    }

    @Test
    fun `la histeresis usa los umbrales de la configuracion`() {
        val p = processor(cfg = config.copy(luxSmoothingAlpha = 1f))
        assertTrue(p.onReading(light(9f)).isDark)
        assertTrue(p.onReading(light(15f)).isDark)
        assertFalse(p.onReading(light(21f)).isDark)
        assertFalse(p.onReading(light(15f)).isDark)
        assertTrue(p.onReading(light(5f)).isDark)
    }

    @Test
    fun `sin sensor de luz lux es nulo e isDark es verdadero`() {
        val p = processor(fullHardware.copy(hasLightSensor = false))
        val s = p.onReading(light(5000f))
        assertNull(s.lux)
        assertTrue(s.isDark)
    }

    // ---------------------------------------------------------------- movimiento

    @Test
    fun `con aceleracion lineal el movimiento es la magnitud directa`() {
        val p = processor()
        assertEquals(7f, p.onReading(acc(2f, 3f, 6f)).motion, 1e-5f)
    }

    @Test
    fun `sin aceleracion lineal se filtra la gravedad`() {
        val p = processor(fullHardware.copy(hasLinearAcceleration = false))
        repeat(30) { p.onReading(acc(0f, 0f, 9.81f)) }
        assertEquals(0f, p.onReading(acc(0f, 0f, 9.81f)).motion, 1e-3f)
        assertTrue(p.onReading(acc(0f, 0f, 19.81f)).motion > 7f)
    }

    // ---------------------------------------------------------------- reset y flujo

    @Test
    fun `reset restaura el estado inicial`() {
        val p = processor()
        p.onReading(prox(0f))
        p.onReading(light(1f))
        p.onReading(acc(1f, 1f, 1f))
        p.reset()
        val s = p.onReading(acc(0f, 0f, 0f))
        assertFalse(s.isNear)
        assertNull(s.lux)
        assertNull(s.distanceCm)
        assertFalse(s.isDark)
    }

    @Test
    fun `process convierte lecturas en snapshots en orden`() = runTest {
        val out = processor().process(
            flowOf(prox(0f, 1), light(2f, 2), acc(0f, 0f, 3f, 3)),
        ).toList()
        assertEquals(listOf(1L, 2L, 3L), out.map { it.timestampNs })
        assertTrue(out.last().isNear)
        assertEquals(3f, out.last().motion, 1e-5f)
    }

    @Test
    fun `cada coleccion de process empieza con el estado limpio`() = runTest {
        val p = processor()
        val readings = flowOf(prox(0f, 1))
        assertTrue(p.process(readings).toList().single().isNear)
        // La segunda colección no debe heredar la proximidad anterior.
        val second = p.process(flowOf(acc(0f, 0f, 0f, 2))).toList().single()
        assertFalse(second.isNear)
    }
}
