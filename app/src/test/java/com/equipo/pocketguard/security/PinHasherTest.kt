package com.equipo.pocketguard.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {

    private val hasher = PinHasher()
    private val salt = ByteArray(16) { it.toByte() }

    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }

    @Test
    fun `el mismo PIN con la misma sal produce el mismo hash`() {
        assertArrayEquals(hasher.hash("1234", salt), hasher.hash("1234", salt))
    }

    @Test
    fun `una sal distinta produce un hash distinto`() {
        val other = ByteArray(16) { (it + 1).toByte() }
        assertNotEquals(hasher.hash("1234", salt).hex(), hasher.hash("1234", other).hex())
    }

    @Test
    fun `un PIN distinto produce un hash distinto`() {
        assertNotEquals(hasher.hash("1234", salt).hex(), hasher.hash("1235", salt).hex())
    }

    @Test
    fun `usa PBKDF2 HMAC SHA256 con 120000 iteraciones y clave de 256 bits`() {
        // Vector calculado de forma independiente con hashlib.pbkdf2_hmac('sha256', b'1234', bytes(range(16)), 120000, 32).
        val expected = "5070321ca34dbdafe9b25371d1c0f83bcafe073fbae1f2274f00a6d6c68e9899"
        assertEquals(expected, hasher.hash("1234", salt).hex())
        assertEquals(32, hasher.hash("1234", salt).size)
    }

    @Test
    fun `coincide con el vector de prueba del RFC 7914`() {
        val rfc = PinHasher(iterations = 1, keyBits = 512)
        val expected = "55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc" +
            "49ca9cccf179b645991664b39d77ef317c71b845b1e30bd509112041d3a19783"
        assertEquals(expected, rfc.hash("passwd", "salt".toByteArray()).hex())
    }

    @Test
    fun `la sal tiene 16 bytes y es aleatoria`() {
        val a = hasher.newSalt()
        val b = hasher.newSalt()
        assertEquals(16, a.size)
        assertEquals(16, b.size)
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun `matches acepta el PIN correcto y rechaza los demas`() {
        val hash = hasher.hash("4321", salt)
        assertTrue(hasher.matches("4321", salt, hash))
        assertFalse(hasher.matches("4322", salt, hash))
        assertFalse(hasher.matches("", salt, hash))
        assertFalse(hasher.matches("43210", salt, hash))
    }

    @Test
    fun `el hash no contiene el PIN en claro`() {
        val hash = hasher.hash("123456", salt)
        assertFalse(hash.hex().contains("123456"))
        assertFalse(String(hash, Charsets.ISO_8859_1).contains("123456"))
    }
}
