package es.robertodorado.robguard.devices

import android.content.Context
import android.util.Base64
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import es.robertodorado.robguard.R
import es.robertodorado.robguard.cloud.GoogleOwnerAuth
import kotlinx.coroutines.tasks.await

/**
 * P02: autenticación por instalación, INDEPENDIENTE del Google UID de RG-02.
 *
 * Firebase Auth anónimo produce un UID propio por instalación. Firestore Rules
 * es la autoridad: /vaults/{ownerUid} solo autoriza un UID anónimo cuyo
 * documento /devices/{deviceUid} siga autorizado en servidor.
 *
 * No confundir Anonymous Auth con una clave ligada físicamente al hardware:
 * el token de sesión es una credencial portable si un atacante extrae los
 * datos privados de la aplicación. La clave Android Keystore de P01 es defensa
 * futura para un backend de retos firmados, pero Firestore Rules no verifica
 * ECDSA. La revocación corta cualquier copia de ese mismo UID.
 */
internal class DeviceAuthorization(
    context: Context,
    private val owner: GoogleOwnerAuth
) {
    private val appContext = context.applicationContext
    private val identity by lazy { DeviceIdentity(appContext) }
    private val expectedOwnerUid: String =
        appContext.getString(R.string.rg02_owner_uid)

    /** Sin despliegue de reglas RG-03: mantener RG-02 operativo hasta RG-04. */
    val enabled: Boolean = appContext.resources.getBoolean(R.bool.rg03_device_access_enabled)

    private val app: FirebaseApp by lazy {
        check(owner.isConfigured) { "Firebase RG-02 no configurado" }
        FirebaseApp.getApps(appContext).firstOrNull { it.name == DEVICE_APP }
            ?: FirebaseApp.initializeApp(
                appContext,
                FirebaseOptions.Builder()
                    .setApiKey(appContext.getString(R.string.rg02_firebase_api_key))
                    .setApplicationId(appContext.getString(R.string.rg02_firebase_app_id))
                    .setProjectId(appContext.getString(R.string.rg02_firebase_project_id))
                    .build(),
                DEVICE_APP
            )
    }
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance(app) }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance(app) }

    data class DeviceInfo(
        val authUid: String,
        val installationId: String,
        val alias: String,
        val status: DeviceStatus,
        val createdAtMillis: Long,
        val lastSeenAtMillis: Long?,
        val wipeRequested: Boolean,
        val isThisDevice: Boolean
    )

    val thisDeviceUid: String?
        get() = if (enabled) auth.currentUser?.takeIf { it.isAnonymous }?.uid else null

    private suspend fun anonymousUid(): String {
        val user = auth.currentUser ?: auth.signInAnonymously().await().user
        check(user != null && user.isAnonymous) { "Sesión de dispositivo inesperada" }
        return user.uid
    }

    /**
     * Solo el login GOOGLE del propietario puede registrar una propuesta;
     * la creación nunca otorga permisos sobre la bóveda.
     */
    suspend fun registerIfNeeded(): DeviceInfo {
        check(enabled) { "RG-03 aún no activada" }
        val ownerUid = owner.authorizedUid ?: error("Conecta la cuenta Google propietaria")
        val deviceUid = anonymousUid()
        val ref = db.collection("devices").document(deviceUid)
        var snap = ref.get(Source.SERVER).await()
        if (!snap.exists()) {
            val data = mapOf(
                "schema" to 3,
                "deviceUid" to deviceUid,
                "ownerUid" to ownerUid,
                "installationId" to identity.installationId(),
                "alias" to identity.proposedDisplayName(),
                "publicKey" to Base64.encodeToString(
                    identity.publicKeyX509(), Base64.NO_WRAP
                ),
                "status" to "pending",
                "wipeRequested" to false,
                "createdAt" to FieldValue.serverTimestamp()
            )
            // Transacción bajo GOOGLE owner, nunca con credencial anónima.
            // Evita que se sobrescriba una autorización preexistente.
            val ownerDb = owner.firestore ?: error("Cuenta Google no autorizada")
            val ownerRef = ownerDb.collection("devices").document(deviceUid)
            ownerDb.runTransaction { tx ->
                check(!tx.get(ownerRef).exists()) {
                    "La identidad ya está registrada, no se sobrescribirá"
                }
                tx.set(ownerRef, data)
                null
            }.await()
            snap = ref.get(Source.SERVER).await()
        }
        return readDeviceInfo(snap, deviceUid).also {
            check(it.installationId == identity.installationId()) {
                "Identidad de instalación incoherente; no generar otra cuenta automáticamente"
            }
        }
    }

    suspend fun refreshMine(): DeviceInfo {
        check(enabled) { "RG-03 no activada" }
        val uid = anonymousUid()
        val snap = db.collection("devices").document(uid).get(Source.SERVER).await()
        check(snap.exists()) { "Dispositivo sin registro: necesita nueva aprobación" }
        val info = readDeviceInfo(snap, uid)
        check(info.installationId == identity.installationId()) {
            "Registro pertenece a otra instalación"
        }
        return info
    }

    /** Consulta siempre servidor, NO caché. Los permisos reales están en rules. */
    suspend fun firestoreForVault(): FirebaseFirestore {
        val info = refreshMine()
        check(info.status == DeviceStatus.AUTHORIZED) {
            "Dispositivo no autorizado; la copia local sigue cifrada"
        }
        return db
    }

    suspend fun listDevices(): List<DeviceInfo> {
        firestoreForVault()
        val uid = anonymousUid()
        return db.collection("devices").get(Source.SERVER).await().documents
            .map { readDeviceInfo(it, uid) }
            .sortedWith(compareByDescending<DeviceInfo> { it.isThisDevice }
                .thenByDescending { it.createdAtMillis })
    }

    suspend fun approveDevice(targetUid: String) {
        val currentUid = anonymousUid()
        check(targetUid != currentUid) { "No puedes aprobar este mismo dispositivo" }
        check(targetUid.isNotBlank() && '/' !in targetUid)
        firestoreForVault()
        db.collection("devices").document(targetUid).update(
            mapOf(
                "status" to "authorized",
                "approvedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        // El servidor verifica que el aprobador siga AUTORIZADO y el
        // destino estuviera PENDIENTE. No hay elevación por cliente.
    }

    suspend fun revokeDevice(targetUid: String, wipeRequested: Boolean) {
        val currentUid = anonymousUid()
        check(targetUid != currentUid) { "No se permite revocar el propio dispositivo" }
        check(targetUid.isNotBlank() && '/' !in targetUid)
        firestoreForVault()
        db.collection("devices").document(targetUid).update(
            mapOf(
                "status" to "revoked",
                "wipeRequested" to wipeRequested,
                "revokedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        // No borrar /vaults: la copia cifrada del propietario sigue intacta.
    }

    suspend fun recordHeartbeat() {
        val info = refreshMine()
        if (info.status != DeviceStatus.AUTHORIZED) return
        db.collection("devices").document(info.authUid).update(
            "lastSeenAt", FieldValue.serverTimestamp()
        ).await()
    }

    /**
     * El cliente primero ejecuta el borrado criptográfico local y SOLO
     * después confirma al servidor. No es una confirmación de destrucción
     * física de la memoria flash.
     */
    suspend fun acknowledgeWipe() {
        val info = refreshMine()
        check(info.status == DeviceStatus.REVOKED && info.wipeRequested) {
            "No hay orden de borrado remoto"
        }
        db.collection("devices").document(info.authUid).update(
            mapOf(
                "status" to "wipe_confirmed",
                "wipedAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    private fun readDeviceInfo(
        snap: com.google.firebase.firestore.DocumentSnapshot,
        myUid: String
    ): DeviceInfo {
        check(snap.exists() && snap.getLong("schema") == 3L) {
            "Registro de instalación incompatible"
        }
        // La revocación y su orden de borrado deben poder procesarse incluso
        // si el usuario ha desconectado Google en el dispositivo perdido.
        check(snap.getString("ownerUid") == expectedOwnerUid) {
            "El registro no corresponde a la cuenta propietaria"
        }
        return DeviceInfo(
            authUid = snap.id,
            installationId = requireNotNull(snap.getString("installationId")),
            alias = requireNotNull(snap.getString("alias")),
            status = when (snap.getString("status")) {
                "pending" -> DeviceStatus.PENDING
                "authorized" -> DeviceStatus.AUTHORIZED
                "revoked" -> DeviceStatus.REVOKED
                "wipe_confirmed" -> DeviceStatus.WIPE_CONFIRMED
                else -> error("Estado de autorización desconocido")
            },
            createdAtMillis = requireNotNull(snap.getTimestamp("createdAt")).toDate().time,
            lastSeenAtMillis = snap.getTimestamp("lastSeenAt")?.toDate()?.time,
            wipeRequested = snap.getBoolean("wipeRequested") ?: false,
            isThisDevice = snap.id == myUid
        )
    }

    companion object {
        private const val DEVICE_APP = "RobGuardDevice"
    }
}
