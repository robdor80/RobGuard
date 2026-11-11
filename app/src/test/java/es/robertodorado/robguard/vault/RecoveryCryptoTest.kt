package es.robertodorado.robguard.vault

import org.junit.Assert.*
import org.junit.Test
import javax.crypto.AEADBadTagException

class RecoveryCryptoTest {
    @Test fun keyCanBeRestoredWithTheSameAccountAndRecoveryCode() {
        val original = VaultCrypto.newDataKey()
        val code = RecoveryCrypto.generate()
        assertEquals(8, code.split("-").size)
        val wrapped = RecoveryCrypto.wrapDek(original, "uid-1", code)
        assertArrayEquals(original, RecoveryCrypto.recoverDek(wrapped, "uid-1", code))
        assertArrayEquals(original, RecoveryCrypto.recoverDek(wrapped, "uid-1", code.lowercase()))
    }

    @Test fun repeatedWrappingNeverReusesAnIv() {
        val dek = VaultCrypto.newDataKey()
        val code = RecoveryCrypto.generate()
        assertFalse(
            RecoveryCrypto.wrapDek(dek, "uid", code).contentEquals(
                RecoveryCrypto.wrapDek(dek, "uid", code)
            )
        )
    }

    @Test fun wrongCodeAccountOrModifiedCiphertextAreRejected() {
        val dek = VaultCrypto.newDataKey()
        val code = RecoveryCrypto.generate()
        val wrapped = RecoveryCrypto.wrapDek(dek, "uid", code)
        assertThrows(AEADBadTagException::class.java) {
            RecoveryCrypto.recoverDek(wrapped, "other", code)
        }
        assertThrows(AEADBadTagException::class.java) {
            RecoveryCrypto.recoverDek(wrapped, "uid", RecoveryCrypto.generate())
        }
        val corrupted = wrapped.copyOf()
        corrupted[corrupted.lastIndex] = (corrupted.last().toInt() xor 1).toByte()
        assertThrows(AEADBadTagException::class.java) {
            RecoveryCrypto.recoverDek(corrupted, "uid", code)
        }
    }

    @Test fun malformedRecoveryCodesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            RecoveryCrypto.wrapDek(VaultCrypto.newDataKey(), "uid", "abc")
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecoveryCrypto.wrapDek(VaultCrypto.newDataKey(), "uid", "!" .repeat(32))
        }
    }
}
