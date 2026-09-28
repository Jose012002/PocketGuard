package com.equipo.pocketguard.decision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionConfigValidationTest {

    private val default = DetectionConfig.Default

    @Test
    fun `los valores por defecto son los de la seccion 8_4 y son validos`() {
        assertEquals(3_000L, default.armingStableMs)
        assertEquals(15_000L, default.armingTimeoutMs)
        assertEquals(10f, default.darkEnterLux)
        assertEquals(20f, default.darkExitLux)
        assertEquals(0.5f, default.luxSmoothingAlpha)
        assertEquals(4.0f, default.lightJumpFactor)
        assertEquals(30f, default.lightJumpMinLux)
        assertEquals(2.5f, default.motionThreshold)
        assertEquals(1_000L, default.coincidenceWindowMs)
        assertEquals(3_000L, default.preAlarmGraceMs)
        assertEquals(150L, default.strobeIntervalMs)
        assertTrue(default.isValid)
        assertTrue(default.validate().isEmpty())
    }

    @Test
    fun `los limites de cada rango son validos`() {
        val min = DetectionConfig(
            armingStableMs = 1_000, armingTimeoutMs = 5_000, darkEnterLux = 1f, darkExitLux = 6f,
            luxSmoothingAlpha = 0.1f, lightJumpFactor = 1.5f, lightJumpMinLux = 5f, motionThreshold = 0.5f,
            coincidenceWindowMs = 300, preAlarmGraceMs = 0, strobeIntervalMs = 80,
        )
        val max = DetectionConfig(
            armingStableMs = 10_000, armingTimeoutMs = 60_000, darkEnterLux = 50f, darkExitLux = 100f,
            luxSmoothingAlpha = 1.0f, lightJumpFactor = 20f, lightJumpMinLux = 500f, motionThreshold = 10f,
            coincidenceWindowMs = 3_000, preAlarmGraceMs = 10_000, strobeIntervalMs = 1_000,
        )
        assertTrue(min.isValid)
        assertTrue(max.isValid)
    }

    @Test
    fun `cada parametro fuera de rango se reporta`() {
        val cases = mapOf(
            ConfigParam.ARMING_STABLE_MS to default.copy(armingStableMs = 999),
            ConfigParam.ARMING_TIMEOUT_MS to default.copy(armingTimeoutMs = 60_001),
            ConfigParam.DARK_ENTER_LUX to default.copy(darkEnterLux = 0.5f),
            ConfigParam.DARK_EXIT_LUX to default.copy(darkExitLux = 101f),
            ConfigParam.LUX_SMOOTHING_ALPHA to default.copy(luxSmoothingAlpha = 0.05f),
            ConfigParam.LIGHT_JUMP_FACTOR to default.copy(lightJumpFactor = 21f),
            ConfigParam.LIGHT_JUMP_MIN_LUX to default.copy(lightJumpMinLux = 4f),
            ConfigParam.MOTION_THRESHOLD to default.copy(motionThreshold = 10.1f),
            ConfigParam.COINCIDENCE_WINDOW_MS to default.copy(coincidenceWindowMs = 299),
            ConfigParam.PRE_ALARM_GRACE_MS to default.copy(preAlarmGraceMs = -1),
            ConfigParam.STROBE_INTERVAL_MS to default.copy(strobeIntervalMs = 79),
        )
        assertEquals(ConfigParam.entries.toSet(), cases.keys)
        for ((param, config) in cases) {
            assertEquals(setOf(param), config.validate())
            assertFalse(config.isValid)
        }
    }

    @Test
    fun `darkExitLux debe superar a darkEnterLux en al menos 5`() {
        assertEquals(
            setOf(ConfigParam.DARK_EXIT_LUX),
            default.copy(darkEnterLux = 10f, darkExitLux = 14.9f).validate(),
        )
        assertTrue(default.copy(darkEnterLux = 10f, darkExitLux = 15f).isValid)
    }

    @Test
    fun `coerced recorta cada valor a su rango`() {
        val wild = DetectionConfig(
            armingStableMs = 0, armingTimeoutMs = 1_000_000, darkEnterLux = 500f, darkExitLux = 0f,
            luxSmoothingAlpha = 5f, lightJumpFactor = 0f, lightJumpMinLux = 9_999f, motionThreshold = -1f,
            coincidenceWindowMs = 10, preAlarmGraceMs = 99_999, strobeIntervalMs = 0,
        )
        val fixed = wild.coerced()
        assertTrue(fixed.isValid)
        assertEquals(1_000L, fixed.armingStableMs)
        assertEquals(60_000L, fixed.armingTimeoutMs)
        assertEquals(50f, fixed.darkEnterLux)
        assertEquals(55f, fixed.darkExitLux)
        assertEquals(1.0f, fixed.luxSmoothingAlpha)
        assertEquals(1.5f, fixed.lightJumpFactor)
        assertEquals(500f, fixed.lightJumpMinLux)
        assertEquals(0.5f, fixed.motionThreshold)
        assertEquals(300L, fixed.coincidenceWindowMs)
        assertEquals(10_000L, fixed.preAlarmGraceMs)
        assertEquals(80L, fixed.strobeIntervalMs)
    }

    @Test
    fun `coerced no altera una configuracion valida`() {
        assertEquals(default, default.coerced())
    }

    @Test
    fun `los perfiles ajustan los tres umbrales de la tabla`() {
        val low = default.withProfile(SensitivityProfile.LOW)
        assertEquals(4.0f, low.motionThreshold)
        assertEquals(6.0f, low.lightJumpFactor)
        assertEquals(800L, low.coincidenceWindowMs)

        val medium = default.withProfile(SensitivityProfile.MEDIUM)
        assertEquals(2.5f, medium.motionThreshold)
        assertEquals(4.0f, medium.lightJumpFactor)
        assertEquals(1_000L, medium.coincidenceWindowMs)

        val high = default.withProfile(SensitivityProfile.HIGH)
        assertEquals(1.5f, high.motionThreshold)
        assertEquals(2.5f, high.lightJumpFactor)
        assertEquals(1_500L, high.coincidenceWindowMs)

        for (profile in SensitivityProfile.entries) assertTrue(default.withProfile(profile).isValid)
    }

    @Test
    fun `un perfil no toca los demas parametros`() {
        val custom = default.copy(preAlarmGraceMs = 5_000, strobeIntervalMs = 200)
        val high = custom.withProfile(SensitivityProfile.HIGH)
        assertEquals(5_000L, high.preAlarmGraceMs)
        assertEquals(200L, high.strobeIntervalMs)
    }

    @Test
    fun `matchingProfile reconoce el perfil aplicado y devuelve null si esta personalizado`() {
        assertEquals(SensitivityProfile.MEDIUM, default.matchingProfile())
        for (profile in SensitivityProfile.entries) {
            assertEquals(profile, default.withProfile(profile).matchingProfile())
        }
        assertNull(default.copy(motionThreshold = 3.3f).matchingProfile())
    }
}
