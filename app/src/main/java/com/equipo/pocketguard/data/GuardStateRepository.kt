package com.equipo.pocketguard.data

import com.equipo.pocketguard.decision.GuardState
import com.equipo.pocketguard.decision.MessageCode
import com.equipo.pocketguard.processing.SensorSnapshot
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Lo que la interfaz necesita saber: el estado de la máquina y la última lectura fusionada de los sensores. */
data class GuardUiState(
    val state: GuardState = GuardState.Disarmed,
    /** Última lectura mientras el servicio vigila; `null` si está desarmado. */
    val snapshot: SensorSnapshot? = null,
)

/**
 * Puente entre el servicio y la interfaz: el servicio escribe y la interfaz solo observa (flujo unidireccional).
 * Es un singleton en memoria; el servicio y las pantallas viven en el mismo proceso.
 */
@Singleton
class GuardStateRepository @Inject constructor() {

    private val _uiState = MutableStateFlow(GuardUiState())
    val uiState: StateFlow<GuardUiState> = _uiState

    /** Solo los cambios de estado, sin el ruido de cada lectura de sensor. */
    val state: Flow<GuardState> = _uiState.map { it.state }.distinctUntilChanged()

    private val _messages = MutableSharedFlow<MessageCode>(extraBufferCapacity = MESSAGE_BUFFER)

    /** Avisos puntuales para mostrar en pantalla (por ejemplo, tiempo de armado agotado). */
    val messages: SharedFlow<MessageCode> = _messages

    fun updateState(state: GuardState) {
        _uiState.update {
            // Al desarmar se descarta la última lectura para no mostrar datos viejos.
            if (state == GuardState.Disarmed) GuardUiState(state = state) else it.copy(state = state)
        }
    }

    fun publishSnapshot(snapshot: SensorSnapshot) {
        _uiState.update { it.copy(snapshot = snapshot) }
    }

    fun emitMessage(code: MessageCode) {
        _messages.tryEmit(code)
    }

    private companion object {
        const val MESSAGE_BUFFER = 8
    }
}
