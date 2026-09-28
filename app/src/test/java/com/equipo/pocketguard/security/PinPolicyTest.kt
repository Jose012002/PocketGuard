package com.equipo.pocketguard.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinPolicyTest {

    @Test
    fun `acepta de 4 a 6 digitos`() {
        for (pin in listOf("1234", "12345", "123456", "0000", "000000")) assertTrue(pin, PinPolicy.isValid(pin))
    }

    @Test
    fun `rechaza menos de 4 o mas de 6 digitos`() {
        for (pin in listOf("", "1", "123", "1234567")) assertFalse(pin, PinPolicy.isValid(pin))
    }

    @Test
    fun `rechaza caracteres que no son digitos`() {
        for (pin in listOf("12a4", "12 34", "12.34", "-1234", "١٢٣٤", "１２３４")) {
            assertFalse(pin, PinPolicy.isValid(pin))
        }
    }
}
