package com.equipo.pocketguard.actuation

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SirenWaveformTest {

    private fun samples(waveform: SirenWaveform, count: Int) = ShortArray(count).also { waveform.fill(it) }

    private fun zeroCrossings(s: ShortArray, from: Int = 0, to: Int = s.size): Int {
        var n = 0
        for (i in from + 1 until to) if ((s[i - 1] < 0) != (s[i] < 0)) n++
        return n
    }

    @Test
    fun `la frecuencia barre de 600 a 1200 Hz y vuelve a bajar en cada ciclo`() {
        val w = SirenWaveform()
        assertEquals(600.0, w.frequencyAt(0.0), 1e-9)
        assertEquals(900.0, w.frequencyAt(0.25), 1e-9)
        assertEquals(1_200.0, w.frequencyAt(0.5), 1e-9)
        assertEquals(900.0, w.frequencyAt(0.75), 1e-9)
        assertEquals(600.0, w.frequencyAt(0.999999), 0.01)
    }

    @Test
    fun `la amplitud nunca supera el 90 por ciento del maximo`() {
        val s = samples(SirenWaveform(), 44_100)
        val limit = (0.9 * Short.MAX_VALUE).toInt() + 1
        assertTrue(s.all { abs(it.toInt()) <= limit })
        assertTrue("debe llegar cerca del pico", s.maxOf { abs(it.toInt()) } > limit * 0.95)
    }

    @Test
    fun `al inicio suena cerca de 600 Hz`() {
        val w = SirenWaveform()
        val s = samples(w, 44_100 / 20) // 50 ms
        // 600 Hz durante 50 ms son unos 60 cruces por cero (algo más porque la frecuencia ya empieza a subir).
        val crossings = zeroCrossings(s)
        assertTrue("cruces = $crossings", crossings in 58..70)
    }

    @Test
    fun `a la mitad del ciclo suena cerca de 1200 Hz`() {
        val w = SirenWaveform()
        samples(w, 44_100 * 475 / 1000) // avanza hasta el 47,5 % del ciclo
        val s = samples(w, 44_100 / 20) // 50 ms alrededor del pico
        val crossings = zeroCrossings(s)
        assertTrue("cruces = $crossings", crossings in 110..125)
    }

    @Test
    fun `es continua, no hay saltos entre muestras contiguas`() {
        val s = samples(SirenWaveform(), 44_100 * 2)
        // Paso máximo de fase a 1200 Hz: 2π·1200/44100 ≈ 0,171 rad → salto máximo ≈ 0,171 · amplitud.
        val maxStep = (0.171 * 0.9 * Short.MAX_VALUE).toInt() + 50
        for (i in 1 until s.size) {
            assertTrue("salto en $i: ${abs(s[i] - s[i - 1])}", abs(s[i] - s[i - 1]) <= maxStep)
        }
    }

    @Test
    fun `el tamano de los bloques no cambia el audio`() {
        val whole = samples(SirenWaveform(), 10_000)
        val chunked = SirenWaveform().let { w ->
            ShortArray(10_000).also { out ->
                var pos = 0
                for (size in listOf(1, 7, 882, 1_000, 3_333)) {
                    val part = ShortArray(size)
                    w.fill(part)
                    part.copyInto(out, pos)
                    pos += size
                }
                val rest = ShortArray(10_000 - pos)
                w.fill(rest)
                rest.copyInto(out, pos)
            }
        }
        assertTrue(whole.contentEquals(chunked))
    }

    @Test
    fun `fill con count parcial solo escribe esas muestras`() {
        val buffer = ShortArray(10) { 12_345 }
        SirenWaveform().fill(buffer, count = 4)
        assertTrue((4 until 10).all { buffer[it] == 12_345.toShort() })
    }

    @Test
    fun `el ciclo se repite cada segundo en frecuencia`() {
        val w = SirenWaveform()
        samples(w, 44_100) // un ciclo completo
        val s = samples(w, 44_100 / 20)
        val crossings = zeroCrossings(s)
        assertTrue("cruces = $crossings", crossings in 58..70)
    }
}
