package es.robertodorado.robguard.vault

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import javax.crypto.AEADBadTagException

class VaultCryptoTest {
    @Test fun roundTripPreservesUtf8AndUsesRandomIv() {
        val key = VaultCrypto.dataKey(VaultCrypto.newDataKey())
        val clear = "Secreto: ñ, á, ☕".toByteArray()
        val a = VaultCrypto.seal(key, clear, "registros")
        val b = VaultCrypto.seal(key, clear, "registros")
        assertArrayEquals(clear, VaultCrypto.open(key, a, "registros"))
        assertArrayEquals(clear, VaultCrypto.open(key, b, "registros"))
        assertFalse(a.contentEquals(b))
    }

    @Test fun changesInCiphertextAreRejected() {
        val key = VaultCrypto.dataKey(VaultCrypto.newDataKey())
        val encrypted = VaultCrypto.seal(key, "contraseña".toByteArray(), "datos")
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()
        assertThrows(AEADBadTagException::class.java) {
            VaultCrypto.open(key, encrypted, "datos")
        }
    }

    @Test fun wrongPurposeAndWrongKeyAreRejected() {
        val key = VaultCrypto.dataKey(VaultCrypto.newDataKey())
        val encrypted = VaultCrypto.seal(key, byteArrayOf(1, 2, 3), "datos")
        assertThrows(AEADBadTagException::class.java) {
            VaultCrypto.open(key, encrypted, "otra-función")
        }
        assertThrows(AEADBadTagException::class.java) {
            VaultCrypto.open(VaultCrypto.dataKey(VaultCrypto.newDataKey()), encrypted, "datos")
        }
    }

    @Test fun truncatedAndUnknownFormatsAreRejected() {
        val key = VaultCrypto.dataKey(VaultCrypto.newDataKey())
        assertThrows(IllegalArgumentException::class.java) {
            VaultCrypto.open(key, byteArrayOf(1, 2), "datos")
        }
        assertThrows(IllegalArgumentException::class.java) {
            VaultCrypto.open(key, ByteArray(40), "datos")
        }
    }

    @Test fun sessionWipesKeyAndRejectsFurtherUse() {
        val session = VaultSession(VaultCrypto.newDataKey(), emptyList())
        session.close()
        assertFalse(session.isOpen)
        assertThrows(IllegalStateException::class.java) { session.secretKey() }
    }
}
