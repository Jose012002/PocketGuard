package com.equipo.pocketguard.capture

import com.equipo.pocketguard.processing.SensorSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Calendar

class TraceRecorderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // 2026-09-27 10:30:45 en la zona horaria local, para que el nombre del archivo sea determinista.
    private val fixedTime = Calendar.getInstance().apply { set(2026, Calendar.SEPTEMBER, 27, 10, 30, 45) }.timeInMillis

    private fun recorder(dir: File = File(tmp.root, "traces")) = TraceRecorder(dir) { fixedTime }

    private fun snapshot(tNs: Long, distance: Float? = 0f, lux: Float? = 5f, raw: Float? = 4f, motion: Float = 0.5f) =
        SensorSnapshot(
            isNear = true,
            distanceCm = distance,
            lux = lux,
            isDark = true,
            motion = motion,
            timestampNs = tNs,
            rawLux = raw,
        )

    @Test
    fun `sin grabar no hay archivo y record no hace nada`() {
        val r = recorder()
        r.record(snapshot(1), "Stored")
        assertFalse(r.isRecording.value)
        assertNull(r.stop())
        assertTrue(r.listTraces().isEmpty())
    }

    @Test
    fun `start crea el archivo con nombre trace_fecha y la cabecera`() {
        val r = recorder()
        val file = r.start()
        assertTrue(r.isRecording.value)
        assertEquals("trace_20260927_103045.csv", file.name)
        assertEquals("traces", file.parentFile?.name)
        r.stop()
        assertEquals(listOf(TraceRecorder.HEADER), file.readLines())
        assertEquals("elapsed_ms,timestamp_ns,distance_cm,is_near,lux,is_dark,motion,state", TraceRecorder.HEADER)
    }

    @Test
    fun `cada snapshot es una fila con tiempo relativo al primero`() {
        val r = recorder()
        val file = r.start()
        r.record(snapshot(5_000_000_000, distance = 0f, raw = 4f, motion = 0.5f), "Arming")
        r.record(snapshot(5_250_000_000, distance = 5f, raw = 120f, motion = 3.25f), "Suspicion")
        r.stop()

        val lines = file.readLines()
        assertEquals(3, lines.size)
        assertEquals("0,5000000000,0.0,true,4.0,true,0.5,Arming", lines[1])
        assertEquals("250,5250000000,5.0,true,120.0,true,3.25,Suspicion", lines[2])
    }

    @Test
    fun `los sensores ausentes quedan como campos vacios`() {
        val r = recorder()
        val file = r.start()
        r.record(snapshot(1_000_000, distance = null, lux = null, raw = null), "Stored")
        r.stop()
        assertEquals("0,1000000,,true,,true,0.5,Stored", file.readLines()[1])
    }

    @Test
    fun `si no hay lux crudo se usa el suavizado`() {
        val r = recorder()
        val file = r.start()
        r.record(snapshot(1, lux = 7f, raw = null), "Stored")
        r.stop()
        assertEquals("7.0", file.readLines()[1].split(",")[4])
    }

    @Test
    fun `stop devuelve el archivo y termina la grabacion`() {
        val r = recorder()
        val file = r.start()
        assertEquals(file, r.stop())
        assertFalse(r.isRecording.value)
        r.record(snapshot(1), "Stored") // ya no escribe
        assertEquals(1, file.readLines().size)
    }

    @Test
    fun `start repetido durante una grabacion devuelve el mismo archivo`() {
        val r = recorder()
        val first = r.start()
        assertEquals(first, r.start())
        r.stop()
    }

    @Test
    fun `dos grabaciones en el mismo segundo no se pisan`() {
        val r = recorder()
        val first = r.start()
        r.stop()
        val second = r.start()
        r.stop()
        assertNotEquals(first, second)
        assertEquals("trace_20260927_103045_1.csv", second.name)
    }

    @Test
    fun `listTraces devuelve las trazas mas recientes primero`() {
        val r = recorder()
        r.start()
        r.stop()
        r.start()
        r.stop()
        val names = r.listTraces().map { it.name }
        assertEquals(listOf("trace_20260927_103045_1.csv", "trace_20260927_103045.csv"), names)
    }

    @Test
    fun `una nueva grabacion reinicia el tiempo relativo`() {
        val r = recorder()
        r.start()
        r.record(snapshot(9_000_000_000), "Stored")
        r.stop()
        val file = r.start()
        r.record(snapshot(20_000_000_000), "Stored")
        r.stop()
        assertEquals("0", file.readLines()[1].split(",")[0])
    }
}
