package com.equipo.pocketguard.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject

/** Formato válido del PIN: entre 4 y 6 dígitos (RF-01). */
object PinPolicy {
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 6

    fun isValid(pin: String): Boolean = pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it in '0'..'9' }
}

/**
 * Hash del PIN con PBKDF2WithHmacSHA256 y sal aleatoria (RF-02). El PIN nunca se guarda ni se registra
 * en texto plano (RNF-07). Kotlin/JVM puro, sin Android.
 */
class PinHasher(
    private val iterations: Int = DEFAULT_ITERATIONS,
    private val saltBytes: Int = SALT_BYTES,
    private val keyBits: Int = KEY_BITS,
) {
    @Inject
    constructor() : this(DEFAULT_ITERATIONS)

    private val random = SecureRandom()

    fun newSalt(): ByteArray = ByteArray(saltBytes).also(random::nextBytes)

    fun hash(pin: String, salt: ByteArray): ByteArray {
        val chars = pin.toCharArray()
        val spec = PBEKeySpec(chars, salt, iterations, keyBits)
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
            chars.fill('\u0000')
        }
    }

    /** Compara en tiempo constante para no filtrar información por el tiempo de respuesta. */
    fun matches(pin: String, salt: ByteArray, expected: ByteArray): Boolean =
        MessageDigest.isEqual(hash(pin, salt), expected)

    companion object {
        const val ALGORITHM = "PBKDF2WithHmacSHA256"
        const val DEFAULT_ITERATIONS = 120_000
        const val SALT_BYTES = 16
        const val KEY_BITS = 256
    }
}
