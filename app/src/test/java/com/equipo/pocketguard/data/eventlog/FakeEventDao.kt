package com.equipo.pocketguard.data.eventlog

import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** DAO en memoria con el mismo orden que la consulta real (más reciente primero). */
class FakeEventDao : EventDao {
    private val stored = MutableStateFlow<List<EventEntity>>(emptyList())
    private var nextId = 1L

    val trimCalls = mutableListOf<Int>()
    var failNextInsert = false

    val all: List<EventEntity> get() = stored.value

    override suspend fun insert(event: EventEntity) {
        if (failNextInsert) {
            failNextInsert = false
            throw IOException("disco lleno")
        }
        stored.value = stored.value + event.copy(id = nextId++)
    }

    override fun observeAll(): Flow<List<EventEntity>> = stored.map { list ->
        list.sortedWith(compareByDescending<EventEntity> { it.timestampMs }.thenByDescending { it.id })
    }

    override suspend fun clear() {
        stored.value = emptyList()
    }

    override suspend fun trimTo(keep: Int) {
        trimCalls += keep
        stored.value = stored.value.sortedByDescending { it.id }.take(keep).sortedBy { it.id }
    }
}
