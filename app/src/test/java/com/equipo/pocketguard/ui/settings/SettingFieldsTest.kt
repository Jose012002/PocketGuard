package com.equipo.pocketguard.ui.settings

import com.equipo.pocketguard.decision.ConfigParam
import com.equipo.pocketguard.decision.DetectionConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingFieldsTest {

    private val default = DetectionConfig.Default

    private fun field(param: ConfigParam) = SettingFields.all.single { it.param == param }

    @Test
    fun `hay un campo para cada parametro de DetectionConfig`() {
        assertEquals(ConfigParam.entries.toSet(), SettingFields.all.map { it.param }.toSet())
        assertEquals(ConfigParam.entries.size, SettingFields.all.size)
    }

    @Test
    fun `el valor por defecto de cada campo cae dentro de su rango y sobre la rejilla`() {
        for (f in SettingFields.all) {
            val range = f.range(default)
            val value = f.read(default)
            assertTrue("${f.param}: $value no está en $range", value in range)
            val steps = (value - range.start) / f.step
            assertEquals("${f.param} no cae sobre la rejilla del deslizador", steps.toDouble(), Math.rint(steps.toDouble()), 1e-3)
        }
    }

    @Test
    fun `el rango de cada campo coincide con DetectionConfig_Ranges`() {
        val r = DetectionConfig.Ranges
        assertEquals(r.ARMING_STABLE_MS.first.toFloat()..r.ARMING_STABLE_MS.last.toFloat(), field(ConfigParam.ARMING_STABLE_MS).range(default))
        assertEquals(r.ARMING_TIMEOUT_MS.first.toFloat()..r.ARMING_TIMEOUT_MS.last.toFloat(), field(ConfigParam.ARMING_TIMEOUT_MS).range(default))
        assertEquals(r.DARK_ENTER_LUX, field(ConfigParam.DARK_ENTER_LUX).range(default))
        assertEquals(r.LUX_SMOOTHING_ALPHA, field(ConfigParam.LUX_SMOOTHING_ALPHA).range(default))
        assertEquals(r.LIGHT_JUMP_FACTOR, field(ConfigParam.LIGHT_JUMP_FACTOR).range(default))
        assertEquals(r.LIGHT_JUMP_MIN_LUX, field(ConfigParam.LIGHT_JUMP_MIN_LUX).range(default))
        assertEquals(r.MOTION_THRESHOLD, field(ConfigParam.MOTION_THRESHOLD).range(default))
        assertEquals(r.COINCIDENCE_WINDOW_MS.first.toFloat()..r.COINCIDENCE_WINDOW_MS.last.toFloat(), field(ConfigParam.COINCIDENCE_WINDOW_MS).range(default))
        assertEquals(r.PRE_ALARM_GRACE_MS.first.toFloat()..r.PRE_ALARM_GRACE_MS.last.toFloat(), field(ConfigParam.PRE_ALARM_GRACE_MS).range(default))
        assertEquals(r.STROBE_INTERVAL_MS.first.toFloat()..r.STROBE_INTERVAL_MS.last.toFloat(), field(ConfigParam.STROBE_INTERVAL_MS).range(default))
    }

    @Test
    fun `el rango de darkExitLux depende de darkEnterLux`() {
        val exit = field(ConfigParam.DARK_EXIT_LUX)
        assertEquals(15f..100f, exit.range(default.copy(darkEnterLux = 10f)))
        assertEquals(6f..100f, exit.range(default.copy(darkEnterLux = 1f)))
        assertEquals(55f..100f, exit.range(default.copy(darkEnterLux = 50f)))
    }

    @Test
    fun `los extremos de cada rango producen una configuracion valida`() {
        for (f in SettingFields.all) {
            val range = f.range(default)
            assertTrue("${f.param} en el mínimo", f.write(default, range.start).isValid)
            assertTrue("${f.param} en el máximo", f.write(default, range.endInclusive).isValid)
        }
    }

    @Test
    fun `escribir y leer devuelve el mismo valor`() {
        assertEquals(4000L, field(ConfigParam.ARMING_STABLE_MS).write(default, 4000f).armingStableMs)
        assertEquals(20_000L, field(ConfigParam.ARMING_TIMEOUT_MS).write(default, 20_000f).armingTimeoutMs)
        assertEquals(7f, field(ConfigParam.DARK_ENTER_LUX).write(default, 7f).darkEnterLux)
        assertEquals(0.75f, field(ConfigParam.LUX_SMOOTHING_ALPHA).write(default, 0.75f).luxSmoothingAlpha, 1e-6f)
        assertEquals(5.5f, field(ConfigParam.LIGHT_JUMP_FACTOR).write(default, 5.5f).lightJumpFactor, 1e-6f)
        assertEquals(50f, field(ConfigParam.LIGHT_JUMP_MIN_LUX).write(default, 50f).lightJumpMinLux)
        assertEquals(3.3f, field(ConfigParam.MOTION_THRESHOLD).write(default, 3.3f).motionThreshold, 1e-4f)
        assertEquals(1_500L, field(ConfigParam.COINCIDENCE_WINDOW_MS).write(default, 1_500f).coincidenceWindowMs)
        assertEquals(0L, field(ConfigParam.PRE_ALARM_GRACE_MS).write(default, 0f).preAlarmGraceMs)
        assertEquals(250L, field(ConfigParam.STROBE_INTERVAL_MS).write(default, 250f).strobeIntervalMs)
    }

    @Test
    fun `un valor intermedio se ajusta a la rejilla del deslizador`() {
        assertEquals(3_000L, field(ConfigParam.ARMING_STABLE_MS).write(default, 3_120f).armingStableMs)
        assertEquals(3_500L, field(ConfigParam.ARMING_STABLE_MS).write(default, 3_300f).armingStableMs)
        assertEquals(2.6f, field(ConfigParam.MOTION_THRESHOLD).write(default, 2.63f).motionThreshold, 1e-4f)
    }

    @Test
    fun `un valor fuera de rango se recorta`() {
        assertEquals(10_000L, field(ConfigParam.ARMING_STABLE_MS).write(default, 99_999f).armingStableMs)
        assertEquals(0.5f, field(ConfigParam.MOTION_THRESHOLD).write(default, -3f).motionThreshold, 1e-6f)
    }

    @Test
    fun `subir el umbral de oscuro arrastra al umbral de claro`() {
        val config = default.copy(darkEnterLux = 10f, darkExitLux = 15f)
        val updated = field(ConfigParam.DARK_ENTER_LUX).write(config, 30f)
        assertEquals(30f, updated.darkEnterLux)
        assertEquals(35f, updated.darkExitLux)
        assertTrue(updated.isValid)
    }

    @Test
    fun `escribir un campo no altera los demas`() {
        val updated = field(ConfigParam.MOTION_THRESHOLD).write(default, 4f)
        assertEquals(default.copy(motionThreshold = 4f), updated)
    }

    @Test
    fun `los pasos del deslizador cuadran con el rango`() {
        assertEquals(17, field(ConfigParam.ARMING_STABLE_MS).sliderSteps(default)) // 18 intervalos
        assertEquals(94, field(ConfigParam.MOTION_THRESHOLD).sliderSteps(default)) // 95 intervalos
        assertEquals(84, field(ConfigParam.DARK_EXIT_LUX).sliderSteps(default)) // 15 a 100
    }

    @Test
    fun `el valor mostrado convierte milisegundos a segundos donde corresponde`() {
        assertEquals(3f, field(ConfigParam.ARMING_STABLE_MS).displayValue(3_000f))
        assertEquals(1_000f, field(ConfigParam.COINCIDENCE_WINDOW_MS).displayValue(1_000f))
        assertEquals(2.5f, field(ConfigParam.MOTION_THRESHOLD).displayValue(2.5f))
    }
}
