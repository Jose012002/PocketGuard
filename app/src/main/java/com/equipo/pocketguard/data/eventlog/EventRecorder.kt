package com.equipo.pocketguard.data.eventlog

import android.util.Log
import com.equipo.pocketguard.decision.EventType
import com.equipo.pocketguard.di.ActuationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** Registro de eventos del sistema (RF-30). */
interface EventRecorder {
    fun record(type: EventType, detail: String = "")
}

/**
 * Guarda los eventos en Room y los escribe también en Logcat. Los inserta en orden mediante una cola, sin
 * bloquear a quien registra (el pipeline de detección). Conserva solo los [MAX_EVENTS] más recientes.
 */
@Singleton
class RoomEventRecorder(
    private val dao: EventDao,
    scope: CoroutineScope,
    private val clockMs: () -> Long,
    ioContext: CoroutineContext,
) : EventRecorder {

    @Inject
    constructor(
        dao: EventDao,
        @ActuationScope scope: CoroutineScope,
    ) : this(dao, scope, System::currentTimeMillis, Dispatchers.IO)

    private val queue = Channel<EventEntity>(Channel.UNLIMITED)

    init {
        scope.launch(ioContext) {
            for (event in queue) {
                try {
                    dao.insert(event)
                    dao.trimTo(MAX_EVENTS)
                } catch (e: Exception) {
                    // El historial es secundario: un fallo de disco no debe afectar a la alarma.
                    Log.w(TAG, "No se pudo guardar el evento ${event.type}", e)
                }
            }
        }
    }

    override fun record(type: EventType, detail: String) {
        Log.d(TAG, "Evento $type ${detail.trim()}".trim())
        queue.trySend(EventEntity(timestampMs = clockMs(), type = type.name, detail = detail.trim()))
    }

    companion object {
        const val MAX_EVENTS = 500
        private const val TAG = "PocketGuard"
    }
}
