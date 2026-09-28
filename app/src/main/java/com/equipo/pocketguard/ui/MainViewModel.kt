package com.equipo.pocketguard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.equipo.pocketguard.data.PinRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Decide con qué pantalla arrancar: onboarding si todavía no hay PIN. */
@HiltViewModel
class MainViewModel @Inject constructor(
    pinRepository: PinRepository,
) : ViewModel() {

    /** `null` mientras se lee el almacenamiento. */
    val hasPin: StateFlow<Boolean?> = pinRepository.hasPin
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
