package com.equipo.pocketguard.ui.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.equipo.pocketguard.data.GuardStateRepository
import com.equipo.pocketguard.data.SettingsRepository
import com.equipo.pocketguard.decision.GuardState
import com.equipo.pocketguard.service.GuardController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Datos de la pantalla de alarma: el estado de la máquina y la duración del periodo de gracia. */
@HiltViewModel
class AlarmViewModel @Inject constructor(
    repository: GuardStateRepository,
    settings: SettingsRepository,
    private val guardController: GuardController,
) : ViewModel() {

    val state: StateFlow<GuardState> = repository.state
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.uiState.value.state)

    /** Duración de la gracia con la que se armó (los ajustes están bloqueados mientras está armado). */
    val graceMs: Long = settings.current().preAlarmGraceMs

    /** RF-32: solo en compilaciones debug; desarma sin PIN para facilitar pruebas y demostraciones. */
    fun debugStop() = guardController.disarm(authOk = true)
}
