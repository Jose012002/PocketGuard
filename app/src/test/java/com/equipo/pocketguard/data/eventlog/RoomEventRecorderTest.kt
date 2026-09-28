package com.equipo.pocketguard.data.eventlog

import com.equipo.pocketguard.decision.EventType
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoomEventRecorderTest {

    private val dao = FakeEventDao()
    private var now = 1_000L

    private fun kotlinx.coroutines.test.TestScope.recorder() =
        RoomEventRecorder(dao, backgroundScope, { now }, EmptyCoroutineContext)

    @Test
    fun `record guarda el tipo, el detalle y la hora del momento en que se registro`() = runTest {
        val r = recorder()
        now = 5_000
        r.record(EventType.ALARM, " latencyMs=1.5 ")
        now = 9_000 // la hora ya cambió cuando se ejecuta el insert
        runCurrent()

        val event = dao.all.single()
        assertEquals("ALARM", event.type)
        assertEquals("latencyMs=1.5", event.detail)
        assertEquals(5_000L, event.timestampMs)
    }

    @Test
    fun `los eventos se guardan en el orden en que se registran`() = runTest {
        val r = recorder()
        EventType.entries.forEach { r.record(it) }
        runCurrent()

        assertEquals(EventType.entries.map { it.name }, dao.all.map { it.type })
    }

    @Test
    fun `tras cada insercion se recorta el historial a 500 eventos`() = runTest {
        val r = recorder()
        r.record(EventType.ARMED)
        r.record(EventType.STORED)
        runCurrent()

        assertEquals(listOf(500, 500), dao.trimCalls)
        assertEquals(500, RoomEventRecorder.MAX_EVENTS)
    }

    @Test
    fun `un fallo al guardar no impide guardar los siguientes`() = runTest {
        val r = recorder()
        dao.failNextInsert = true
        r.record(EventType.ARMED)
        r.record(EventType.STORED)
        runCurrent()

        assertEquals(listOf("STORED"), dao.all.map { it.type })
    }

    @Test
    fun `record no bloquea a quien lo llama`() = runTest {
        val r = recorder()
        r.record(EventType.ARMED)
        assertTrue("el insert debe ocurrir después, en la cola", dao.all.isEmpty())
        runCurrent()
        assertEquals(1, dao.all.size)
    }
}
