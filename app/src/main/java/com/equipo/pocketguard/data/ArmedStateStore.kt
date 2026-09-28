package com.equipo.pocketguard.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** Recuerda si la alarma estaba armada para que el servicio pueda rearmarse si el sistema lo reinicia (sección 7.5). */
interface ArmedStateStore {
    suspend fun isArmed(): Boolean

    suspend fun setArmed(armed: Boolean)
}

@Singleton
class DataStoreArmedStateStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ArmedStateStore {

    override suspend fun isArmed(): Boolean = try {
        dataStore.data.first()[ARMED_KEY] ?: false
    } catch (e: IOException) {
        false
    }

    override suspend fun setArmed(armed: Boolean) {
        dataStore.edit { it[ARMED_KEY] = armed }
    }

    private companion object {
        val ARMED_KEY = booleanPreferencesKey("armed")
    }
}
