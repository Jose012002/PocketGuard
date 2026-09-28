package com.equipo.pocketguard.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.equipo.pocketguard.data.GuardStateRepository
import com.equipo.pocketguard.data.SettingsRepository
import com.equipo.pocketguard.decision.DetectionConfig
import com.equipo.pocketguard.decision.GuardState
import com.equipo.pocketguard.decision.SensitivityProfile
import com.equipo.pocketguard.security.BiometricAuthenticator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val config: DetectionConfig = DetectionConfig.Default,
    /** Perfil que coincide con los umbrales actuales, o `null` si están personalizados. */
    val profile: SensitivityProfile? = SensitivityProfile.MEDIUM,
    /** CU-09: los ajustes están bloqueados mientras la alarma está armada. */
    val locked: Boolean = false,
    val biometricEnabled: Boolean = false,
    /** El teléfono tiene sensor y biometría registrada. */
    val biometricSupported: Boolean = false,
)

/** CU-09: ajuste de parámetros, perfiles de sensibilidad, biometría y restablecimiento. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    repository: GuardStateRepository,
    biometric: BiometricAuthenticator,
) : ViewModel() {

    private val biometricSupported = biometric.isAvailable()

    val state: StateFlow<SettingsUiState> = combine(
        settings.config,
        settings.biometricEnabled,
        repository.state.map { it != GuardState.Disarmed },
    ) { config, biometricEnabled, armed ->
        SettingsUiState(
            config = config,
            profile = config.matchingProfile(),
            locked = armed,
            biometricEnabled = biometricEnabled,
            biometricSupported = biometricSupported,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        SettingsUiState(
            config = settings.current(),
            profile = settings.current().matchingProfile(),
            locked = repository.uiState.value.state != GuardState.Disarmed,
            biometricSupported = biometricSupported,
        ),
    )

    /** Guarda un cambio hecho con el deslizador de [field]. Se ignora con la alarma armada. */
    fun onFieldChanged(field: SettingField, value: Float) = updateConfig { field.write(it, value) }

    fun selectProfile(profile: SensitivityProfile) = updateConfig { it.withProfile(profile) }

    fun reset() = updateConfig { DetectionConfig.Default }

    fun setBiometricEnabled(enabled: Boolean) {
        if (enabled && !biometricSupported) return
        viewModelScope.launch { settings.setBiometricEnabled(enabled) }
    }

    private fun updateConfig(transform: (DetectionConfig) -> DetectionConfig) {
        if (state.value.locked) return
        viewModelScope.launch { settings.update(transform(settings.current())) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
