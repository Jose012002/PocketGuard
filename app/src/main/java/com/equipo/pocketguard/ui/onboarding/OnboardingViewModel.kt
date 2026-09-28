package com.equipo.pocketguard.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.equipo.pocketguard.data.PinRepository
import com.equipo.pocketguard.security.PinPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OnboardingStep { INTRO, CREATE_PIN, CONFIRM_PIN, PERMISSIONS }

enum class OnboardingError { PIN_TOO_SHORT, PIN_MISMATCH, SAVE_FAILED }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.INTRO,
    /** Página de la introducción (0 a [OnboardingViewModel.INTRO_PAGES] − 1). */
    val introPage: Int = 0,
    val pin: String = "",
    val error: OnboardingError? = null,
    val saving: Boolean = false,
    /** `null` hasta que se pide el permiso; `false` muestra el aviso con acceso a Ajustes (CU-01, 5a). */
    val notificationsGranted: Boolean? = null,
    val finished: Boolean = false,
)

/** CU-01: bienvenida, creación del PIN con confirmación y permiso de notificaciones. */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val pinRepository: PinRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    // El primer PIN se guarda aparte para no exponerlo en el estado de la pantalla.
    private var firstPin: String? = null

    fun nextIntroPage() = _state.update {
        if (it.introPage < INTRO_PAGES - 1) {
            it.copy(introPage = it.introPage + 1)
        } else {
            it.copy(step = OnboardingStep.CREATE_PIN)
        }
    }

    fun previousIntroPage() = _state.update { it.copy(introPage = (it.introPage - 1).coerceAtLeast(0)) }

    fun onDigit(digit: Char) = _state.update {
        if (it.saving || it.pin.length >= PinPolicy.MAX_LENGTH || digit !in '0'..'9') it
        else it.copy(pin = it.pin + digit, error = null)
    }

    fun onDelete() = _state.update { it.copy(pin = it.pin.dropLast(1), error = null) }

    fun submitPin() {
        val current = _state.value
        if (current.saving) return
        when (current.step) {
            OnboardingStep.CREATE_PIN -> {
                if (!PinPolicy.isValid(current.pin)) {
                    _state.update { it.copy(error = OnboardingError.PIN_TOO_SHORT) }
                    return
                }
                firstPin = current.pin
                _state.update { it.copy(step = OnboardingStep.CONFIRM_PIN, pin = "", error = null) }
            }
            OnboardingStep.CONFIRM_PIN -> confirmPin(current.pin)
            else -> Unit
        }
    }

    private fun confirmPin(confirmation: String) {
        val first = firstPin
        if (first == null || confirmation != first) {
            // Flujo alternativo 3a: los PIN no coinciden, se vuelve a empezar.
            firstPin = null
            _state.update { it.copy(step = OnboardingStep.CREATE_PIN, pin = "", error = OnboardingError.PIN_MISMATCH) }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                pinRepository.setPin(first)
                firstPin = null
                _state.update { it.copy(step = OnboardingStep.PERMISSIONS, pin = "", saving = false, error = null) }
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, pin = "", error = OnboardingError.SAVE_FAILED) }
            }
        }
    }

    /** Resultado de la solicitud del permiso de notificaciones. */
    fun onPermissionResult(granted: Boolean) = _state.update { it.copy(notificationsGranted = granted) }

    /** Termina el onboarding; el permiso denegado no lo impide. */
    fun finish() = _state.update { it.copy(finished = true) }

    companion object {
        const val INTRO_PAGES = 3
    }
}
