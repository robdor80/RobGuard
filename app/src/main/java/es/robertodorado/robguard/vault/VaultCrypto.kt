package es.robertodorado.robguard.vault

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Envolvente RG01: cabecera de 4 bytes + IV aleatorio de 12 bytes +
 * ciphertext y etiqueta de integridad GCM de 128 bits.
 */
internal object VaultCrypto {
    private val magic = byteArrayOf(0x52, 0x47, 0x30, 0x31)
    private const val IV_SIZE = 12
    private const val TAG_BITS = 128
    private val random = SecureRandom()

    fun newDataKey(): ByteArray = ByteArray(32).also(random::nextBytes)

    fun seal(key: SecretKey, clear: ByteArray, purpose: String): ByteArray {
        val iv = ByteArray(IV_SIZE).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(purpose.toByteArray(Charsets.UTF_8))
        return magic + iv + cipher.doFinal(clear)
    }

    fun open(key: SecretKey, envelope: ByteArray, purpose: String): ByteArray {
        require(envelope.size >= magic.size + IV_SIZE + TAG_BITS / 8) {
            "Envolvente cifrada truncada"
        }
        require(envelope.copyOfRange(0, magic.size).contentEquals(magic)) {
            "Versión de cifrado desconocida"
        }
        val iv = envelope.copyOfRange(magic.size, magic.size + IV_SIZE)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(purpose.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(envelope, magic.size + IV_SIZE, envelope.size - magic.size - IV_SIZE)
    }

    fun dataKey(bytes: ByteArray): SecretKey {
        require(bytes.size == 32) { "La clave de datos debe tener 256 bits" }
        return SecretKeySpec(bytes, "AES")
    }
}
