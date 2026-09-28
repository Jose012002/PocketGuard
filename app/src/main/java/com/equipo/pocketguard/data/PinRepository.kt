package com.equipo.pocketguard.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.equipo.pocketguard.security.PinHasher
import com.equipo.pocketguard.security.PinPolicy
import java.io.IOException
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Guarda solo el hash del PIN y su sal, nunca el PIN (RF-02, RNF-07). El hash es costoso a propósito,
 * así que se calcula fuera del hilo principal. Para verificar con límite de intentos, usar `PinAuthenticator`.
 */
@Singleton
class PinRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val hasher: PinHasher,
) {
    /** `true` cuando ya existe un PIN (CU-01 decide con esto si muestra el onboarding). */
    val hasPin: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[HASH_KEY] != null && it[SALT_KEY] != null }
        .distinctUntilChanged()

    /** Crea o reemplaza el PIN. Lanza [IllegalArgumentException] si no son 4 a 6 dígitos. */
    suspend fun setPin(pin: String) {
        require(PinPolicy.isValid(pin)) { "El PIN debe tener de ${PinPolicy.MIN_LENGTH} a ${PinPolicy.MAX_LENGTH} dígitos" }
        val salt = hasher.newSalt()
        val hash = withContext(Dispatchers.Default) { hasher.hash(pin, salt) }
        dataStore.edit {
            it[HASH_KEY] = encode(hash)
            it[SALT_KEY] = encode(salt)
        }
    }

    /** `true` si [pin] coincide con el guardado; `false` si no coincide o si no hay PIN. */
    suspend fun verify(pin: String): Boolean {
        val prefs = dataStore.data.first()
        val hash = prefs[HASH_KEY]?.let(::decode) ?: return false
        val salt = prefs[SALT_KEY]?.let(::decode) ?: return false
        return withContext(Dispatchers.Default) { hasher.matches(pin, salt, hash) }
    }

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun decode(text: String): ByteArray = Base64.getDecoder().decode(text)

    private companion object {
        val HASH_KEY = stringPreferencesKey("pin_hash")
        val SALT_KEY = stringPreferencesKey("pin_salt")
    }
}
