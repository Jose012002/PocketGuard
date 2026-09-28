package com.equipo.pocketguard.data.eventlog

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Un evento del historial (RF-30). [type] es el nombre de un `EventType`. */
@Entity(tableName = "events", indices = [Index("timestampMs")])
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Hora de pared (`System.currentTimeMillis`), para mostrar fecha y hora. */
    val timestampMs: Long,
    val type: String,
    val detail: String = "",
)
