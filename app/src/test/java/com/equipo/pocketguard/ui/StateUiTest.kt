package com.equipo.pocketguard.ui

import com.equipo.pocketguard.R
import com.equipo.pocketguard.decision.AvailableSensors
import com.equipo.pocketguard.decision.GuardState
import com.equipo.pocketguard.decision.Indicators
import com.equipo.pocketguard.ui.theme.GuardColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class StateUiTest {

    private val sensors = AvailableSensors(proximity = true, light = true, accelerometer = true)

    @Test
    fun `cada estado tiene su nombre y su color de la seccion 9`() {
        assertEquals(R.string.state_disarmed to GuardColors.Disarmed, GuardState.Disarmed.toUi().let { it.label to it.color })
        assertEquals(
            R.string.state_arming to GuardColors.Arming,
            GuardState.Arming(0, null, sensors).toUi().let { it.label to it.color },
        )
        assertEquals(
            R.string.state_stored to GuardColors.Stored,
            GuardState.Stored(5f, sensors).toUi().let { it.label to it.color },
        )
        assertEquals(
            R.string.state_suspicion to GuardColors.Suspicion,
            GuardState.Suspicion(0, 5f, Indicators.None, sensors).toUi().let { it.label to it.color },
        )
        assertEquals(
            R.string.state_pre_alarm to GuardColors.PreAlarm,
            GuardState.PreAlarm(0).toUi().let { it.label to it.color },
        )
        assertEquals(
            R.string.state_alarm to GuardColors.Alarm,
            GuardState.Alarm(0).toUi().let { it.label to it.color },
        )
    }

    @Test
    fun `los estados se distinguen tambien por texto e icono, no solo por color`() {
        val states = listOf(
            GuardState.Disarmed,
            GuardState.Arming(0, null, sensors),
            GuardState.Stored(5f, sensors),
            GuardState.Suspicion(0, 5f, Indicators.None, sensors),
            GuardState.PreAlarm(0),
            GuardState.Alarm(0),
        ).map { it.toUi() }
        assertEquals(states.size, states.map { it.label }.toSet().size)
        assertEquals(states.size, states.map { it.hint }.toSet().size)
    }

    @Test
    fun `pre-alarma y alarma comparten el rojo pero no el texto`() {
        assertNotEquals(GuardState.PreAlarm(0).toUi().label, GuardState.Alarm(0).toUi().label)
    }

    private fun ms(v: Long) = v * 1_000_000L

    @Test
    fun `la gracia restante empieza completa y baja con el tiempo`() {
        assertEquals(3_000L, graceRemainingMs(startedAtNs = ms(1_000), nowNs = ms(1_000), graceMs = 3_000))
        assertEquals(2_000L, graceRemainingMs(ms(1_000), ms(2_000), 3_000))
        assertEquals(1L, graceRemainingMs(ms(1_000), ms(3_999), 3_000))
    }

    @Test
    fun `la gracia restante nunca es negativa ni supera la gracia`() {
        assertEquals(0L, graceRemainingMs(ms(1_000), ms(4_000), 3_000))
        assertEquals(0L, graceRemainingMs(ms(1_000), ms(60_000), 3_000))
        assertEquals(3_000L, graceRemainingMs(ms(1_000), ms(500), 3_000)) // reloj "hacia atrás"
    }

    @Test
    fun `con gracia cero no queda tiempo`() {
        assertEquals(0L, graceRemainingMs(0, ms(100), 0))
    }
}
