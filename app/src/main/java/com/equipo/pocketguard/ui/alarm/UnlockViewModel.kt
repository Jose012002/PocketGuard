package com.equipo.pocketguard.ui.alarm

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.equipo.pocketguard.data.SettingsRepository
import com.equipo.pocketguard.security.AuthResult
import com.equipo.pocketguard.security.BiometricAuthenticator
import com.equipo.pocketguard.security.BiometricResult
import com.equipo.pocketguard.security.PinAuthenticator
import com.equipo.pocketguard.security.PinPolicy
import com.equipo.pocketguard.service.GuardController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UnlockUiState(
    val pin: String = "",
    val submitting: Boolean = false,
    /** Intentos que quedan tras un PIN incorrecto; `null` si no hay error que mostrar. */
    val wrongPinAttemptsLeft: Int? = null,
    /** Milisegundos de bloqueo pendientes (RF-05); 0 si no hay bloqueo. */
    val lockedMs: Long = 0,
) {
    val locked: Boolean get() = lockedMs > 0
    val canSubmit: Boolean get() = pin.length >= PinPolicy.MIN_LENGTH && !submitting && !locked
}

/**
 * Autenticación para desarmar (CU-05, CU-06 y CU-07), compartida por el diálogo de inicio y por
 * `AlarmActivity`. Un intento fallido se informa al servicio con `disarm(authOk = false)` para que quede
 * registrado; un acierto lo desarma.
 */
@HiltViewModel
class UnlockViewModel @Inject constructor(
    private val authenticator: PinAuthenticator,
    private val guardController: GuardController,
    private val biometric: BiometricAuthenticator,
    settings: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(UnlockUiState())
    val state: StateFlow<UnlockUiState> = _state.asStateFlow()

    private val _unlocked = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Se emite una vez cuando el dueño se autenticó y se pidió el desarme. */
    val unlocked: SharedFlow<Unit> = _unlocked.asSharedFlow()

    /** La biometría está habilitada por el dueño y el dispositivo la soporta (RF-03). */
    val biometricAvailable: StateFlow<Boolean> = settings.biometricEnabled
        .map { it && biometric.isAvailable() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private var countdownJob: Job? = null

    /** Limpia el PIN y los mensajes al abrir de nuevo el diálogo. El bloqueo, si sigue vigente, se conserva. */
    fun reset() {
        _state.value = UnlockUiState()
        refreshLock()
    }

    fun onDigit(digit: Char) = _state.update {
        if (it.locked || it.submitting || it.pin.length >= PinPolicy.MAX_LENGTH || digit !in '0'..'9') {
            it
        } else {
            it.copy(pin = it.pin + digit, wrongPinAttemptsLeft = null)
        }
    }.also { if (_state.value.pin.length == PinPolicy.MAX_LENGTH) submit() }

    fun onDelete() = _state.update { it.copy(pin = it.pin.dropLast(1), wrongPinAttemptsLeft = null) }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(submitting = true) }
        viewModelScope.launch {
            // Con bloqueo vigente no se verifica ni se registra un intento más.
            val locked = authenticator.remainingLockMs()
            if (locked > 0) {
                _state.update { it.copy(pin = "", submitting = false, lockedMs = locked) }
                startCountdown()
                return@launch
            }
            when (val result = authenticator.verify(current.pin)) {
                AuthResult.Success -> {
                    guardController.disarm(authOk = true)
                    _unlocked.emit(Unit)
                    _state.value = UnlockUiState()
                }
                is AuthResult.WrongPin -> {
                    guardController.disarm(authOk = false)
                    _state.update {
                        it.copy(pin = "", submitting = false, wrongPinAttemptsLeft = result.attemptsLeft)
                    }
                }
                is AuthResult.Locked -> {
                    guardController.disarm(authOk = false)
                    _state.update { it.copy(pin = "", submitting = false, lockedMs = result.remainingMs) }
                    startCountdown()
                }
            }
        }
    }

    /**
     * Abre el diálogo biométrico. No guarda la actividad. Un cierre voluntario no cuenta como fallo
     * y el PIN sigue disponible.
     */
    fun authenticateWithBiometric(activity: FragmentActivity, title: String, negativeText: String) {
        if (!biometricAvailable.value || _state.value.locked) return
        biometric.authenticate(activity, title, null, negativeText) { result ->
            if (result is BiometricResult.Success) onBiometricSuccess()
        }
    }

    internal fun onBiometricSuccess() {
        authenticator.registerBiometricSuccess()
        guardController.disarm(authOk = true)
        _state.value = UnlockUiState()
        _unlocked.tryEmit(Unit)
    }

    /** Vuelve a leer el bloqueo por si sigue vigente al reabrir la pantalla. */
    private fun refreshLock() {
        val remaining = authenticator.remainingLockMs()
        if (remaining > 0) {
            _state.update { it.copy(lockedMs = remaining) }
            startCountdown()
        }
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (true) {
                val remaining = authenticator.remainingLockMs()
                _state.update { it.copy(lockedMs = remaining) }
                if (remaining <= 0) break
                delay(COUNTDOWN_STEP_MS)
            }
        }
    }

    private companion object {
        const val COUNTDOWN_STEP_MS = 250L
    }
}
