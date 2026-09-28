package com.equipo.pocketguard.ui.history

import com.equipo.pocketguard.data.eventlog.EventEntity
import com.equipo.pocketguard.data.eventlog.FakeEventDao
import com.equipo.pocketguard.decision.EventType
import com.equipo.pocketguard.ui.MainDispatcherRule
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HistoryViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val dao = FakeEventDao()

    private fun kotlinx.coroutines.test.TestScope.observe(vm: HistoryViewModel) {
        backgroundScope.launch(mainRule.dispatcher) { vm.items.collect { } }
    }

    @Test
    fun `mientras se lee no hay lista y luego llega vacia`() = runTest {
        val vm = HistoryViewModel(dao)
        observe(vm)
        assertEquals(emptyList<HistoryItem>(), vm.items.value)
    }

    @Test
    fun `los eventos salen del mas reciente al mas antiguo`() = runTest {
        dao.insert(EventEntity(timestampMs = 1_000, type = "ARMED"))
        dao.insert(EventEntity(timestampMs = 3_000, type = "ALARM", detail = "latencyMs=2.0"))
        dao.insert(EventEntity(timestampMs = 2_000, type = "STORED"))
        val vm = HistoryViewModel(dao)
        observe(vm)

        val items = vm.items.value!!
        assertEquals(listOf(EventType.ALARM, EventType.STORED, EventType.ARMED), items.map { it.type })
        assertEquals(listOf(3_000L, 2_000L, 1_000L), items.map { it.timestampMs })
        assertEquals("latencyMs=2.0", items.first().detail)
    }

    @Test
    fun `un tipo desconocido se conserva sin fallar`() = runTest {
        dao.insert(EventEntity(timestampMs = 1, type = "EVENTO_DE_UNA_VERSION_FUTURA"))
        val vm = HistoryViewModel(dao)
        observe(vm)

        assertNull(vm.items.value!!.single().type)
    }

    @Test
    fun `los eventos nuevos aparecen en vivo`() = runTest {
        val vm = HistoryViewModel(dao)
        observe(vm)

        dao.insert(EventEntity(timestampMs = 1, type = "ARMED"))

        assertEquals(1, vm.items.value!!.size)
    }

    @Test
    fun `borrar el historial lo vacia`() = runTest {
        dao.insert(EventEntity(timestampMs = 1, type = "ARMED"))
        dao.insert(EventEntity(timestampMs = 2, type = "STORED"))
        val vm = HistoryViewModel(dao)
        observe(vm)

        vm.clear()

        assertTrue(vm.items.value!!.isEmpty())
        assertTrue(dao.all.isEmpty())
    }

    @Test
    fun `cada tipo de evento tiene texto propio`() {
        val labels = EventType.entries.map { it.label() }
        assertEquals(EventType.entries.size, labels.toSet().size)
    }

    @Test
    fun `cada tipo de evento tiene icono`() {
        val icons = EventType.entries.map { it.icon().name }
        assertEquals(EventType.entries.size, icons.size)
        assertNotEquals(icons.first(), icons.last())
    }
}
