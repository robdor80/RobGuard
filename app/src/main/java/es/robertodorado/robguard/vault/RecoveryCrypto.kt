package es.robertodorado.robguard.vault

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.spec.SecretKeySpec

/**
 * Clave de recuperación aleatoria de 160 bits: 32 caracteres Base32 RFC 4648.
 *
 * NO es una contraseña elegida por el usuario: no necesita un KDF de contraseñas
 * lentas porque la entropía proviene exclusivamente de SecureRandom.
 * Google/Firebase jamás recibe esta clave ni el DEK en claro.
 */
internal object RecoveryCrypto {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private const val CODE_BYTES = 20
    private const val CODE_CHARS = 32
    private const val PREFIX = "RobGuard/recovery-key/v1/"
    private const val WRAP_PURPOSE = "RobGuard/DEK/recovery/v1/"

    fun generate(): String {
        val raw = ByteArray(CODE_BYTES).also(SecureRandom()::nextBytes)
        return try {
            encode(raw).chunked(4).joinToString("-")
        } finally {
            raw.fill(0)
        }
    }

    fun wrapDek(dek: ByteArray, uid: String, recoveryCode: String): ByteArray {
        require(dek.size == 32) { "DEK no válida" }
        return withRecoveryKey(uid, recoveryCode) { key ->
            VaultCrypto.seal(key, dek, WRAP_PURPOSE + uid)
        }
    }

    fun recoverDek(wrapped: ByteArray, uid: String, recoveryCode: String): ByteArray {
        val recovered = withRecoveryKey(uid, recoveryCode) { key ->
            VaultCrypto.open(key, wrapped, WRAP_PURPOSE + uid)
        }
        if (recovered.size != 32) {
            recovered.fill(0)
            throw IllegalArgumentException("DEK recuperada no válida")
        }
        return recovered
    }

    private inline fun <T> withRecoveryKey(
        uid: String, code: String, block: (SecretKeySpec) -> T
    ): T {
        require(uid.isNotBlank() && uid.length <= 128) { "UID no válido" }
        val raw = decode(code)
        val domain = (PREFIX + uid + "/").toByteArray(Charsets.UTF_8)
        val digest = try {
            MessageDigest.getInstance("SHA-256").apply {
                update(domain)
                update(raw)
            }.digest()
        } finally {
            raw.fill(0)
        }
        return try {
            block(SecretKeySpec(digest, "AES"))
        } finally {
            digest.fill(0)
        }
    }

    private fun encode(data: ByteArray): String {
        val out = StringBuilder(CODE_CHARS)
        var buffer = 0
        var bits = 0
        for (byte in data) {
            buffer = (buffer shl 8) or (byte.toInt() and 255)
            bits += 8
            while (bits >= 5) {
                bits -= 5
                out.append(ALPHABET[(buffer ushr bits) and 31])
            }
        }
        check(bits == 0)
        return out.toString()
    }

    private fun decode(input: String): ByteArray {
        val normalized = input.uppercase(java.util.Locale.ROOT)
            .replace("-", "").replace(" ", "").replace("\n", "")
        require(normalized.length == CODE_CHARS) { "Código de recuperación no válido" }
        val out = ByteArray(CODE_BYTES)
        var buffer = 0
        var bits = 0
        var cursor = 0
        for (char in normalized) {
            val digit = ALPHABET.indexOf(char)
            require(digit >= 0) { "Código de recuperación no válido" }
            buffer = (buffer shl 5) or digit
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out[cursor++] = ((buffer ushr bits) and 255).toByte()
            }
        }
        check(cursor == CODE_BYTES && bits == 0)
        return out
    }
}
