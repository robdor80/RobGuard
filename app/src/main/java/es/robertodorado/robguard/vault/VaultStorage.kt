package es.robertodorado.robguard.vault

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Datos cifrados AES-256-GCM y escritura atómica, sin SharedPreferences ni nube.
 * La DEK aleatoria se cifra con una KEK no exportable de Android Keystore.
 * La DEK puede recibir en RG-02 una envoltura de recuperación independiente.
 */
internal class VaultStorage(context: Context) {
    private val appContext = context.applicationContext
    private val directory = File(appContext.filesDir, "vault").apply {
        check(isDirectory || mkdirs()) { "No se pudo crear el directorio privado" }
    }
    private val wrappedKeyFile = AtomicFile(File(directory, "key.rg01"))
    private val entriesFile = AtomicFile(File(directory, "entries.rg01"))

    fun openAfterAuthentication(): VaultSession {
        checkDeviceSecurity()
        val keyExists = wrappedKeyFile.baseFile.exists()
        val recordsExist = entriesFile.baseFile.exists()
        check(keyExists == recordsExist) {
            "Bóveda incompleta. No se creará una nueva sobre datos existentes."
        }
        if (!keyExists) return createEmptyVault()

        val keyBytes = VaultCrypto.open(existingDeviceKey(), wrappedKeyFile.readFully(), KEY_PURPOSE)
        try {
            require(keyBytes.size == 32) { "Clave de bóveda no válida" }
            val plaintext = VaultCrypto.open(
                VaultCrypto.dataKey(keyBytes), entriesFile.readFully(), DATA_PURPOSE
            )
            val entries = try {
                decode(plaintext)
            } finally {
                plaintext.fill(0)
            }
            return VaultSession(keyBytes, entries)
        } finally {
            keyBytes.fill(0)
        }
    }

    private fun createEmptyVault(): VaultSession {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        check(!keyStore.containsAlias(ALIAS)) {
            "Hay una clave anterior sin ficheros. No se reiniciará automáticamente."
        }
        val dek = VaultCrypto.newDataKey()
        try {
            val kek = createDeviceKey()
            val wrapped = VaultCrypto.seal(kek, dek, KEY_PURPOSE)
            val serialized = encode(emptyList())
            try {
                val ciphertext = VaultCrypto.seal(VaultCrypto.dataKey(dek), serialized, DATA_PURPOSE)
                atomicWrite(wrappedKeyFile, wrapped)
                atomicWrite(entriesFile, ciphertext)
            } finally {
                serialized.fill(0)
            }
            return VaultSession(dek, emptyList())
        } finally {
            dek.fill(0)
        }
    }

    fun save(session: VaultSession, entries: List<VaultEntry>) {
        check(session.isOpen) { "Bóveda bloqueada" }
        require(entries.size <= 10000) { "Límite de entradas excedido" }
        require(entries.map { it.id }.toSet().size == entries.size) { "Entradas duplicadas" }
        entries.forEach(VaultEntry::validate)
        val plaintext = encode(entries)
        try {
            val ciphertext = VaultCrypto.seal(session.secretKey(), plaintext, DATA_PURPOSE)
            atomicWrite(entriesFile, ciphertext)
            session.records = entries.toList()
        } finally {
            plaintext.fill(0)
        }
    }

    private fun checkDeviceSecurity() {
        val keyguard = appContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        check(keyguard.isDeviceSecure) {
            "Configura un PIN, patrón o contraseña de bloqueo en Android."
        }
    }

    private fun existingDeviceKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey(ALIAS, null) as? SecretKey)
            ?: error("No está la clave de Android Keystore. No se ha borrado la bóveda.")
    }

    private fun createDeviceKey(): SecretKey {
        val builder = KeyGenParameterSpec.Builder(
            ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setRandomizedEncryptionRequired(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(
                60, KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(60)
        }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply { init(builder.build()) }.generateKey()
    }

    private fun atomicWrite(file: AtomicFile, content: ByteArray) {
        val stream = file.startWrite()
        try {
            stream.write(content)
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }

    private fun encode(entries: List<VaultEntry>): ByteArray {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(JSONObject()
                .put("id", entry.id).put("title", entry.title)
                .put("username", entry.username).put("password", entry.password)
                .put("website", entry.website).put("category", entry.category)
                .put("notes", entry.notes).put("createdAt", entry.createdAt)
                .put("updatedAt", entry.updatedAt))
        }
        return JSONObject().put("schema", 1).put("entries", array).toString()
            .toByteArray(Charsets.UTF_8)
    }

    private fun decode(content: ByteArray): List<VaultEntry> {
        val root = JSONObject(String(content, Charsets.UTF_8))
        require(root.getInt("schema") == 1) { "Esquema de bóveda no compatible" }
        val array = root.getJSONArray("entries")
        require(array.length() <= 10000) { "Demasiados registros" }
        val entries = (0 until array.length()).map { index ->
            val obj = array.getJSONObject(index)
            VaultEntry(
                id = obj.getString("id"), title = obj.getString("title"),
                username = obj.getString("username"), password = obj.getString("password"),
                website = obj.getString("website"), category = obj.getString("category"),
                notes = obj.getString("notes"), createdAt = obj.getLong("createdAt"),
                updatedAt = obj.getLong("updatedAt")
            ).validate()
        }
        require(entries.map { it.id }.toSet().size == entries.size) { "IDs duplicados" }
        return entries
    }

    companion object {
        private const val ALIAS = "es.robertodorado.robguard.local.kek.v1"
        private const val KEY_PURPOSE = "RobGuard/DEK/local/v1"
        private const val DATA_PURPOSE = "RobGuard/entries/v1"
    }
}

/** Sesión privada: se borrará la clave en memoria al bloquear la pantalla. */
internal class VaultSession(key: ByteArray, initialRecords: List<VaultEntry>) {
    private val keyBytes = key.copyOf()
    internal var records: List<VaultEntry> = initialRecords
        set(value) {
            check(isOpen) { "Sesión cerrada" }
            field = value
        }
    var isOpen: Boolean = true
        private set

    internal fun secretKey(): SecretKey {
        check(isOpen) { "Sesión cerrada" }
        return VaultCrypto.dataKey(keyBytes)
    }

    fun close() {
        if (!isOpen) return
        keyBytes.fill(0)
        records = emptyList()
        isOpen = false
    }
}
