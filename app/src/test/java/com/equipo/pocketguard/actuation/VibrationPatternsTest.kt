package com.equipo.pocketguard.actuation

import com.equipo.pocketguard.decision.VibrationPattern
import org.junit.Assert.assertEquals
import org.junit.Test

class VibrationPatternsTest {

    @Test
    fun `CONFIRM es un pulso de 150 ms sin repetir`() {
        val spec = VibrationPatterns.specFor(VibrationPattern.CONFIRM)
        assertEquals(listOf(0L, 150L), spec.timings)
        assertEquals(-1, spec.repeatIndex)
    }

    @Test
    fun `SOFT es 200 ms encendido y 800 ms apagado repetido`() {
        val spec = VibrationPatterns.specFor(VibrationPattern.SOFT)
        assertEquals(listOf(0L, 200L, 800L), spec.timings)
        assertEquals(0, spec.repeatIndex)
    }

    @Test
    fun `ALARM es 800 ms encendido y 200 ms apagado repetido`() {
        val spec = VibrationPatterns.specFor(VibrationPattern.ALARM)
        assertEquals(listOf(0L, 800L, 200L), spec.timings)
        assertEquals(0, spec.repeatIndex)
    }

    @Test
    fun `todos los patrones tienen una definicion`() {
        for (p in VibrationPattern.entries) VibrationPatterns.specFor(p)
    }
}
