package com.equipo.pocketguard.capture

import com.equipo.pocketguard.decision.AvailableSensors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorCapabilitiesTest {

    private val full = SensorCapabilities(
        proximityMaxRangeCm = 5f,
        hasLight = true,
        hasAccelerometer = true,
        hasLinearAcceleration = true,
        hasFlash = true,
    )

    @Test
    fun `con los tres sensores el soporte es completo`() {
        assertEquals(SensorSupport.FULL, full.support)
        assertTrue(full.missing.isEmpty())
        assertTrue(full.canArm)
    }

    @Test
    fun `sin luz o sin proximidad funciona en modo degradado`() {
        val noLight = full.copy(hasLight = false)
        assertEquals(SensorSupport.DEGRADED, noLight.support)
        assertEquals(setOf(SensorKind.LIGHT), noLight.missing)
        assertTrue(noLight.canArm)

        val noProximity = full.copy(proximityMaxRangeCm = null)
        assertEquals(SensorSupport.DEGRADED, noProximity.support)
        assertEquals(setOf(SensorKind.PROXIMITY), noProximity.missing)
        assertTrue(noProximity.canArm)
    }

    @Test
    fun `sin proximidad ni luz no se puede armar`() {
        val c = full.copy(proximityMaxRangeCm = null, hasLight = false)
        assertEquals(SensorSupport.UNSUPPORTED, c.support)
        assertEquals(setOf(SensorKind.PROXIMITY, SensorKind.LIGHT), c.missing)
        assertFalse(c.canArm)
    }

    @Test
    fun `sin acelerometro no se puede armar aunque haya proximidad y luz`() {
        val c = full.copy(hasAccelerometer = false, hasLinearAcceleration = false)
        assertEquals(SensorSupport.UNSUPPORTED, c.support)
        assertEquals(setOf(SensorKind.ACCELEROMETER), c.missing)
        assertFalse(c.canArm)
    }

    @Test
    fun `la aceleracion lineal por si sola cuenta como fuente de movimiento`() {
        val c = full.copy(hasAccelerometer = false)
        assertTrue(c.hasMotionSensor)
        assertEquals(SensorSupport.FULL, c.support)
    }

    @Test
    fun `el acelerometro por si solo cuenta como fuente de movimiento`() {
        val c = full.copy(hasLinearAcceleration = false)
        assertTrue(c.hasMotionSensor)
        assertEquals(SensorSupport.FULL, c.support)
    }

    @Test
    fun `la falta de flash no degrada la deteccion`() {
        val c = full.copy(hasFlash = false)
        assertEquals(SensorSupport.FULL, c.support)
        assertTrue(c.canArm)
    }

    @Test
    fun `toAvailableSensors traduce las capacidades para la capa de decision`() {
        assertEquals(AvailableSensors(proximity = true, light = true, accelerometer = true), full.toAvailableSensors())
        assertEquals(
            AvailableSensors(proximity = false, light = true, accelerometer = true),
            full.copy(proximityMaxRangeCm = null).toAvailableSensors(),
        )
        assertEquals(
            AvailableSensors(proximity = true, light = true, accelerometer = false),
            full.copy(hasAccelerometer = false, hasLinearAcceleration = false).toAvailableSensors(),
        )
    }

    @Test
    fun `toHardware traduce las capacidades para la capa de procesamiento`() {
        val hw = full.copy(proximityMaxRangeCm = 8f, hasLinearAcceleration = false).toHardware()
        assertEquals(8f, hw.proximityMaxRangeCm)
        assertTrue(hw.hasLightSensor)
        assertFalse(hw.hasLinearAcceleration)

        val bare = full.copy(proximityMaxRangeCm = null, hasLight = false).toHardware()
        assertNull(bare.proximityMaxRangeCm)
        assertFalse(bare.hasLightSensor)
        assertTrue(bare.hasLinearAcceleration)
    }
}
