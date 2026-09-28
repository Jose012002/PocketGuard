package com.equipo.pocketguard.data

import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.equipo.pocketguard.decision.DetectionConfig
import com.equipo.pocketguard.decision.SensitivityProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRepositoryTest {

    private val dataStore = InMemoryDataStore()
    private val repository = SettingsRepository(dataStore)

    @Test
    fun `sin nada guardado devuelve los valores por defecto`() = runTest {
        assertEquals(DetectionConfig.Default, repository.config.first())
        assertEquals(DetectionConfig.Default, repository.current())
        assertFalse(repository.biometricEnabled.first())
    }

    @Test
    fun `update guarda y se puede volver a leer`() = runTest {
        val custom = DetectionConfig.Default.copy(motionThreshold = 3.2f, preAlarmGraceMs = 0, strobeIntervalMs = 300)
        repository.update(custom)
        assertEquals(custom, repository.config.first())
    }

    @Test
    fun `los cambios sobreviven a una nueva instancia sobre el mismo almacenamiento`() = runTest {
        val custom = DetectionConfig.Default.withProfile(SensitivityProfile.HIGH).copy(armingStableMs = 5_000)
        repository.update(custom)

        val reopened = SettingsRepository(dataStore)
        assertEquals(custom, reopened.current())
    }

    @Test
    fun `current refleja de inmediato lo ultimo guardado`() = runTest {
        assertEquals(DetectionConfig.Default, repository.current())
        val custom = DetectionConfig.Default.copy(coincidenceWindowMs = 2_000)

        repository.update(custom)

        assertEquals(custom, repository.current())
    }

    @Test
    fun `update recorta los valores fuera de rango y devuelve lo guardado`() = runTest {
        val saved = repository.update(DetectionConfig.Default.copy(motionThreshold = 99f, armingStableMs = 1))

        assertEquals(10f, saved.motionThreshold)
        assertEquals(1_000L, saved.armingStableMs)
        assertEquals(saved, repository.config.first())
        assertEquals(saved, repository.current())
    }

    @Test
    fun `un valor corrupto en el almacenamiento se recorta al leer`() = runTest {
        dataStore.updateData {
            it.toMutablePreferences().apply {
                this[floatPreferencesKey("cfg_motion_threshold")] = -5f
                this[longPreferencesKey("cfg_pre_alarm_grace_ms")] = 999_999L
            }
        }

        val config = repository.config.first()
        assertEquals(0.5f, config.motionThreshold)
        assertEquals(10_000L, config.preAlarmGraceMs)
        assertTrue(config.isValid)
    }

    @Test
    fun `reset restablece los valores por defecto`() = runTest {
        repository.update(DetectionConfig.Default.copy(motionThreshold = 5f))

        val restored = repository.reset()

        assertEquals(DetectionConfig.Default, restored)
        assertEquals(DetectionConfig.Default, repository.config.first())
        assertEquals(DetectionConfig.Default, repository.current())
    }

    @Test
    fun `la preferencia de biometria se guarda`() = runTest {
        repository.setBiometricEnabled(true)
        assertTrue(repository.biometricEnabled.first())
        repository.setBiometricEnabled(false)
        assertFalse(repository.biometricEnabled.first())
    }

    @Test
    fun `guardar la configuracion no altera la preferencia de biometria`() = runTest {
        repository.setBiometricEnabled(true)
        repository.update(DetectionConfig.Default.copy(motionThreshold = 4f))
        assertTrue(repository.biometricEnabled.first())
    }
}
