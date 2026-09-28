package com.equipo.pocketguard.processing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HysteresisTest {

    // enter = 10, exit = 20 (valores por defecto de DetectionConfig)
    private fun detector() = Hysteresis(enterBelow = 10f, exitAbove = 20f)

    @Test
    fun `arranca en claro`() {
        assertFalse(detector().isActive)
    }

    @Test
    fun `entra a oscuro solo por debajo del umbral de entrada`() {
        val h = detector()
        assertFalse(h.update(10f)) // igual al umbral: no entra
        assertFalse(h.update(15f))
        assertTrue(h.update(9.9f))
    }

    @Test
    fun `sale de oscuro solo por encima del umbral de salida`() {
        val h = detector()
        h.update(0f)
        assertTrue(h.update(20f)) // igual al umbral: no sale
        assertTrue(h.update(15f))
        assertFalse(h.update(20.1f))
    }

    @Test
    fun `entre los dos umbrales conserva el estado anterior`() {
        val oscuro = detector().apply { update(0f) }
        val claro = detector().apply { update(100f) }
        for (lux in listOf(10f, 12f, 15f, 19f, 20f)) {
            assertTrue("oscuro con $lux", oscuro.update(lux))
            assertFalse("claro con $lux", claro.update(lux))
        }
    }

    @Test
    fun `no rebota con ruido alrededor de un umbral unico`() {
        val h = detector()
        h.update(5f)
        var cambios = 0
        var prev = h.isActive
        for (lux in listOf(11f, 9.5f, 12f, 10.5f, 11f, 9f, 13f)) {
            val now = h.update(lux)
            if (now != prev) cambios++
            prev = now
        }
        assertTrue(cambios == 0)
    }

    @Test
    fun `reset vuelve al estado inicial`() {
        val h = detector()
        h.update(0f)
        h.reset()
        assertFalse(h.isActive)
        val oscuroInicial = Hysteresis(10f, 20f, initial = true)
        oscuroInicial.update(100f)
        oscuroInicial.reset()
        assertTrue(oscuroInicial.isActive)
    }

    @Test
    fun `rechaza umbrales invertidos o iguales`() {
        assertThrows(IllegalArgumentException::class.java) { Hysteresis(20f, 10f) }
        assertThrows(IllegalArgumentException::class.java) { Hysteresis(10f, 10f) }
    }
}
