package com.equipo.pocketguard.data

import androidx.datastore.preferences.core.stringPreferencesKey
import com.equipo.pocketguard.security.PinHasher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PinRepositoryTest {

    private val dataStore = InMemoryDataStore()
    private val repository = PinRepository(dataStore, PinHasher(iterations = 10))

    @Test
    fun `al inicio no hay PIN`() = runTest {
        assertFalse(repository.hasPin.first())
        assertFalse(repository.verify("1234"))
    }

    @Test
    fun `setPin guarda el PIN y verify lo reconoce`() = runTest {
        repository.setPin("1234")
        assertTrue(repository.hasPin.first())
        assertTrue(repository.verify("1234"))
        assertFalse(repository.verify("1235"))
    }

    @Test
    fun `el PIN nunca queda en texto plano en el almacenamiento`() = runTest {
        repository.setPin("482913")
        val stored = dataStore.data.first().asMap().values.map { it.toString() }
        assertTrue(stored.isNotEmpty())
        assertTrue(stored.none { it.contains("482913") })
    }

    @Test
    fun `se guardan el hash y la sal`() = runTest {
        repository.setPin("1234")
        val prefs = dataStore.data.first()
        assertNotNull(prefs[stringPreferencesKey("pin_hash")])
        assertNotNull(prefs[stringPreferencesKey("pin_salt")])
    }

    @Test
    fun `cambiar el PIN genera una sal nueva e invalida el anterior`() = runTest {
        repository.setPin("1234")
        val firstSalt = dataStore.data.first()[stringPreferencesKey("pin_salt")]

        repository.setPin("5678")

        assertNotEquals(firstSalt, dataStore.data.first()[stringPreferencesKey("pin_salt")])
        assertFalse(repository.verify("1234"))
        assertTrue(repository.verify("5678"))
    }

    @Test
    fun `rechaza PIN con formato invalido`() = runTest {
        for (pin in listOf("", "123", "1234567", "12ab")) {
            val failure = runCatching { repository.setPin(pin) }.exceptionOrNull()
            assertTrue("'$pin' debería rechazarse", failure is IllegalArgumentException)
        }
        assertFalse(repository.hasPin.first())
    }

    @Test
    fun `los datos de otra clave no afectan al PIN`() = runTest {
        repository.setPin("1234")
        SettingsRepository(dataStore).setBiometricEnabled(true)
        assertTrue(repository.verify("1234"))
        assertEquals(true, repository.hasPin.first())
    }
}
