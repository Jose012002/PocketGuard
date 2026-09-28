package com.equipo.pocketguard.decision

/** Parámetros configurables de la detección (sección 8.4). Ninguna otra clase debe llevar umbrales propios. */
data class DetectionConfig(
    val armingStableMs: Long = 3_000,
    val armingTimeoutMs: Long = 15_000,
    val darkEnterLux: Float = 10f,
    val darkExitLux: Float = 20f,
    val luxSmoothingAlpha: Float = 0.5f,
    val lightJumpFactor: Float = 4.0f,
    val lightJumpMinLux: Float = 30f,
    val motionThreshold: Float = 2.5f,
    val coincidenceWindowMs: Long = 1_000,
    val preAlarmGraceMs: Long = 3_000,
    val strobeIntervalMs: Long = 150,
) {
    /** Parámetros cuyo valor está fuera de rango. Vacío si la configuración es válida. */
    fun validate(): Set<ConfigParam> = buildSet {
        if (armingStableMs !in Ranges.ARMING_STABLE_MS) add(ConfigParam.ARMING_STABLE_MS)
        if (armingTimeoutMs !in Ranges.ARMING_TIMEOUT_MS) add(ConfigParam.ARMING_TIMEOUT_MS)
        if (darkEnterLux !in Ranges.DARK_ENTER_LUX) add(ConfigParam.DARK_ENTER_LUX)
        if (darkExitLux !in (darkEnterLux + Ranges.DARK_EXIT_MARGIN_LUX)..Ranges.DARK_EXIT_MAX_LUX) {
            add(ConfigParam.DARK_EXIT_LUX)
        }
        if (luxSmoothingAlpha !in Ranges.LUX_SMOOTHING_ALPHA) add(ConfigParam.LUX_SMOOTHING_ALPHA)
        if (lightJumpFactor !in Ranges.LIGHT_JUMP_FACTOR) add(ConfigParam.LIGHT_JUMP_FACTOR)
        if (lightJumpMinLux !in Ranges.LIGHT_JUMP_MIN_LUX) add(ConfigParam.LIGHT_JUMP_MIN_LUX)
        if (motionThreshold !in Ranges.MOTION_THRESHOLD) add(ConfigParam.MOTION_THRESHOLD)
        if (coincidenceWindowMs !in Ranges.COINCIDENCE_WINDOW_MS) add(ConfigParam.COINCIDENCE_WINDOW_MS)
        if (preAlarmGraceMs !in Ranges.PRE_ALARM_GRACE_MS) add(ConfigParam.PRE_ALARM_GRACE_MS)
        if (strobeIntervalMs !in Ranges.STROBE_INTERVAL_MS) add(ConfigParam.STROBE_INTERVAL_MS)
    }

    val isValid: Boolean get() = validate().isEmpty()

    /** Copia con cada valor recortado a su rango permitido. */
    fun coerced(): DetectionConfig {
        val enter = darkEnterLux.coerceIn(Ranges.DARK_ENTER_LUX)
        return copy(
            armingStableMs = armingStableMs.coerceIn(Ranges.ARMING_STABLE_MS),
            armingTimeoutMs = armingTimeoutMs.coerceIn(Ranges.ARMING_TIMEOUT_MS),
            darkEnterLux = enter,
            darkExitLux = darkExitLux.coerceIn((enter + Ranges.DARK_EXIT_MARGIN_LUX)..Ranges.DARK_EXIT_MAX_LUX),
            luxSmoothingAlpha = luxSmoothingAlpha.coerceIn(Ranges.LUX_SMOOTHING_ALPHA),
            lightJumpFactor = lightJumpFactor.coerceIn(Ranges.LIGHT_JUMP_FACTOR),
            lightJumpMinLux = lightJumpMinLux.coerceIn(Ranges.LIGHT_JUMP_MIN_LUX),
            motionThreshold = motionThreshold.coerceIn(Ranges.MOTION_THRESHOLD),
            coincidenceWindowMs = coincidenceWindowMs.coerceIn(Ranges.COINCIDENCE_WINDOW_MS),
            preAlarmGraceMs = preAlarmGraceMs.coerceIn(Ranges.PRE_ALARM_GRACE_MS),
            strobeIntervalMs = strobeIntervalMs.coerceIn(Ranges.STROBE_INTERVAL_MS),
        )
    }

    /** Aplica los tres umbrales que definen un perfil de sensibilidad (RF-29). */
    fun withProfile(profile: SensitivityProfile): DetectionConfig = copy(
        motionThreshold = profile.motionThreshold,
        lightJumpFactor = profile.lightJumpFactor,
        coincidenceWindowMs = profile.coincidenceWindowMs,
    )

    /** Perfil cuyos tres umbrales coinciden con esta configuración, o `null` si está personalizada. */
    fun matchingProfile(): SensitivityProfile? = SensitivityProfile.entries.firstOrNull {
        motionThreshold == it.motionThreshold &&
            lightJumpFactor == it.lightJumpFactor &&
            coincidenceWindowMs == it.coincidenceWindowMs
    }

    /** Rangos permitidos de cada parámetro. */
    object Ranges {
        val ARMING_STABLE_MS = 1_000L..10_000L
        val ARMING_TIMEOUT_MS = 5_000L..60_000L
        val DARK_ENTER_LUX = 1f..50f
        const val DARK_EXIT_MARGIN_LUX = 5f
        const val DARK_EXIT_MAX_LUX = 100f
        val LUX_SMOOTHING_ALPHA = 0.1f..1.0f
        val LIGHT_JUMP_FACTOR = 1.5f..20f
        val LIGHT_JUMP_MIN_LUX = 5f..500f
        val MOTION_THRESHOLD = 0.5f..10f
        val COINCIDENCE_WINDOW_MS = 300L..3_000L
        val PRE_ALARM_GRACE_MS = 0L..10_000L
        val STROBE_INTERVAL_MS = 80L..1_000L
    }

    companion object {
        val Default = DetectionConfig()
    }
}

enum class ConfigParam {
    ARMING_STABLE_MS,
    ARMING_TIMEOUT_MS,
    DARK_ENTER_LUX,
    DARK_EXIT_LUX,
    LUX_SMOOTHING_ALPHA,
    LIGHT_JUMP_FACTOR,
    LIGHT_JUMP_MIN_LUX,
    MOTION_THRESHOLD,
    COINCIDENCE_WINDOW_MS,
    PRE_ALARM_GRACE_MS,
    STROBE_INTERVAL_MS,
}

/** Perfiles de sensibilidad (RF-29). */
enum class SensitivityProfile(
    val motionThreshold: Float,
    val lightJumpFactor: Float,
    val coincidenceWindowMs: Long,
) {
    LOW(motionThreshold = 4.0f, lightJumpFactor = 6.0f, coincidenceWindowMs = 800),
    MEDIUM(motionThreshold = 2.5f, lightJumpFactor = 4.0f, coincidenceWindowMs = 1_000),
    HIGH(motionThreshold = 1.5f, lightJumpFactor = 2.5f, coincidenceWindowMs = 1_500),
}
