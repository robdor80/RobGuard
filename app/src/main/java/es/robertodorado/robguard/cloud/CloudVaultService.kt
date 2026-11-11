package es.robertodorado.robguard.cloud

import android.util.Base64
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import es.robertodorado.robguard.devices.DeviceAuthorization
import es.robertodorado.robguard.vault.VaultEntry
import es.robertodorado.robguard.vault.VaultMerge
import es.robertodorado.robguard.vault.VaultSession
import es.robertodorado.robguard.vault.VaultStorage
import kotlinx.coroutines.tasks.await

/**
 * Sincronización explícita al abrir/guardar y bajo bloqueo de edición de UI.
 * Firestore solo ve strings Base64 de dos envolventes autenticadas AES-GCM.
 * No hay escritura ciega: compare-and-swap en transacción.
 */
internal class CloudVaultService(
    private val storage: VaultStorage,
    private val ownerAuth: GoogleOwnerAuth,
    private val deviceAuthorization: DeviceAuthorization
) {
    data class SyncResult(val entries: List<VaultEntry>, val revision: Long)
    class Conflict(val count: Int) : IllegalStateException(
        "Conflicto simultáneo en $count registro(s); no se sobrescribió nada"
    )

    private data class Remote(
        val revision: Long, val wrappedDek: ByteArray, val entries: ByteArray
    )

    private suspend fun authorized(): Pair<String, DocumentReference> {
        val uid = ownerAuth.authorizedUid ?: error("Cuenta Google no autorizada")
        val db: FirebaseFirestore = if (deviceAuthorization.enabled) {
            // En RG-03, comprobar el estado en SERVIDOR antes de cada operación.
            // Firestore Rules repite esta condición en cada lectura/escritura.
            deviceAuthorization.firestoreForVault()
        } else {
            // Compatibilidad RG-02 hasta la migración controlada en RG-04.
            ownerAuth.firestore ?: error("Firebase no disponible")
        }
        return uid to db.collection("vaults").document(uid)
    }

    /** Crear copia inicial solo si NO existe documento remoto. */
    suspend fun createBackup(session: VaultSession, recoveryCode: String): SyncResult {
        val (uid, ref) = authorized()
        check(storage.readCloudBaseline(session, uid) == null) {
            "Este dispositivo ya está sincronizado"
        }
        val local = session.records.toList()
        val wrapped = storage.wrapRecoveryKey(session, uid, recoveryCode)
        val encrypted = storage.encryptForCloud(session, uid, local)
        require(encrypted.size < MAX_BINARY_BYTES) { "Bóveda demasiado grande para Firestore" }
        ref.firestore.runTransaction { transaction ->
            check(!transaction.get(ref).exists()) {
                "Ya existe una copia remota; recupera la bóveda, no la sobrescribas"
            }
            transaction.set(ref, mapOf(
                "schema" to 2,
                "revision" to 1L,
                "wrappedDek" to Base64.encodeToString(wrapped, Base64.NO_WRAP),
                "entries" to Base64.encodeToString(encrypted, Base64.NO_WRAP)
            ))
            null
        }.await()
        // Si la aplicación se detiene aquí, el código de recuperación
        // ya permite acceder a la copia remota sin perder los datos locales.
        storage.writeCloudBaseline(session, uid, 1, wrapped, local)
        return SyncResult(local, 1)
    }

    suspend fun restore(session: VaultSession, recoveryCode: String): Pair<VaultSession, SyncResult> {
        val (uid, ref) = authorized()
        check(storage.readCloudBaseline(session, uid) == null) {
            "Esta bóveda ya tiene copia vinculada"
        }
        val remote = readRemote(ref) ?: error("No existe copia cifrada en Firebase")
        if (session.records.isNotEmpty()) {
            // Recuperación de una vinculación interrumpida: sólo se admite
            // si la DEK cifrada en Firebase coincide con la DEK local.
            // NUNCA reemplazar una bóveda con clave diferente.
            val candidate = es.robertodorado.robguard.vault.RecoveryCrypto.recoverDek(
                remote.wrappedDek, uid, recoveryCode
            )
            val localDek = session.secretKey().encoded
            val sameKey = try {
                java.security.MessageDigest.isEqual(candidate, localDek)
            } finally {
                candidate.fill(0)
                localDek.fill(0)
            }
            check(sameKey) { "La bóveda local utiliza otra clave: no se sobrescribirá" }
            val baseline = storage.decryptFromCloud(session, uid, remote.entries)
            storage.writeCloudBaseline(
                session, uid, remote.revision, remote.wrappedDek, baseline
            )
            return session to SyncResult(session.records, remote.revision)
        }
        val restored = storage.importRecoveredVault(
            session, uid, recoveryCode, remote.wrappedDek, remote.entries, remote.revision
        )
        return restored to SyncResult(restored.records, remote.revision)
    }

    suspend fun synchronize(
        session: VaultSession, preserveConflictsAsCopies: Boolean = false
    ): SyncResult {
        val (uid, ref) = authorized()
        val baseline = storage.readCloudBaseline(session, uid)
            ?: error("No hay vinculación cifrada. Crea o recupera la copia primero")
        val remote = readRemote(ref)
            ?: error("La copia remota ha desaparecido; no se sobrescribió")
        check(remote.wrappedDek.contentEquals(baseline.wrappedDek)) {
            "La envoltura de recuperación remota ha cambiado"
        }
        check(remote.revision >= baseline.revision) { "Revisión remota anterior a la local" }
        val remoteEntries = storage.decryptFromCloud(session, uid, remote.entries)
        val result = VaultMerge.merge(baseline.entries, session.records, remoteEntries)
        val merged = when {
            result.canPublish -> result.merged
            preserveConflictsAsCopies -> VaultMerge.resolveKeepingCopies(
                baseline.entries, session.records, remoteEntries
            )
            else -> throw Conflict(result.conflicts.size)
        }

        var revision = remote.revision
        if (merged != remoteEntries) {
            val cipher = storage.encryptForCloud(session, uid, merged)
            require(cipher.size < MAX_BINARY_BYTES) {
                "Bóveda demasiado grande para Firestore"
            }
            // Firestore reintenta transacciones en concurrencia. Si otro móvil
            // avanzó su revisión, ABORTAMOS sin reemplazar los datos.
            ref.firestore.runTransaction { transaction ->
                val latest = transaction.get(ref)
                check(latest.getLong("revision") == remote.revision &&
                    latest.getString("wrappedDek") ==
                    Base64.encodeToString(remote.wrappedDek, Base64.NO_WRAP)) {
                    "La nube cambió durante la sincronización; reintentar"
                }
                transaction.update(ref, mapOf(
                    "revision" to (remote.revision + 1L),
                    "entries" to Base64.encodeToString(cipher, Base64.NO_WRAP)
                ))
                null
            }.await()
            revision++
        }

        // Persistir primero el snapshot efectivo y DESPUÉS la baseline:
        // una interrupción no puede ocultar cambios locales sin sincronizar.
        if (merged != session.records) storage.save(session, merged)
        storage.writeCloudBaseline(session, uid, revision, remote.wrappedDek, merged)
        return SyncResult(merged, revision)
    }

    private suspend fun readRemote(ref: DocumentReference): Remote? {
        val snap = ref.get(Source.SERVER).await()
        if (!snap.exists()) return null
        require(snap.getLong("schema") == 2L) { "Esquema remoto incompatible" }
        val revision = requireNotNull(snap.getLong("revision"))
        require(revision > 0) { "Revisión remota inválida" }
        val wrapped = decodeCipher(requireNotNull(snap.getString("wrappedDek")), 2048)
        val data = decodeCipher(requireNotNull(snap.getString("entries")), MAX_BINARY_BYTES)
        return Remote(revision, wrapped, data)
    }

    private fun decodeCipher(value: String, limit: Int): ByteArray {
        require(value.length <= limit * 2) { "Campo cifrado remoto excesivo" }
        val decoded = Base64.decode(value, Base64.NO_WRAP)
        require(decoded.size in 32..limit) { "Envolvente remota no válida" }
        return decoded
    }

    companion object {
        // Firestore limita documentos a 1 MiB; conservar margen.
        private const val MAX_BINARY_BYTES = 570000
    }
}
