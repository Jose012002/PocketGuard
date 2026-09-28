package com.equipo.pocketguard.processing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GravityFilterTest {

    private val g = 9.81f

    @Test
    fun `la primera muestra no produce movimiento aunque incluya la gravedad`() {
        assertEquals(0f, GravityFilter().update(0f, 0f, g), 1e-6f)
    }

    @Test
    fun `un telefono en reposo da movimiento cero`() {
        val f = GravityFilter()
        repeat(100) { assertEquals(0f, f.update(0f, 0f, g), 1e-4f) }
    }

    @Test
    fun `un golpe se detecta y luego decae al volver al reposo`() {
        val f = GravityFilter()
        repeat(50) { f.update(0f, 0f, g) }
        val golpe = f.update(0f, 0f, g + 10f)
        assertTrue("golpe = $golpe", golpe > 7f)
        var m = golpe
        repeat(60) { m = f.update(0f, 0f, g) }
        assertEquals(0f, m, 0.01f)
    }

    @Test
    fun `un cambio sostenido de orientacion deja de contar como movimiento`() {
        val f = GravityFilter()
        repeat(50) { f.update(0f, 0f, g) }
        val giro = f.update(g, 0f, 0f) // el teléfono se pone de lado
        assertTrue(giro > 5f)
        var m = giro
        repeat(80) { m = f.update(g, 0f, 0f) }
        assertEquals(0f, m, 0.01f)
    }

    @Test
    fun `usa la magnitud de los tres ejes`() {
        val h = GravityFilter(alpha = 1f) // la gravedad no cambia: linear = muestra - primera muestra
        h.update(0f, 0f, 0f)
        assertEquals(3f, h.update(1f, 2f, 2f), 1e-6f)
    }

    @Test
    fun `reset vuelve a inicializar la gravedad con la siguiente muestra`() {
        val f = GravityFilter()
        repeat(10) { f.update(0f, 0f, g) }
        f.reset()
        assertEquals(0f, f.update(g, 0f, 0f), 1e-6f)
    }

    @Test
    fun `el alpha por defecto es 0_8`() {
        assertEquals(0.8f, GravityFilter.DEFAULT_ALPHA, 0f)
    }
}
