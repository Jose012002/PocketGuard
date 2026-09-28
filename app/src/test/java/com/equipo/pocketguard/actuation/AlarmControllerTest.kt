package com.equipo.pocketguard.actuation

import com.equipo.pocketguard.decision.AvailableSensors
import com.equipo.pocketguard.decision.Effect
import com.equipo.pocketguard.decision.EventType
import com.equipo.pocketguard.decision.GuardState
import com.equipo.pocketguard.decision.MessageCode
import com.equipo.pocketguard.decision.SamplingMode
import com.equipo.pocketguard.decision.VibrationPattern
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Test

class AlarmControllerTest {

    private val siren = mockk<SirenActuator>(relaxed = true)
    private val torch = mockk<TorchActuator>(relaxed = true)
    private val vibration = mockk<VibrationActuator>(relaxed = true)
    private val notifier = mockk<AlarmNotifier>(relaxed = true)
    private val controller = AlarmController(siren, torch, vibration, notifier)

    private val alarmState = GuardState.Alarm(startedAtNs = 0)

    private val alarmEffects = listOf(
        Effect.StartSiren,
        Effect.StartStrobe,
        Effect.Vibrate(VibrationPattern.ALARM),
        Effect.ShowAlarmUi,
        Effect.Log(EventType.ALARM),
    )

    @Test
    fun `los efectos de alarma activan sirena, flash, vibracion y notificacion en orden`() {
        controller.execute(alarmEffects, alarmState)

        verifyOrder {
            siren.start()
            torch.start()
            vibration.vibrate(VibrationPattern.ALARM)
            notifier.showAlarm()
        }
    }

    @Test
    fun `StopAll detiene los tres actuadores`() {
        controller.execute(listOf(Effect.StopAll), GuardState.Disarmed)

        verify(exactly = 1) { siren.stop() }
        verify(exactly = 1) { torch.stop() }
        verify(exactly = 1) { vibration.stop() }
    }

    @Test
    fun `ClearAlarmUi retira las notificaciones de alarma`() {
        controller.execute(listOf(Effect.ClearAlarmUi), GuardState.Disarmed)
        verify(exactly = 1) { notifier.clearAlarmUi() }
    }

    @Test
    fun `la pre-alarma vibra suave y muestra su notificacion`() {
        controller.execute(
            listOf(Effect.Vibrate(VibrationPattern.SOFT), Effect.ShowPreAlarmUi),
            GuardState.PreAlarm(0),
        )
        verifyOrder {
            vibration.vibrate(VibrationPattern.SOFT)
            notifier.showPreAlarm()
        }
    }

    @Test
    fun `la confirmacion de guardado vibra con el patron CONFIRM`() {
        controller.execute(listOf(Effect.Vibrate(VibrationPattern.CONFIRM)), GuardState.Stored(5f, AvailableSensors(proximity = true, light = true, accelerometer = true)))
        verify(exactly = 1) { vibration.vibrate(VibrationPattern.CONFIRM) }
    }

    @Test
    fun `UpdateStatusNotification recibe el estado resultante de la transicion`() {
        val stored = GuardState.Stored(5f, AvailableSensors(proximity = true, light = true, accelerometer = true))
        controller.execute(listOf(Effect.UpdateStatusNotification), stored)
        verify(exactly = 1) { notifier.updateStatus(stored) }
    }

    @Test
    fun `ShowMessage entrega el codigo al notificador`() {
        controller.execute(listOf(Effect.ShowMessage(MessageCode.ARMING_TIMEOUT)), GuardState.Disarmed)
        verify(exactly = 1) { notifier.showMessage(MessageCode.ARMING_TIMEOUT) }
    }

    @Test
    fun `si un actuador falla los demas siguen actuando (RNF-16)`() {
        every { torch.start() } throws IllegalStateException("cámara ocupada")
        every { siren.start() } throws SecurityException("sin audio")

        controller.execute(alarmEffects, alarmState)

        verify(exactly = 1) { siren.start() }
        verify(exactly = 1) { torch.start() }
        verify(exactly = 1) { vibration.vibrate(VibrationPattern.ALARM) }
        verify(exactly = 1) { notifier.showAlarm() }
    }

    @Test
    fun `si un actuador falla al detener los demas tambien se detienen`() {
        every { siren.stop() } throws IllegalStateException("fallo")

        controller.execute(listOf(Effect.StopAll), GuardState.Disarmed)

        verify(exactly = 1) { torch.stop() }
        verify(exactly = 1) { vibration.stop() }
    }

    @Test
    fun `los efectos del servicio no tocan ningun actuador`() {
        controller.execute(
            listOf(
                Effect.StartMonitoring,
                Effect.StopMonitoring,
                Effect.AcquireWakeLock,
                Effect.ReleaseWakeLock,
                Effect.SetSampling(SamplingMode.HIGH),
                Effect.Log(EventType.SUSPICION),
            ),
            GuardState.Disarmed,
        )
        confirmVerified(siren, torch, vibration, notifier)
    }

    @Test
    fun `una lista vacia no hace nada`() {
        controller.execute(emptyList(), GuardState.Disarmed)
        confirmVerified(siren, torch, vibration, notifier)
    }
}
