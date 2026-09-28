package com.equipo.pocketguard.processing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ExponentialSmootherTest {

    @Test
    fun `la primera muestra pasa sin filtrar`() {
        val s = ExponentialSmoother(0.5f)
        assertNull(s.value)
        assertEquals(100f, s.update(100f), 0f)
        assertEquals(100f, s.value!!, 0f)
    }

    @Test
    fun `aplica y = alpha por x mas 1 menos alpha por y anterior`() {
        val s = ExponentialSmoother(0.5f)
        s.update(100f)
        assertEquals(50f, s.update(0f), 1e-4f)
        assertEquals(25f, s.update(0f), 1e-4f)
        assertEquals(62.5f, s.update(100f), 1e-4f)
    }

    @Test
    fun `alpha 1 no suaviza`() {
        val s = ExponentialSmoother(1f)
        s.update(10f)
        assertEquals(500f, s.update(500f), 0f)
    }

    @Test
    fun `alpha bajo reacciona mas despacio que alpha alto`() {
        val slow = ExponentialSmoother(0.1f).apply { update(0f) }
        val fast = ExponentialSmoother(0.9f).apply { update(0f) }
        assertEquals(10f, slow.update(100f), 1e-4f)
        assertEquals(90f, fast.update(100f), 1e-4f)
    }

    @Test
    fun `converge a una entrada constante`() {
        val s = ExponentialSmoother(0.3f)
        s.update(0f)
        var last = 0f
        repeat(60) { last = s.update(200f) }
        assertEquals(200f, last, 0.01f)
    }

    @Test
    fun `reset descarta el historial`() {
        val s = ExponentialSmoother(0.5f)
        s.update(100f)
        s.reset()
        assertNull(s.value)
        assertEquals(7f, s.update(7f), 0f)
    }

    @Test
    fun `rechaza alpha fuera de 0 a 1`() {
        assertThrows(IllegalArgumentException::class.java) { ExponentialSmoother(0f) }
        assertThrows(IllegalArgumentException::class.java) { ExponentialSmoother(-0.1f) }
        assertThrows(IllegalArgumentException::class.java) { ExponentialSmoother(1.01f) }
    }
}
