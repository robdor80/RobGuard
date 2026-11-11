package es.robertodorado.robguard.devices

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * Identidad de INSTALACIÓN, distinta del Firebase owner UID.
 *
 * La clave de firma se crea en Android Keystore y no se exporta. El ID
 * aleatorio NO sirve por sí solo para conceder acceso a la nube.
 * El backend RG-03 deberá validar la firma de un reto fresco, su
 * caducidad y autorización vigente antes de permitir cada operación.
 *
 * Esta clase no comunica nada a Firebase; P01 aislada hasta P02.
 */
internal class DeviceIdentity(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "rg03_installation", Context.MODE_PRIVATE
    )

    private val mutex = Any()

    fun installationId(): String = synchronized(mutex) {
        val stored = prefs.getString(ID_KEY, null)
        if (stored != null) {
            check(stored.matches(Regex("[A-Za-z0-9_-]{16,128}"))) {
                "Identidad local dañada: no se ha regenerado"
            }
            check(readSigningKey() != null) {
                "Falta clave de la instalación: requiere nueva vinculación segura"
            }
            return@synchronized stored
        }
        // Si se interrumpió la creación entre generar Keystore y
        // guardar preferencias, se reutiliza esa clave huérfana sin
        // asociarla a autorización remota anterior.
        if (readSigningKey() == null) generateSigningKey()
        val bytes = ByteArray(24).also(SecureRandom()::nextBytes)
        val newId = try {
            Base64.encodeToString(
                bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
            )
        } finally {
            bytes.fill(0)
        }
        check(prefs.edit().putString(ID_KEY, newId).commit()) {
            "No se pudo persistir el identificador de instalación"
        }
        newId
    }

    fun publicKeyX509(): ByteArray = synchronized(mutex) {
        installationId()
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val encoded = store.getCertificate(ALIAS)?.publicKey?.encoded
        requireNotNull(encoded) { "Certificado de instalación no disponible" }.copyOf()
    }

    /**
     * El payload de reto lo genera el servidor, nunca el cliente:
     * debe incluir nonce aleatorio único, acción, owner UID, device ID
     * y vencimiento; el servidor impide replays y valida todo.
     */
    fun signServerChallenge(challenge: ByteArray): ByteArray = synchronized(mutex) {
        val id = installationId()
        val key = requireNotNull(readSigningKey()) {
            "La clave de firma local ha desaparecido"
        }
        val transcript = DeviceChallengeTranscript.make(id, challenge)
        try {
            Signature.getInstance("SHA256withECDSA").run {
                initSign(key)
                update(transcript)
                sign()
            }
        } finally {
            transcript.fill(0)
        }
    }

    fun proposedDisplayName(): String {
        // Exclusivamente para una etiqueta visual; no es autenticación
        // ni identificador estable de hardware.
        return listOf(Build.MANUFACTURER, Build.MODEL)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .take(64)
            .ifBlank { "Dispositivo Android" }
    }

    private fun readSigningKey(): PrivateKey? {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return store.getKey(ALIAS, null) as? PrivateKey
    }

    private fun generateSigningKey() {
        val spec = KeyGenParameterSpec.Builder(
            ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(false)
            .build()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
            .apply { initialize(spec) }
            .generateKeyPair()
    }

    companion object {
        private const val ALIAS = "es.robertodorado.robguard.device.sign.v1"
        private const val ID_KEY = "installation_id"
    }
}

/**
 * Canonicalización compartida en tests. NO implica que el servidor confíe
 * en la declaración de identidad: primero debe validar el enrollment.
 */
internal object DeviceChallengeTranscript {
    fun make(installationId: String, challenge: ByteArray): ByteArray {
        require(installationId.matches(Regex("[A-Za-z0-9_-]{16,128}")))
        require(challenge.size in 16..512) { "Reto inválido" }
        return "RobGuard/device-challenge/v1\u0000".toByteArray(Charsets.UTF_8) +
            installationId.toByteArray(Charsets.US_ASCII) +
            byteArrayOf(0) + challenge
    }
}
