package com.equipo.pocketguard.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.equipo.pocketguard.capture.SensorCapabilities
import com.equipo.pocketguard.capture.SensorCapabilityChecker
import com.equipo.pocketguard.data.GuardStateRepository
import com.equipo.pocketguard.decision.GuardState
import com.equipo.pocketguard.decision.MessageCode
import com.equipo.pocketguard.service.GuardController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val guardState: GuardState,
    val capabilities: SensorCapabilities,
) {
    val isArmed: Boolean get() = guardState != GuardState.Disarmed

    /** RF-08: el botón de armar solo funciona si se cumple el mínimo de sensores. */
    val canArm: Boolean get() = !isArmed && capabilities.canArm
}

/** CU-02 y CU-07: estado de la alarma, sensores disponibles y órdenes de armar/desarmar. */
@HiltViewModel
class HomeViewModel @Inject constructor(
    repository: GuardStateRepository,
    checker: SensorCapabilityChecker,
    private val guardController: GuardController,
) : ViewModel() {

    private val capabilities = checker.check()

    val state: StateFlow<HomeUiState> = repository.state
        .map { HomeUiState(it, capabilities) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            HomeUiState(repository.uiState.value.state, capabilities),
        )

    /** Avisos puntuales del servicio (por ejemplo, tiempo de armado agotado). */
    val messages: Flow<MessageCode> = repository.messages

    /** Un solo toque desde la pantalla principal (RNF-12). */
    fun arm() {
        if (state.value.canArm) guardController.arm()
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
