package com.equipo.pocketguard.actuation

import com.equipo.pocketguard.decision.DetectionConfig
import com.equipo.pocketguard.decision.DetectionConfigProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TorchActuatorTest {

    private class FakeDriver(override val isAvailable: Boolean = true) : TorchDriver {
        val calls = mutableListOf<Boolean>()
        var failWhenOn = false
        var failAlways = false

        override fun setTorch(on: Boolean) {
            if (failAlways || (failWhenOn && on)) throw IllegalStateException("cámara ocupada")
            calls += on
        }
    }

    private val interval = DetectionConfig.Default.strobeIntervalMs

    private fun actuator(scope: TestScope, driver: FakeDriver, config: DetectionConfig = DetectionConfig.Default) =
        TorchActuator(driver, DetectionConfigProvider { config }, scope)

    @Test
    fun `alterna la linterna cada strobeIntervalMs`() = runTest {
        val driver = FakeDriver()
        val torch = actuator(this, driver)

        torch.start()
        runCurrent()
        assertEquals(listOf(true), driver.calls)

        advanceTimeBy(interval)
        runCurrent()
        assertEquals(listOf(true, false), driver.calls)

        advanceTimeBy(interval)
        runCurrent()
        assertEquals(listOf(true, false, true), driver.calls)

        torch.stop()
        advanceUntilIdle()
    }

    @Test
    fun `usa el intervalo de la configuracion`() = runTest {
        val driver = FakeDriver()
        val torch = actuator(this, driver, DetectionConfig.Default.copy(strobeIntervalMs = 400))

        torch.start()
        runCurrent()
        advanceTimeBy(399)
        runCurrent()
        assertEquals(listOf(true), driver.calls)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(true, false), driver.calls)

        torch.stop()
        advanceUntilIdle()
    }

    @Test
    fun `stop deja la linterna apagada y detiene el estroboscopio`() = runTest {
        val driver = FakeDriver()
        val torch = actuator(this, driver)

        torch.start()
        runCurrent()
        assertEquals(listOf(true), driver.calls) // quedó encendida
        torch.stop()
        advanceUntilIdle()

        assertEquals(false, driver.calls.last())
        val callsAfterStop = driver.calls.size
        advanceTimeBy(interval * 10)
        runCurrent()
        assertEquals(callsAfterStop, driver.calls.size)
    }

    @Test
    fun `start repetido no crea un segundo estroboscopio`() = runTest {
        val driver = FakeDriver()
        val torch = actuator(this, driver)

        torch.start()
        torch.start()
        runCurrent()
        assertEquals(listOf(true), driver.calls)

        torch.stop()
        advanceUntilIdle()
    }

    @Test
    fun `se puede reiniciar despues de detenerlo`() = runTest {
        val driver = FakeDriver()
        val torch = actuator(this, driver)

        torch.start()
        runCurrent()
        torch.stop()
        torch.start() // inmediato: debe esperar a que termine el apagado anterior
        runCurrent()

        assertEquals(listOf(true, false, true), driver.calls)
        torch.stop()
        advanceUntilIdle()
        assertEquals(false, driver.calls.last())
    }

    @Test
    fun `sin flash no hace nada y no falla`() = runTest {
        val driver = FakeDriver(isAvailable = false)
        val torch = actuator(this, driver)

        torch.start()
        advanceTimeBy(interval * 5)
        runCurrent()
        torch.stop()

        assertTrue(driver.calls.isEmpty())
    }

    @Test
    fun `si la camara esta ocupada el estroboscopio no lanza excepciones y sigue intentando`() =
        runTest {
            val driver = FakeDriver().apply { failAlways = true }
            val torch = actuator(this, driver)

            torch.start()
            runCurrent()
            advanceTimeBy(interval * 4)
            runCurrent()

            // La cámara se libera: el siguiente ciclo ya logra encender.
            driver.failAlways = false
            advanceTimeBy(interval * 2)
            runCurrent()
            assertTrue("debería haber vuelto a funcionar: ${driver.calls}", driver.calls.contains(true))

            torch.stop()
            advanceUntilIdle()
        }

    @Test
    fun `si solo falla al encender, stop no lanza excepciones`() = runTest {
        val driver = FakeDriver().apply { failWhenOn = true }
        val torch = actuator(this, driver)

        torch.start()
        runCurrent()
        advanceTimeBy(interval * 3)
        runCurrent()
        torch.stop()
        advanceUntilIdle()

        assertTrue(driver.calls.all { !it }) // solo se lograron apagados
    }
}
