package com.equipo.pocketguard.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.equipo.pocketguard.decision.DetectionConfig
import com.equipo.pocketguard.decision.DetectionConfigProvider
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

/**
 * Parámetros de detección y preferencias del dueño, en DataStore. También es el [DetectionConfigProvider]
 * que usan el servicio y los actuadores. Todo valor se recorta a su rango al leer y al escribir.
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : DetectionConfigProvider {

    private val cached = AtomicReference<DetectionConfig?>(null)

    private val preferences: Flow<Preferences> = dataStore.data.catch {
        if (it is IOException) emit(emptyPreferences()) else throw it
    }

    val config: Flow<DetectionConfig> = preferences.map { it.toConfig() }.distinctUntilChanged()

    /** Si el dueño habilitó desarmar con biometría (RF-03). */
    val biometricEnabled: Flow<Boolean> = preferences.map { it[BIOMETRIC_KEY] ?: false }.distinctUntilChanged()

    /**
     * Configuración vigente, leída sin suspender. La primera vez lee DataStore (unos milisegundos) y luego
     * responde desde memoria; todos los cambios pasan por [update], que mantiene la copia al día.
     */
    override fun current(): DetectionConfig =
        cached.get() ?: runBlocking { config.first() }.also { cached.set(it) }

    /** Guarda [config] recortada a los rangos permitidos y devuelve lo realmente guardado. */
    suspend fun update(config: DetectionConfig): DetectionConfig {
        val valid = config.coerced()
        dataStore.edit { it.write(valid) }
        cached.set(valid)
        return valid
    }

    /** Restablece los valores por defecto (RF-28). */
    suspend fun reset(): DetectionConfig = update(DetectionConfig.Default)

    suspend fun setBiometricEnabled(enabled: Boolean) {
        dataStore.edit { it[BIOMETRIC_KEY] = enabled }
    }

    private fun Preferences.toConfig(): DetectionConfig {
        val d = DetectionConfig.Default
        return DetectionConfig(
            armingStableMs = this[ARMING_STABLE_MS] ?: d.armingStableMs,
            armingTimeoutMs = this[ARMING_TIMEOUT_MS] ?: d.armingTimeoutMs,
            darkEnterLux = this[DARK_ENTER_LUX] ?: d.darkEnterLux,
            darkExitLux = this[DARK_EXIT_LUX] ?: d.darkExitLux,
            luxSmoothingAlpha = this[LUX_SMOOTHING_ALPHA] ?: d.luxSmoothingAlpha,
            lightJumpFactor = this[LIGHT_JUMP_FACTOR] ?: d.lightJumpFactor,
            lightJumpMinLux = this[LIGHT_JUMP_MIN_LUX] ?: d.lightJumpMinLux,
            motionThreshold = this[MOTION_THRESHOLD] ?: d.motionThreshold,
            coincidenceWindowMs = this[COINCIDENCE_WINDOW_MS] ?: d.coincidenceWindowMs,
            preAlarmGraceMs = this[PRE_ALARM_GRACE_MS] ?: d.preAlarmGraceMs,
            strobeIntervalMs = this[STROBE_INTERVAL_MS] ?: d.strobeIntervalMs,
        ).coerced()
    }

    private fun MutablePreferences.write(c: DetectionConfig) {
        this[ARMING_STABLE_MS] = c.armingStableMs
        this[ARMING_TIMEOUT_MS] = c.armingTimeoutMs
        this[DARK_ENTER_LUX] = c.darkEnterLux
        this[DARK_EXIT_LUX] = c.darkExitLux
        this[LUX_SMOOTHING_ALPHA] = c.luxSmoothingAlpha
        this[LIGHT_JUMP_FACTOR] = c.lightJumpFactor
        this[LIGHT_JUMP_MIN_LUX] = c.lightJumpMinLux
        this[MOTION_THRESHOLD] = c.motionThreshold
        this[COINCIDENCE_WINDOW_MS] = c.coincidenceWindowMs
        this[PRE_ALARM_GRACE_MS] = c.preAlarmGraceMs
        this[STROBE_INTERVAL_MS] = c.strobeIntervalMs
    }

    private companion object {
        val ARMING_STABLE_MS = longPreferencesKey("cfg_arming_stable_ms")
        val ARMING_TIMEOUT_MS = longPreferencesKey("cfg_arming_timeout_ms")
        val DARK_ENTER_LUX = floatPreferencesKey("cfg_dark_enter_lux")
        val DARK_EXIT_LUX = floatPreferencesKey("cfg_dark_exit_lux")
        val LUX_SMOOTHING_ALPHA = floatPreferencesKey("cfg_lux_smoothing_alpha")
        val LIGHT_JUMP_FACTOR = floatPreferencesKey("cfg_light_jump_factor")
        val LIGHT_JUMP_MIN_LUX = floatPreferencesKey("cfg_light_jump_min_lux")
        val MOTION_THRESHOLD = floatPreferencesKey("cfg_motion_threshold")
        val COINCIDENCE_WINDOW_MS = longPreferencesKey("cfg_coincidence_window_ms")
        val PRE_ALARM_GRACE_MS = longPreferencesKey("cfg_pre_alarm_grace_ms")
        val STROBE_INTERVAL_MS = longPreferencesKey("cfg_strobe_interval_ms")
        val BIOMETRIC_KEY = booleanPreferencesKey("biometric_enabled")
    }
}
