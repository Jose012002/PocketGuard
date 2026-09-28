package com.equipo.pocketguard.decision

import com.equipo.pocketguard.processing.SensorSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TheftDetectionEngineTest {

    private val config = DetectionConfig.Default
    private val engine = TheftDetectionEngine(config)

    private val all = AvailableSensors(proximity = true, light = true, accelerometer = true)
    private val noLight = AvailableSensors(proximity = true, light = false, accelerometer = true)
    private val noProximity = AvailableSensors(proximity = false, light = true, accelerometer = true)

    private fun ms(v: Long) = v * 1_000_000L

    /** Snapshot en reposo dentro del bolsillo: cerca, oscuro, sin movimiento. */
    private fun snap(
        tMs: Long,
        near: Boolean = true,
        dark: Boolean = true,
        lux: Float? = 5f,
        motion: Float = 0f,
    ) = GuardInput.Snapshot(
        SensorSnapshot(
            isNear = near,
            distanceCm = if (near) 0f else 5f,
            lux = lux,
            isDark = dark,
            motion = motion,
            timestampNs = ms(tMs),
        ),
    )

    private fun tick(tMs: Long) = GuardInput.Tick(ms(tMs))

    // Snapshots que activan un solo indicador (la luz base de los estados de prueba es 5 lux).
    private fun farOnly(t: Long) = snap(t, near = false)
    private fun lightOnly(t: Long) = snap(t, lux = 200f)
    private fun motionOnly(t: Long) = snap(t, motion = 5f)
    private fun everything(t: Long) = snap(t, near = false, dark = false, lux = 200f, motion = 5f)

    private val stored = GuardState.Stored(baselineLux = 5f, sensors = all)

    private fun suspicion(openedAtMs: Long, flags: Indicators = Indicators.None, sensors: AvailableSensors = all) =
        GuardState.Suspicion(ms(openedAtMs), baselineLux = 5f, flags = flags, sensors = sensors)

    private val alarmEffects = listOf(
        Effect.StartSiren,
        Effect.StartStrobe,
        Effect.Vibrate(VibrationPattern.ALARM),
        Effect.ShowAlarmUi,
        Effect.Log(EventType.ALARM),
    )

    private val preAlarmEffects = listOf(
        Effect.Vibrate(VibrationPattern.SOFT),
        Effect.ShowPreAlarmUi,
        Effect.Log(EventType.PRE_ALARM),
    )

    private val disarmEffects = listOf(
        Effect.StopAll,
        Effect.StopMonitoring,
        Effect.ReleaseWakeLock,
        Effect.ClearAlarmUi,
        Effect.Log(EventType.DISARMED),
    )

    private fun assertTransition(expectedState: GuardState, expectedEffects: List<Effect>, actual: Transition) {
        assertEquals(expectedState, actual.state)
        assertEquals(expectedEffects, actual.effects)
    }

    private fun assertNoChange(state: GuardState, actual: Transition) =
        assertTransition(state, emptyList(), actual)

    // ---------------------------------------------------------------- Disarmed

    @Test
    fun `Disarmed + Arm con sensores suficientes pasa a Arming`() {
        val t = engine.reduce(GuardState.Disarmed, GuardInput.Arm(all, ms(100)))
        assertTransition(
            GuardState.Arming(ms(100), null, all),
            listOf(
                Effect.StartMonitoring,
                Effect.AcquireWakeLock,
                Effect.SetSampling(SamplingMode.NORMAL),
                Effect.UpdateStatusNotification,
                Effect.Log(EventType.ARMED),
            ),
            t,
        )
    }

    @Test
    fun `Disarmed + Arm con sensores insuficientes no cambia el estado`() {
        val t = engine.reduce(
            GuardState.Disarmed,
            GuardInput.Arm(AvailableSensors(proximity = false, light = false, accelerometer = true), ms(0)),
        )
        assertTransition(
            GuardState.Disarmed,
            listOf(Effect.ShowMessage(MessageCode.INSUFFICIENT_SENSORS)),
            t,
        )
    }

    @Test
    fun `Arm sin acelerometro es insuficiente aunque haya proximidad y luz`() {
        val t = engine.reduce(
            GuardState.Disarmed,
            GuardInput.Arm(AvailableSensors(proximity = true, light = true, accelerometer = false), ms(0)),
        )
        assertEquals(GuardState.Disarmed, t.state)
    }

    @Test
    fun `Disarmed ignora snapshots, ticks y desarmes`() {
        assertNoChange(GuardState.Disarmed, engine.reduce(GuardState.Disarmed, snap(0)))
        assertNoChange(GuardState.Disarmed, engine.reduce(GuardState.Disarmed, tick(0)))
        assertNoChange(GuardState.Disarmed, engine.reduce(GuardState.Disarmed, GuardInput.Disarm(true)))
        assertNoChange(GuardState.Disarmed, engine.reduce(GuardState.Disarmed, GuardInput.Disarm(false)))
    }

    @Test
    fun `Arm estando armado se ignora`() {
        val arming = GuardState.Arming(ms(0), null, all)
        assertNoChange(arming, engine.reduce(arming, GuardInput.Arm(all, ms(500))))
    }

    // ---------------------------------------------------------------- Arming

    @Test
    fun `Arming + bolsillo verdadero sin pocketSince registra el inicio`() {
        val t = engine.reduce(GuardState.Arming(ms(0), null, all), snap(1_000))
        assertNoChange(GuardState.Arming(ms(0), ms(1_000), all), t)
    }

    @Test
    fun `Arming + bolsillo estable durante armingStableMs pasa a Stored con la luz base`() {
        val state = GuardState.Arming(ms(0), ms(1_000), all)
        val t = engine.reduce(state, snap(1_000 + config.armingStableMs, lux = 7f))
        assertTransition(
            GuardState.Stored(baselineLux = 7f, sensors = all),
            listOf(
                Effect.Vibrate(VibrationPattern.CONFIRM),
                Effect.UpdateStatusNotification,
                Effect.Log(EventType.STORED),
            ),
            t,
        )
    }

    @Test
    fun `Arming + bolsillo verdadero antes de armingStableMs no cambia`() {
        val state = GuardState.Arming(ms(0), ms(1_000), all)
        assertNoChange(state, engine.reduce(state, snap(1_000 + config.armingStableMs - 1)))
    }

    @Test
    fun `Arming + bolsillo falso reinicia pocketSince`() {
        val state = GuardState.Arming(ms(0), ms(1_000), all)
        val t = engine.reduce(state, snap(2_000, near = false))
        assertNoChange(GuardState.Arming(ms(0), null, all), t)
    }

    @Test
    fun `Arming + bolsillo falso con pocketSince nulo no cambia`() {
        val state = GuardState.Arming(ms(0), null, all)
        assertNoChange(state, engine.reduce(state, snap(500, dark = false)))
    }

    @Test
    fun `la condicion de bolsillo interrumpida reinicia el contador`() {
        var state: GuardState = GuardState.Arming(ms(0), null, all)
        state = engine.reduce(state, snap(1_000)).state // empieza el bolsillo
        state = engine.reduce(state, snap(2_500, near = false)).state // se interrumpe
        state = engine.reduce(state, snap(3_000)).state // vuelve a empezar
        // Desde 1 000 ya habrían pasado 3 s, pero el contador se reinició en 3 000.
        assertTransition(
            GuardState.Arming(ms(0), ms(3_000), all),
            emptyList(),
            engine.reduce(state, snap(4_500)),
        )
        assertTrue(engine.reduce(state, snap(3_000 + config.armingStableMs)).state is GuardState.Stored)
    }

    @Test
    fun `Arming + Tick al vencer armingTimeoutMs vuelve a Disarmed`() {
        val state = GuardState.Arming(ms(0), null, all)
        val t = engine.reduce(state, tick(config.armingTimeoutMs))
        assertTransition(
            GuardState.Disarmed,
            listOf(
                Effect.StopMonitoring,
                Effect.ReleaseWakeLock,
                Effect.ShowMessage(MessageCode.ARMING_TIMEOUT),
                Effect.Log(EventType.TIMEOUT),
            ),
            t,
        )
    }

    @Test
    fun `Arming + Tick antes del timeout no cambia`() {
        val state = GuardState.Arming(ms(0), null, all)
        assertNoChange(state, engine.reduce(state, tick(config.armingTimeoutMs - 1)))
    }

    @Test
    fun `en modo degradado sin luz basta con proximidad para la condicion de bolsillo`() {
        val state = GuardState.Arming(ms(0), ms(0), noLight)
        // isDark = false, pero no hay sensor de luz: el término se considera verdadero.
        val t = engine.reduce(state, snap(config.armingStableMs, dark = false, lux = null))
        assertEquals(GuardState.Stored(0f, noLight), t.state)
    }

    @Test
    fun `en modo degradado sin proximidad basta con luz para la condicion de bolsillo`() {
        val state = GuardState.Arming(ms(0), ms(0), noProximity)
        val t = engine.reduce(state, snap(config.armingStableMs, near = false))
        assertEquals(GuardState.Stored(5f, noProximity), t.state)
    }

    // ---------------------------------------------------------------- Stored

    @Test
    fun `Stored + cualquier indicador activo abre la sospecha`() {
        val cases = listOf(
            farOnly(100) to Indicators(proximityFar = true, lightJump = false, motion = false),
            lightOnly(100) to Indicators(proximityFar = false, lightJump = true, motion = false),
            motionOnly(100) to Indicators(proximityFar = false, lightJump = false, motion = true),
        )
        for ((input, flags) in cases) {
            assertTransition(
                suspicion(100, flags),
                listOf(Effect.SetSampling(SamplingMode.HIGH), Effect.Log(EventType.SUSPICION)),
                engine.reduce(stored, input),
            )
        }
    }

    @Test
    fun `Stored sin indicadores activos no cambia`() {
        assertNoChange(stored, engine.reduce(stored, snap(100)))
        assertNoChange(stored, engine.reduce(stored, tick(100)))
    }

    @Test
    fun `el salto de luz depende de la luz base y de lightJumpMinLux`() {
        val bright = GuardState.Stored(baselineLux = 50f, sensors = all) // umbral = max(200, 30) = 200
        assertNoChange(bright, engine.reduce(bright, snap(0, lux = 199f)))
        assertTrue(engine.reduce(bright, snap(0, lux = 200f)).state is GuardState.Suspicion)

        val dark = GuardState.Stored(baselineLux = 1f, sensors = all) // umbral = max(4, 30) = 30
        assertNoChange(dark, engine.reduce(dark, snap(0, lux = 29f)))
        assertTrue(engine.reduce(dark, snap(0, lux = 30f)).state is GuardState.Suspicion)
    }

    @Test
    fun `el movimiento se activa exactamente en motionThreshold`() {
        assertNoChange(stored, engine.reduce(stored, snap(0, motion = config.motionThreshold - 0.01f)))
        assertTrue(engine.reduce(stored, snap(0, motion = config.motionThreshold)).state is GuardState.Suspicion)
    }

    @Test
    fun `un snapshot sin lux no activa el salto de luz`() {
        assertNoChange(stored, engine.reduce(stored, snap(0, lux = null)))
    }

    // ---------------------------------------------------------------- Suspicion

    @Test
    fun `Suspicion acumula indicadores sin completar`() {
        val state = suspicion(0, Indicators(proximityFar = true, lightJump = false, motion = false))
        val t = engine.reduce(state, lightOnly(200))
        assertNoChange(
            suspicion(0, Indicators(proximityFar = true, lightJump = true, motion = false)),
            t,
        )
    }

    @Test
    fun `Suspicion sin indicadores nuevos no cambia`() {
        val state = suspicion(0, Indicators(proximityFar = true, lightJump = false, motion = false))
        assertNoChange(state, engine.reduce(state, farOnly(100)))
    }

    @Test
    fun `Suspicion + todos los indicadores con gracia mayor a cero pasa a PreAlarm`() {
        val state = suspicion(0, Indicators(proximityFar = true, lightJump = true, motion = false))
        assertTransition(GuardState.PreAlarm(ms(300)), preAlarmEffects, engine.reduce(state, motionOnly(300)))
    }

    @Test
    fun `Suspicion + todos los indicadores con gracia cero pasa directo a Alarm`() {
        val zero = TheftDetectionEngine(config.copy(preAlarmGraceMs = 0))
        val state = suspicion(0, Indicators(proximityFar = true, lightJump = true, motion = false))
        assertTransition(GuardState.Alarm(ms(300)), alarmEffects, zero.reduce(state, motionOnly(300)))
    }

    @Test
    fun `Suspicion + Tick al vencer la ventana vuelve a Stored`() {
        val state = suspicion(100)
        assertTransition(
            stored,
            listOf(Effect.SetSampling(SamplingMode.NORMAL)),
            engine.reduce(state, tick(100 + config.coincidenceWindowMs)),
        )
    }

    @Test
    fun `Suspicion + Tick antes de vencer la ventana no cambia`() {
        val state = suspicion(100)
        assertNoChange(state, engine.reduce(state, tick(100 + config.coincidenceWindowMs - 1)))
    }

    @Test
    fun `Suspicion ignora entradas Arm`() {
        val state = suspicion(0)
        assertNoChange(state, engine.reduce(state, GuardInput.Arm(all, ms(10))))
    }

    @Test
    fun `solo proximidad lejos - la ventana expira y vuelve a Stored sin alarma`() {
        var t = engine.reduce(stored, farOnly(100))
        assertTrue(t.state is GuardState.Suspicion)
        t = engine.reduce(t.state, farOnly(600)) // sigue lejos, sin luz ni movimiento
        assertTrue(t.state is GuardState.Suspicion)
        t = engine.reduce(t.state, tick(100 + config.coincidenceWindowMs))
        assertEquals(stored, t.state)
    }

    @Test
    fun `solo movimiento (caminar con el telefono guardado) no genera alarma`() {
        var state: GuardState = stored
        var now = 0L
        repeat(200) { // 20 s de caminata: el movimiento se activa y la ventana expira una y otra vez
            now += 100
            state = engine.reduce(state, motionOnly(now)).state
            state = engine.reduce(state, tick(now)).state
            assertTrue("estado inesperado $state", state is GuardState.Stored || state is GuardState.Suspicion)
        }
    }

    @Test
    fun `solo salto de luz (se enciende la luz del cuarto) no genera alarma`() {
        var state: GuardState = stored
        var now = 0L
        repeat(100) {
            now += 100
            state = engine.reduce(state, lightOnly(now)).state
            state = engine.reduce(state, tick(now)).state
            assertTrue("estado inesperado $state", state is GuardState.Stored || state is GuardState.Suspicion)
        }
    }

    @Test
    fun `los tres indicadores en snapshots distintos dentro de la ventana llevan a PreAlarm`() {
        var t = engine.reduce(stored, farOnly(0))
        t = engine.reduce(t.state, lightOnly(300))
        assertTrue(t.state is GuardState.Suspicion)
        t = engine.reduce(t.state, motionOnly(600))
        assertEquals(GuardState.PreAlarm(ms(600)), t.state)
    }

    @Test
    fun `los tres indicadores repartidos en mas tiempo que la ventana no generan alarma`() {
        var t = engine.reduce(stored, farOnly(0))
        t = engine.reduce(t.state, lightOnly(400))
        // Llega el movimiento con la ventana ya vencida y sin Tick previo.
        t = engine.reduce(t.state, motionOnly(config.coincidenceWindowMs + 500))
        assertTrue("estado inesperado ${t.state}", t.state !is GuardState.PreAlarm && t.state !is GuardState.Alarm)
    }

    @Test
    fun `un snapshot con la ventana vencida reabre la sospecha por nivel sin completarla`() {
        val state = suspicion(0, Indicators(proximityFar = true, lightJump = true, motion = false))
        val t = engine.reduce(state, everything(1_500))
        // Reabre con los indicadores actuales (los tres activos), pero la detección se completa recién en el siguiente snapshot.
        assertEquals(
            suspicion(1_500, Indicators(proximityFar = true, lightJump = true, motion = true)),
            t.state,
        )
        assertEquals(
            listOf(
                Effect.SetSampling(SamplingMode.NORMAL),
                Effect.SetSampling(SamplingMode.HIGH),
                Effect.Log(EventType.SUSPICION),
            ),
            t.effects,
        )
    }

    @Test
    fun `un snapshot con la ventana vencida y sin indicadores vuelve a Stored`() {
        val state = suspicion(0, Indicators(proximityFar = true, lightJump = false, motion = false))
        assertTransition(
            stored,
            listOf(Effect.SetSampling(SamplingMode.NORMAL)),
            engine.reduce(state, snap(2_000)),
        )
    }

    @Test
    fun `extraccion lenta - la ventana expira, se reabre y un movimiento posterior dispara la alarma`() {
        var t = engine.reduce(stored, farOnly(0)) // se saca despacio: solo proximidad lejos
        t = engine.reduce(t.state, tick(config.coincidenceWindowMs))
        assertEquals(stored, t.state) // ventana expirada, sin alarma

        t = engine.reduce(t.state, snap(1_200, near = false, lux = 200f)) // sigue fuera del bolsillo, hay luz
        assertTrue(t.state is GuardState.Suspicion)

        t = engine.reduce(t.state, snap(1_500, near = false, lux = 200f, motion = 5f)) // ahora sí se mueve
        assertEquals(GuardState.PreAlarm(ms(1_500)), t.state)
    }

    @Test
    fun `modo degradado sin luz - proximidad y movimiento bastan`() {
        val st = GuardState.Stored(baselineLux = 0f, sensors = noLight)
        var t = engine.reduce(st, snap(0, near = false, lux = null))
        assertTrue(t.state is GuardState.Suspicion)
        t = engine.reduce(t.state, snap(200, near = false, lux = null, motion = 5f))
        assertEquals(GuardState.PreAlarm(ms(200)), t.state)
    }

    @Test
    fun `modo degradado sin proximidad - luz y movimiento bastan`() {
        val st = GuardState.Stored(baselineLux = 5f, sensors = noProximity)
        var t = engine.reduce(st, lightOnly(0))
        t = engine.reduce(t.state, snap(200, lux = 200f, motion = 5f))
        assertEquals(GuardState.PreAlarm(ms(200)), t.state)
    }

    @Test
    fun `modo degradado - el movimiento solo tampoco dispara la alarma`() {
        val st = GuardState.Stored(baselineLux = 0f, sensors = noLight)
        var t = engine.reduce(st, snap(0, lux = null, motion = 5f))
        t = engine.reduce(t.state, snap(200, lux = null, motion = 5f))
        assertTrue(t.state is GuardState.Suspicion)
    }

    // ---------------------------------------------------------------- PreAlarm

    @Test
    fun `PreAlarm + Tick al agotarse la gracia pasa a Alarm`() {
        assertTransition(
            GuardState.Alarm(ms(config.preAlarmGraceMs + 100)),
            alarmEffects,
            engine.reduce(GuardState.PreAlarm(ms(100)), tick(100 + config.preAlarmGraceMs)),
        )
    }

    @Test
    fun `PreAlarm + Tick antes de agotarse la gracia no cambia`() {
        val state = GuardState.PreAlarm(ms(100))
        assertNoChange(state, engine.reduce(state, tick(100 + config.preAlarmGraceMs - 1)))
    }

    @Test
    fun `PreAlarm ignora snapshots`() {
        val state = GuardState.PreAlarm(ms(100))
        assertNoChange(state, engine.reduce(state, everything(200)))
    }

    // ---------------------------------------------------------------- Alarm

    @Test
    fun `Alarm ignora snapshots y ticks`() {
        val state = GuardState.Alarm(ms(0))
        assertNoChange(state, engine.reduce(state, snap(100)))
        assertNoChange(state, engine.reduce(state, tick(1_000_000)))
    }

    // ---------------------------------------------------------------- Disarm

    private val armedStates: List<GuardState> = listOf(
        GuardState.Arming(ms(0), null, all),
        stored,
        suspicion(0),
        GuardState.PreAlarm(ms(0)),
        GuardState.Alarm(ms(0)),
    )

    @Test
    fun `Disarm con autenticacion correcta desarma desde cualquier estado armado`() {
        for (state in armedStates) {
            assertTransition(GuardState.Disarmed, disarmEffects, engine.reduce(state, GuardInput.Disarm(true)))
        }
    }

    @Test
    fun `Disarm con autenticacion fallida no cambia el estado y registra el intento`() {
        for (state in armedStates) {
            assertTransition(
                state,
                listOf(Effect.Log(EventType.AUTH_FAILED)),
                engine.reduce(state, GuardInput.Disarm(false)),
            )
        }
    }

    @Test
    fun `Disarm fallido en Alarm no la detiene`() {
        val state = GuardState.Alarm(ms(0))
        assertEquals(state, engine.reduce(state, GuardInput.Disarm(false)).state)
    }

    // ---------------------------------------------------------------- Flujo completo

    @Test
    fun `flujo completo E1 armar, guardar, extraer y llegar a la alarma`() {
        var t = engine.reduce(GuardState.Disarmed, GuardInput.Arm(all, ms(0)))
        t = engine.reduce(t.state, snap(500))
        t = engine.reduce(t.state, snap(500 + config.armingStableMs))
        assertTrue(t.state is GuardState.Stored)
        t = engine.reduce(t.state, everything(10_000))
        assertTrue(t.state is GuardState.Suspicion)
        t = engine.reduce(t.state, everything(10_060))
        assertEquals(GuardState.PreAlarm(ms(10_060)), t.state)
        t = engine.reduce(t.state, tick(10_060 + config.preAlarmGraceMs))
        assertTrue(t.state is GuardState.Alarm)
        t = engine.reduce(t.state, GuardInput.Disarm(true))
        assertEquals(GuardState.Disarmed, t.state)
    }

    @Test
    fun `flujo E6 el dueno se autentica durante la gracia y no suena la sirena`() {
        var t = engine.reduce(stored, everything(0))
        t = engine.reduce(t.state, everything(60))
        assertTrue(t.state is GuardState.PreAlarm)
        t = engine.reduce(t.state, GuardInput.Disarm(true))
        assertEquals(GuardState.Disarmed, t.state)
        assertTrue(Effect.StartSiren !in t.effects)
    }
}
