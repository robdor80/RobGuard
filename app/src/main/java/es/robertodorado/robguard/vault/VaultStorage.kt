package es.robertodorado.robguard.vault

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
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
    private val cloudStateFile = AtomicFile(File(directory, "cloud-state.rg02"))
    private val restoreJournalFile = AtomicFile(File(directory, "restore-journal.rg02"))

    /**
     * Preparar la KEK ANTES del diálogo de autenticación: así el token concedido
     * por Android corresponde a una clave que ya existe. Nunca sobrescribir
     * una bóveda ni sustituir una clave existente.
     */
    fun prepareForAuthentication() {
        checkDeviceSecurity()
        rollbackInterruptedRestore()
        val hasKeyFile = wrappedKeyFile.baseFile.exists()
        val hasRecords = entriesFile.baseFile.exists()
        check(hasKeyFile == hasRecords) {
            "Bóveda incompleta. No se han tocado los ficheros."
        }
        if (hasKeyFile) return
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias(ALIAS)) {
            createDeviceKey()
        }
    }

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
        // La clave de este dispositivo debe haberse creado ANTES del prompt.
        // Si un intento anterior falló antes de escribir datos, se reutiliza.
        val kek = existingDeviceKey()
        val dek = VaultCrypto.newDataKey()
        try {
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
        entries.forEach { it.validate() }
        val plaintext = encode(entries)
        try {
            val ciphertext = VaultCrypto.seal(session.secretKey(), plaintext, DATA_PURPOSE)
            atomicWrite(entriesFile, ciphertext)
            session.records = entries.toList()
        } finally {
            plaintext.fill(0)
        }
    }


    /**
     * Snapshot íntegro RG01 cifrado antes de salir del dispositivo.
     * AAD separada de la de archivos locales y ligada a Firebase UID.
     */
    fun encryptForCloud(session: VaultSession, uid: String, entries: List<VaultEntry>): ByteArray {
        check(session.isOpen) { "Bóveda bloqueada" }
        val clear = encode(entries)
        return try {
            VaultCrypto.seal(session.secretKey(), clear, CLOUD_DATA_PURPOSE + uid)
        } finally {
            clear.fill(0)
        }
    }

    fun decryptFromCloud(session: VaultSession, uid: String, cipher: ByteArray): List<VaultEntry> {
        val clear = VaultCrypto.open(session.secretKey(), cipher, CLOUD_DATA_PURPOSE + uid)
        return try {
            decode(clear)
        } finally {
            clear.fill(0)
        }
    }

    fun wrapRecoveryKey(session: VaultSession, uid: String, code: String): ByteArray {
        val dek = session.secretKey().encoded
        return try {
            RecoveryCrypto.wrapDek(dek, uid, code)
        } finally {
            dek.fill(0)
        }
    }

    data class CloudBaseline(
        val revision: Long,
        val wrappedDek: ByteArray,
        val entries: List<VaultEntry>
    )

    fun readCloudBaseline(session: VaultSession, uid: String): CloudBaseline? {
        check(session.isOpen) { "Bóveda bloqueada" }
        if (!cloudStateFile.baseFile.exists()) return null
        val cipher = cloudStateFile.readFully()
        val clear = VaultCrypto.open(session.secretKey(), cipher, CLOUD_STATE_PURPOSE + uid)
        return try {
            val root = JSONObject(String(clear, Charsets.UTF_8))
            require(root.getInt("schema") == 2 && root.getString("uid") == uid) {
                "Cuenta o esquema de sincronización incompatible"
            }
            val revision = root.getLong("revision")
            require(revision > 0) { "Revisión de nube inválida" }
            val wrapped = Base64.decode(root.getString("wrappedDek"), Base64.NO_WRAP)
            val baseline = Base64.decode(root.getString("baseline"), Base64.NO_WRAP)
            val records = try { decode(baseline) } finally { baseline.fill(0) }
            CloudBaseline(revision, wrapped, records)
        } finally {
            clear.fill(0)
        }
    }

    fun writeCloudBaseline(
        session: VaultSession, uid: String, revision: Long,
        wrappedDek: ByteArray, entries: List<VaultEntry>
    ) {
        check(session.isOpen && revision > 0) { "Estado de sincronización inválido" }
        val serialized = encode(entries)
        val clear = try {
            JSONObject()
                .put("schema", 2).put("uid", uid).put("revision", revision)
                .put("wrappedDek", Base64.encodeToString(wrappedDek, Base64.NO_WRAP))
                .put("baseline", Base64.encodeToString(serialized, Base64.NO_WRAP))
                .toString().toByteArray(Charsets.UTF_8)
        } finally {
            serialized.fill(0)
        }
        try {
            atomicWrite(
                cloudStateFile,
                VaultCrypto.seal(session.secretKey(), clear, CLOUD_STATE_PURPOSE + uid)
            )
        } finally {
            clear.fill(0)
        }
    }

    /**
     * Restauración segura solo sobre una bóveda local VACÍA.
     * Antes de sustituir dos ficheros RG01 se escribe un diario con copias
     * CIFRADAS; si hay caída de energía el arranque deshace la operación.
     */
    fun importRecoveredVault(
        oldSession: VaultSession, uid: String, code: String,
        wrappedDek: ByteArray, cloudCipher: ByteArray, revision: Long
    ): VaultSession {
        check(oldSession.isOpen && oldSession.records.isEmpty() &&
            !cloudStateFile.baseFile.exists() && revision > 0) {
            "No se sobrescribe una bóveda local existente"
        }
        val dek = RecoveryCrypto.recoverDek(wrappedDek, uid, code)
        try {
            val clear = VaultCrypto.open(
                VaultCrypto.dataKey(dek), cloudCipher, CLOUD_DATA_PURPOSE + uid
            )
            val recovered = try { decode(clear) } finally { clear.fill(0) }
            val localClear = encode(recovered)
            val localCipher = try {
                VaultCrypto.seal(VaultCrypto.dataKey(dek), localClear, DATA_PURPOSE)
            } finally {
                localClear.fill(0)
            }
            // Requiere autenticación Keystore reciente. Si expiró, falla
            // sin escribir nada y el usuario vuelve a autenticar Android.
            val localWrapped = VaultCrypto.seal(existingDeviceKey(), dek, KEY_PURPOSE)
            val oldKey = wrappedKeyFile.readFully()
            val oldEntries = entriesFile.readFully()
            val journal = JSONObject()
                .put("key", Base64.encodeToString(oldKey, Base64.NO_WRAP))
                .put("entries", Base64.encodeToString(oldEntries, Base64.NO_WRAP))
                .toString().toByteArray(Charsets.UTF_8)
            atomicWrite(restoreJournalFile, journal)
            val newSession = VaultSession(dek, recovered)
            try {
                atomicWrite(wrappedKeyFile, localWrapped)
                atomicWrite(entriesFile, localCipher)
                writeCloudBaseline(newSession, uid, revision, wrappedDek, recovered)
                restoreJournalFile.delete()
                oldSession.close()
                return newSession
            } catch (error: Exception) {
                newSession.close()
                rollbackInterruptedRestore()
                throw error
            } finally {
                journal.fill(0)
            }
        } finally {
            dek.fill(0)
        }
    }

    private fun rollbackInterruptedRestore() {
        if (!restoreJournalFile.baseFile.exists()) return
        val journal = JSONObject(String(restoreJournalFile.readFully(), Charsets.UTF_8))
        val previousKey = Base64.decode(journal.getString("key"), Base64.NO_WRAP)
        val previousEntries = Base64.decode(journal.getString("entries"), Base64.NO_WRAP)
        try {
            atomicWrite(wrappedKeyFile, previousKey)
            atomicWrite(entriesFile, previousEntries)
            cloudStateFile.delete()
            restoreJournalFile.delete()
        } finally {
            previousKey.fill(0)
            previousEntries.fill(0)
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
        private const val CLOUD_DATA_PURPOSE = "RobGuard/cloud/entries/v1/"
        private const val CLOUD_STATE_PURPOSE = "RobGuard/cloud/baseline/v1/"
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
