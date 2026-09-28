package com.equipo.pocketguard.data.eventlog

import android.util.Log
import com.equipo.pocketguard.decision.EventType
import javax.inject.Inject
import javax.inject.Singleton

/** Registro de eventos del sistema (RF-30). La fase 10 añade la implementación con Room. */
interface EventRecorder {
    fun record(type: EventType, detail: String = "")
}

/** Implementación provisional: solo escribe en Logcat. */
@Singleton
class LogcatEventRecorder @Inject constructor() : EventRecorder {
    override fun record(type: EventType, detail: String) {
        Log.d("PocketGuard", "Evento $type ${detail.trim()}".trim())
    }
}
