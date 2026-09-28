package com.equipo.pocketguard.ui.history

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.equipo.pocketguard.R
import com.equipo.pocketguard.data.eventlog.EventDao
import com.equipo.pocketguard.data.eventlog.EventEntity
import com.equipo.pocketguard.decision.EventType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Un renglón del historial. [type] es `null` si el nombre guardado ya no corresponde a ningún tipo conocido. */
data class HistoryItem(
    val id: Long,
    val type: EventType?,
    val timestampMs: Long,
    val detail: String,
)

fun EventEntity.toItem() = HistoryItem(
    id = id,
    type = EventType.entries.firstOrNull { it.name == type },
    timestampMs = timestampMs,
    detail = detail,
)

/** Cómo se muestra cada tipo de evento: texto e ícono (RF-30). */
fun EventType.label(): Int = when (this) {
    EventType.ARMED -> R.string.event_armed
    EventType.STORED -> R.string.event_stored
    EventType.SUSPICION -> R.string.event_suspicion
    EventType.PRE_ALARM -> R.string.event_pre_alarm
    EventType.ALARM -> R.string.event_alarm
    EventType.DISARMED -> R.string.event_disarmed
    EventType.TIMEOUT -> R.string.event_timeout
    EventType.AUTH_FAILED -> R.string.event_auth_failed
}

fun EventType.icon(): ImageVector = when (this) {
    EventType.ARMED -> Icons.Filled.Lock
    EventType.STORED -> Icons.Filled.Shield
    EventType.SUSPICION -> Icons.Filled.Visibility
    EventType.PRE_ALARM -> Icons.Filled.NotificationsActive
    EventType.ALARM -> Icons.Filled.Warning
    EventType.DISARMED -> Icons.Filled.LockOpen
    EventType.TIMEOUT -> Icons.Filled.HourglassTop
    EventType.AUTH_FAILED -> Icons.Filled.Block
}

/** CU-10: eventos del más reciente al más antiguo, con opción de borrarlos. */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val dao: EventDao,
) : ViewModel() {

    val items: StateFlow<List<HistoryItem>?> = dao.observeAll()
        .map { list -> list.map(EventEntity::toItem) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun clear() {
        viewModelScope.launch { dao.clear() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
