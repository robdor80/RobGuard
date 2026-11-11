package es.robertodorado.robguard.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordGeneratorTest {
    @Test fun containsEveryEnabledCharacterFamily() {
        repeat(30) {
            val password = PasswordGenerator.generate(PasswordOptions(length = 24))
            assertEquals(24, password.length)
            assertTrue(password.any(Char::isLowerCase))
            assertTrue(password.any(Char::isUpperCase))
            assertTrue(password.any(Char::isDigit))
            assertTrue(password.any { !it.isLetterOrDigit() })
        }
    }

    @Test fun minimalModeAndStrongRandomness() {
        val options = PasswordOptions(length = 12, uppercase = false, digits = false, symbols = false)
        val first = PasswordGenerator.generate(options)
        val second = PasswordGenerator.generate(options)
        assertEquals(12, first.length)
        assertTrue(first.all(Char::isLowerCase))
        assertNotEquals(first, second)
    }

    @Test fun invalidConfigurationFails() {
        assertThrows(IllegalArgumentException::class.java) {
            PasswordGenerator.generate(PasswordOptions(length = 8))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PasswordGenerator.generate(
                PasswordOptions(lowercase = false, uppercase = false, digits = false, symbols = false)
            )
        }
    }
}
