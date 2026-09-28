package com.equipo.pocketguard.ui.settings

import androidx.annotation.StringRes
import com.equipo.pocketguard.R
import com.equipo.pocketguard.decision.ConfigParam
import com.equipo.pocketguard.decision.DetectionConfig
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Descripción de un parámetro editable con deslizador. Los rangos salen de [DetectionConfig.Ranges]:
 * la pantalla no define umbrales propios. Kotlin puro (salvo los ids de texto) para poder probarlo.
 */
data class SettingField(
    val param: ConfigParam,
    @param:StringRes val label: Int,
    @param:StringRes val description: Int,
    /** Unidad que se muestra junto al valor (puede ser vacía). */
    @param:StringRes val unit: Int,
    /** Separación entre valores del deslizador, en la unidad interna del parámetro. */
    val step: Float,
    /** Se divide el valor interno por esto para mostrarlo (por ejemplo, 1000 para pasar ms a s). */
    val displayDivisor: Float,
    val decimals: Int,
    private val rangeOf: (DetectionConfig) -> ClosedFloatingPointRange<Float>,
    private val readFrom: (DetectionConfig) -> Float,
    private val writeTo: (DetectionConfig, Float) -> DetectionConfig,
) {
    /** Rango permitido con la configuración actual (el de `darkExitLux` depende de `darkEnterLux`). */
    fun range(config: DetectionConfig): ClosedFloatingPointRange<Float> = rangeOf(config)

    fun read(config: DetectionConfig): Float = readFrom(config)

    /** Aplica [value] ajustado a la rejilla del deslizador y recortado al rango. */
    fun write(config: DetectionConfig, value: Float): DetectionConfig =
        writeTo(config, snap(value, range(config))).coerced()

    /** Cantidad de posiciones intermedias que espera `Slider(steps = ...)`. */
    fun sliderSteps(config: DetectionConfig): Int {
        val range = range(config)
        return (((range.endInclusive - range.start) / step).roundToInt() - 1).coerceAtLeast(0)
    }

    fun displayValue(value: Float): Float = value / displayDivisor

    private fun snap(value: Float, range: ClosedFloatingPointRange<Float>): Float {
        val steps = ((value - range.start) / step).roundToInt()
        return (range.start + steps * step).coerceIn(range.start, range.endInclusive)
    }
}

object SettingFields {
    private val r = DetectionConfig.Ranges

    private fun ClosedRange<Long>.toFloatRange() = start.toFloat()..endInclusive.toFloat()

    val all: List<SettingField> = listOf(
        SettingField(
            ConfigParam.ARMING_STABLE_MS, R.string.setting_arming_stable, R.string.setting_arming_stable_desc,
            R.string.unit_seconds, step = 500f, displayDivisor = 1000f, decimals = 1,
            rangeOf = { r.ARMING_STABLE_MS.toFloatRange() },
            readFrom = { it.armingStableMs.toFloat() },
            writeTo = { c, v -> c.copy(armingStableMs = v.roundToLong()) },
        ),
        SettingField(
            ConfigParam.ARMING_TIMEOUT_MS, R.string.setting_arming_timeout, R.string.setting_arming_timeout_desc,
            R.string.unit_seconds, step = 1000f, displayDivisor = 1000f, decimals = 0,
            rangeOf = { r.ARMING_TIMEOUT_MS.toFloatRange() },
            readFrom = { it.armingTimeoutMs.toFloat() },
            writeTo = { c, v -> c.copy(armingTimeoutMs = v.roundToLong()) },
        ),
        SettingField(
            ConfigParam.DARK_ENTER_LUX, R.string.setting_dark_enter, R.string.setting_dark_enter_desc,
            R.string.unit_lux, step = 1f, displayDivisor = 1f, decimals = 0,
            rangeOf = { r.DARK_ENTER_LUX },
            readFrom = { it.darkEnterLux },
            writeTo = { c, v -> c.copy(darkEnterLux = v) },
        ),
        SettingField(
            ConfigParam.DARK_EXIT_LUX, R.string.setting_dark_exit, R.string.setting_dark_exit_desc,
            R.string.unit_lux, step = 1f, displayDivisor = 1f, decimals = 0,
            rangeOf = { (it.darkEnterLux + r.DARK_EXIT_MARGIN_LUX)..r.DARK_EXIT_MAX_LUX },
            readFrom = { it.darkExitLux },
            writeTo = { c, v -> c.copy(darkExitLux = v) },
        ),
        SettingField(
            ConfigParam.LUX_SMOOTHING_ALPHA, R.string.setting_lux_smoothing, R.string.setting_lux_smoothing_desc,
            R.string.unit_none, step = 0.05f, displayDivisor = 1f, decimals = 2,
            rangeOf = { r.LUX_SMOOTHING_ALPHA },
            readFrom = { it.luxSmoothingAlpha },
            writeTo = { c, v -> c.copy(luxSmoothingAlpha = v) },
        ),
        SettingField(
            ConfigParam.LIGHT_JUMP_FACTOR, R.string.setting_light_jump_factor, R.string.setting_light_jump_factor_desc,
            R.string.unit_times, step = 0.5f, displayDivisor = 1f, decimals = 1,
            rangeOf = { r.LIGHT_JUMP_FACTOR },
            readFrom = { it.lightJumpFactor },
            writeTo = { c, v -> c.copy(lightJumpFactor = v) },
        ),
        SettingField(
            ConfigParam.LIGHT_JUMP_MIN_LUX, R.string.setting_light_jump_min, R.string.setting_light_jump_min_desc,
            R.string.unit_lux, step = 5f, displayDivisor = 1f, decimals = 0,
            rangeOf = { r.LIGHT_JUMP_MIN_LUX },
            readFrom = { it.lightJumpMinLux },
            writeTo = { c, v -> c.copy(lightJumpMinLux = v) },
        ),
        SettingField(
            ConfigParam.MOTION_THRESHOLD, R.string.setting_motion_threshold, R.string.setting_motion_threshold_desc,
            R.string.unit_ms2, step = 0.1f, displayDivisor = 1f, decimals = 1,
            rangeOf = { r.MOTION_THRESHOLD },
            readFrom = { it.motionThreshold },
            writeTo = { c, v -> c.copy(motionThreshold = v) },
        ),
        SettingField(
            ConfigParam.COINCIDENCE_WINDOW_MS, R.string.setting_window, R.string.setting_window_desc,
            R.string.unit_ms, step = 100f, displayDivisor = 1f, decimals = 0,
            rangeOf = { r.COINCIDENCE_WINDOW_MS.toFloatRange() },
            readFrom = { it.coincidenceWindowMs.toFloat() },
            writeTo = { c, v -> c.copy(coincidenceWindowMs = v.roundToLong()) },
        ),
        SettingField(
            ConfigParam.PRE_ALARM_GRACE_MS, R.string.setting_grace, R.string.setting_grace_desc,
            R.string.unit_seconds, step = 500f, displayDivisor = 1000f, decimals = 1,
            rangeOf = { r.PRE_ALARM_GRACE_MS.toFloatRange() },
            readFrom = { it.preAlarmGraceMs.toFloat() },
            writeTo = { c, v -> c.copy(preAlarmGraceMs = v.roundToLong()) },
        ),
        SettingField(
            ConfigParam.STROBE_INTERVAL_MS, R.string.setting_strobe, R.string.setting_strobe_desc,
            R.string.unit_ms, step = 10f, displayDivisor = 1f, decimals = 0,
            rangeOf = { r.STROBE_INTERVAL_MS.toFloatRange() },
            readFrom = { it.strobeIntervalMs.toFloat() },
            writeTo = { c, v -> c.copy(strobeIntervalMs = v.roundToLong()) },
        ),
    )
}
