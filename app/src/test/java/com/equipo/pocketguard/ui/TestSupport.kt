package com.equipo.pocketguard.ui

import com.equipo.pocketguard.service.GuardController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Sustituye `Dispatchers.Main` para probar ViewModels (`viewModelScope` usa el hilo principal). */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)

    override fun finished(description: Description) = Dispatchers.resetMain()
}

/** Registra las órdenes que la interfaz enviaría al servicio. */
class FakeGuardController : GuardController {
    var armCalls = 0
    val disarmCalls = mutableListOf<Boolean>()

    override fun arm() {
        armCalls++
    }

    override fun disarm(authOk: Boolean) {
        disarmCalls += authOk
    }
}

/**
 * Espera en tiempo real hasta que se cumpla [condition]. El hash del PIN se calcula en `Dispatchers.Default`
 * (un hilo de verdad), así que el tiempo virtual de `runTest` no basta para esperarlo.
 */
suspend fun awaitCondition(timeoutMs: Long = 5_000, condition: () -> Boolean) {
    withContext(Dispatchers.Default) {
        withTimeout(timeoutMs) { while (!condition()) delay(POLL_MS) }
    }
}

private const val POLL_MS = 2L
