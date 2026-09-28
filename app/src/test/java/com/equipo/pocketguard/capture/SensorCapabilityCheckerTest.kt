package com.equipo.pocketguard.capture

import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorCapabilityCheckerTest {

    private val sensorManager = mockk<SensorManager>()
    private val packageManager = mockk<PackageManager>()
    private val checker = SensorCapabilityChecker(sensorManager, packageManager)

    private fun sensor(range: Float = 0f): Sensor = mockk { every { maximumRange } returns range }

    init {
        // Por defecto el dispositivo no tiene nada.
        every { sensorManager.getDefaultSensor(any<Int>()) } returns null
        every { sensorManager.getDefaultSensor(any<Int>(), any<Boolean>()) } returns null
        every { packageManager.hasSystemFeature(any()) } returns false
    }

    @Test
    fun `un dispositivo sin sensores no puede armar`() {
        val c = checker.check()
        assertNull(c.proximityMaxRangeCm)
        assertFalse(c.hasLight)
        assertFalse(c.hasAccelerometer)
        assertFalse(c.hasLinearAcceleration)
        assertFalse(c.hasFlash)
        assertEquals(SensorSupport.UNSUPPORTED, c.support)
    }

    @Test
    fun `detecta un dispositivo completo`() {
        every { sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY, true) } returns sensor(5f)
        every { sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT) } returns sensor()
        every { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) } returns sensor()
        every { sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) } returns sensor()
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH) } returns true

        val c = checker.check()
        assertEquals(5f, c.proximityMaxRangeCm)
        assertTrue(c.hasLight)
        assertTrue(c.hasAccelerometer)
        assertTrue(c.hasLinearAcceleration)
        assertTrue(c.hasFlash)
        assertEquals(SensorSupport.FULL, c.support)
    }

    @Test
    fun `prefiere la proximidad wake-up y no consulta la normal si existe`() {
        every { sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY, true) } returns sensor(8f)

        assertEquals(8f, checker.check().proximityMaxRangeCm)
        verify(exactly = 0) { sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY) }
    }

    @Test
    fun `si no hay proximidad wake-up usa la normal`() {
        every { sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY) } returns sensor(3f)

        assertEquals(3f, checker.check().proximityMaxRangeCm)
    }

    @Test
    fun `sin aceleracion lineal se detecta solo el acelerometro`() {
        every { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) } returns sensor()
        every { sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT) } returns sensor()

        val c = checker.check()
        assertTrue(c.hasAccelerometer)
        assertFalse(c.hasLinearAcceleration)
        assertEquals(SensorSupport.DEGRADED, c.support)
        assertEquals(setOf(SensorKind.PROXIMITY), c.missing)
    }

    @Test
    fun `detecta la falta de flash`() {
        every { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) } returns sensor()
        every { sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT) } returns sensor()

        assertFalse(checker.check().hasFlash)
    }
}
