package es.robertodorado.robguard

import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import es.robertodorado.robguard.cloud.GoogleOwnerAuth
import es.robertodorado.robguard.cloud.CloudVaultService
import es.robertodorado.robguard.devices.DeviceAuthorization
import es.robertodorado.robguard.devices.DeviceStatus
import es.robertodorado.robguard.vault.RecoveryCrypto
import kotlinx.coroutines.launch
import es.robertodorado.robguard.vault.VaultEntry
import es.robertodorado.robguard.vault.VaultSession
import es.robertodorado.robguard.vault.VaultStorage
import java.util.UUID

class MainActivity : FragmentActivity() {
    private val storage by lazy { VaultStorage(this) }
    private val ownerAuth by lazy { GoogleOwnerAuth(this) }
    private val deviceAuthorization by lazy { DeviceAuthorization(this, ownerAuth) }
    private val cloud by lazy { CloudVaultService(storage, ownerAuth, deviceAuthorization) }
    private val connectivity by lazy {
        getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
    private var networkCallbackRegistered = false
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                runOnUiThread {
                    if (started) {
                        if (deviceAuthorization.enabled) refreshDeviceAccess()
                        if (unlocked) syncCloud()
                    }
                }
            }
        }
    }
    private var cloudPaired by mutableStateOf(false)
    private var cloudConflict by mutableStateOf(false)
    private var cloudBusy by mutableStateOf(false)
    private var cloudStatus by mutableStateOf("Sólo almacenamiento local")
    private var recoveryCode by mutableStateOf<String?>(null)
    private var accountLabel by mutableStateOf<String?>(null)
    private var deviceSelf by mutableStateOf<DeviceAuthorization.DeviceInfo?>(null)
    private var deviceList by mutableStateOf<List<DeviceAuthorization.DeviceInfo>>(emptyList())
    private var deviceMessage by mutableStateOf("RG-03 pendiente de activar en Release")
    private var deviceBusy by mutableStateOf(false)
    private var deviceCheckInProgress = false
    private var session: VaultSession? = null
    private var unlocked by mutableStateOf(false)
    private var records by mutableStateOf<List<VaultEntry>>(emptyList())
    private var message by mutableStateOf<String?>(null)
    private val handler = Handler(Looper.getMainLooper())
    private val idleTimeout = Runnable { lockVault() }
    private var clipboardToken: String? = null
    private var clipboardExpiryMs: Long = 0L
    private var started = false
    private var authorizedWhilePausedAtMs: Long? = null
    private val legacyCredentialLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && started) openAuthorizedVault()
        else if (started) message = "No se ha completado la autenticación."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        accountLabel = ownerAuth.authorizedEmail
        setContent {
            // Pantalla BLOQUEADA sin secretos: permite capturas para soporte.
            // Bóveda abierta: bloquear capturas y vista Recientes.
            SideEffect {
                if (unlocked) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            RobGuardApp(
                unlocked = unlocked,
                entries = records,
                message = message,
                onUnlock = ::authenticate,
                onLock = ::lockVault,
                onSave = ::saveEntry,
                onDelete = ::deleteEntry,
                onCopy = ::copyPassword,
                googleConfigured = ownerAuth.isConfigured,
                googleAccount = accountLabel,
                onConnectGoogle = ::connectGoogle,
                onDisconnectGoogle = ::disconnectGoogle,
                cloudPaired = cloudPaired,
                cloudConflict = cloudConflict,
                onResolveConflicts = ::resolveCloudConflicts,
                cloudBusy = cloudBusy,
                cloudStatus = cloudStatus,
                recoveryCode = recoveryCode,
                onPrepareBackup = ::prepareCloudBackup,
                onConfirmBackup = ::confirmCloudBackup,
                onCancelBackup = { recoveryCode = null },
                onRecover = ::recoverFromCloud,
                onSyncCloud = ::syncCloud,
                deviceAccessEnabled = deviceAuthorization.enabled,
                currentDevice = deviceSelf,
                deviceList = deviceList,
                deviceBusy = deviceBusy,
                deviceMessage = deviceMessage,
                onRefreshDevices = ::loadDeviceList,
                onApproveDevice = ::approveDevice,
                onRevokeDevice = ::revokeDevice
            )
        }
    }

    override fun onStart() {
        // El resultado de PIN/patrón puede despacharse dentro de super.onStart().
        started = true
        super.onStart()
        accountLabel = ownerAuth.authorizedEmail
        if (deviceAuthorization.enabled) refreshDeviceAccess()
        try {
            connectivity.registerDefaultNetworkCallback(networkCallback)
            networkCallbackRegistered = true
        } catch (_: SecurityException) {
            networkCallbackRegistered = false
        }
        authorizedWhilePausedAtMs?.let { at ->
            authorizedWhilePausedAtMs = null
            if (SystemClock.elapsedRealtime() - at <= 10_000L) openAuthorizedVault()
        }
        val pending = clipboardToken
        if (pending != null && SystemClock.elapsedRealtime() >= clipboardExpiryMs) {
            clearOwnClipboard(pending)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        val pending = clipboardToken
        if (hasFocus && pending != null && SystemClock.elapsedRealtime() >= clipboardExpiryMs) {
            clearOwnClipboard(pending)
        }
    }

    override fun onStop() {
        if (networkCallbackRegistered) {
            connectivity.unregisterNetworkCallback(networkCallback)
            networkCallbackRegistered = false
        }
        started = false
        lockVault()
        super.onStop()
    }

    override fun onDestroy() {
        clipboardToken?.let(::clearOwnClipboard)
        clipboardToken = null
        handler.removeCallbacksAndMessages(null)
        lockVault()
        super.onDestroy()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        if (unlocked) armIdleTimeout()
    }

    private fun authenticate() {
        if (unlocked) return
        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!keyguard.isDeviceSecure) {
            message = "Para proteger la bóveda, activa primero un PIN, patrón o contraseña en Android."
            return
        }
        message = null
        try {
            // La clave Keystore necesita existir ANTES de la autenticación.
            storage.prepareForAuthentication()
        } catch (error: Exception) {
            message = if (storage.isRevokedLocally) {
                "RobGuard recibió una orden de revocación y bloqueó la bóveda local."
            } else "No se pudo preparar la bóveda. Diagnóstico: " + diagnostic(error) +
                ". No se han borrado datos."
            return
        }
        val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        val manager = BiometricManager.from(this)
        val preferred = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val authenticators = if (modern &&
            manager.canAuthenticate(preferred) == BiometricManager.BIOMETRIC_SUCCESS) {
            preferred
        } else if (modern) {
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        }
        if (!modern && manager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            requestLegacyCredential()
            return
        }
        if (modern && manager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            message = "Android no permite la autenticación segura. Código: " +
                manager.canAuthenticate(authenticators)
            return
        }
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    if (started) openAuthorizedVault()
                    else authorizedWhilePausedAtMs = SystemClock.elapsedRealtime()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (!modern && errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        requestLegacyCredential()
                    } else if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_CANCELED) {
                        message = "Autenticación no completada. Inténtalo de nuevo."
                    }
                }
            }
        )
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Desbloquear RobGuard")
            .setSubtitle("Accede a tu bóveda cifrada")
            .setAllowedAuthenticators(authenticators)
        if (!modern) builder.setNegativeButtonText("Usar PIN o patrón")
        prompt.authenticate(builder.build())
    }

    private fun requestLegacyCredential() {
        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val intent = keyguard.createConfirmDeviceCredentialIntent(
            "Desbloquear RobGuard", "Confirma la identidad del dispositivo"
        )
        if (intent == null) {
            message = "No está disponible una credencial segura del dispositivo."
        } else {
            legacyCredentialLauncher.launch(intent)
        }
    }

    private fun openAuthorizedVault() {
        if (!started) return
        try {
            val opened = storage.openAfterAuthentication()
            session?.close()
            session = opened
            // Aplicar FLAG_SECURE ANTES de que Compose muestre datos sensibles.
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            records = opened.records
            unlocked = true
            message = null
            // Un fallo de la caché de nube jamás impide abrir la bóveda local.
            cloudPaired = try {
                ownerAuth.authorizedUid?.let { uid ->
                    storage.readCloudBaseline(opened, uid) != null
                } ?: false
            } catch (_: Exception) {
                false
            }
            cloudStatus = if (cloudPaired) "Sincronización cifrada activada"
                else "Copia cifrada pendiente de activar o recuperar"
            armIdleTimeout()
            if (cloudPaired) syncCloud()
        } catch (error: Exception) {
            lockVault()
            message = if (storage.isRevokedLocally) {
                "La bóveda se ha bloqueado por una revocación remota."
            } else "No se ha podido abrir la bóveda. Diagnóstico: " + diagnostic(error) +
                ". No se han borrado datos ni claves. No desinstales la aplicación."
        }
    }

    private fun diagnostic(error: Throwable): String =
        generateSequence(error) { it.cause }.take(3)
            .joinToString("/") { it.javaClass.simpleName.ifEmpty { "Error" } }

    private fun saveEntry(entry: VaultEntry): Boolean {
        if (cloudBusy) {
            message = "Sincronización en curso. Inténtalo al finalizar para evitar conflictos."
            return false
        }
        val active = session?.takeIf { unlocked && it.isOpen } ?: return false
        return try {
            entry.validate()
            val updated = if (records.any { it.id == entry.id }) {
                records.map { if (it.id == entry.id) entry else it }
            } else {
                records + entry
            }
            storage.save(active, updated)
            records = updated
            armIdleTimeout()
            message = null
            if (cloudPaired) syncCloud()
            true
        } catch (_: Exception) {
            message = "No se ha guardado el registro. La bóveda anterior permanece intacta."
            false
        }
    }

    private fun deleteEntry(id: String): Boolean {
        if (cloudBusy) {
            message = "Sincronización en curso. Inténtalo al finalizar."
            return false
        }
        val active = session?.takeIf { unlocked && it.isOpen } ?: return false
        if (records.none { it.id == id }) return false
        return try {
            val updated = records.filterNot { it.id == id }
            storage.save(active, updated)
            records = updated
            armIdleTimeout()
            message = null
            if (cloudPaired) syncCloud()
            true
        } catch (_: Exception) {
            message = "No se pudo borrar el registro."
            false
        }
    }

    private fun connectGoogle() {
        if (!ownerAuth.isConfigured) {
            message = "Firebase no configurado. La bóveda local sigue funcionando."
            return
        }
        lifecycleScope.launch {
            try {
                val email = ownerAuth.signIn(this@MainActivity)
                accountLabel = email
                val active = session
                cloudPaired = if (active?.isOpen == true) {
                    try {
                        ownerAuth.authorizedUid?.let { uid ->
                            storage.readCloudBaseline(active, uid) != null
                        } ?: false
                    } catch (_: Exception) {
                        false
                    }
                } else false
                cloudStatus = if (cloudPaired) "Sincronización cifrada activada"
                    else "Puedes crear o recuperar una copia cifrada"
                message = null
                if (deviceAuthorization.enabled) refreshDeviceAccess()
                if (cloudPaired) syncCloud()
            } catch (_: Exception) {
                accountLabel = null
                message = "No se completó la vinculación Google. Comprueba cuenta y configuración."
            }
        }
    }

    private fun disconnectGoogle() {
        lifecycleScope.launch {
            ownerAuth.signOut()
            accountLabel = null
            deviceSelf = null
            deviceList = emptyList()
            message = "Cuenta Google desconectada. Los datos locales siguen intactos."
        }
    }

    private fun prepareCloudBackup() {
        if (session?.isOpen != true || cloudPaired || ownerAuth.authorizedUid == null) {
            message = "Desbloquea la bóveda y conecta la cuenta Google autorizada."
            return
        }
        recoveryCode = RecoveryCrypto.generate()
    }

    private fun confirmCloudBackup() {
        val code = recoveryCode ?: return
        val active = session?.takeIf { unlocked && it.isOpen } ?: return
        if (cloudBusy) return
        // Mantener visible el código durante la operación. Si el servidor
        // confirma pero falla la persistencia local, el usuario conservará
        // el código necesario para recuperar o revincular la copia cifrada.
        cloudBusy = true
        lifecycleScope.launch {
            try {
                val result = cloud.createBackup(active, code)
                if (session === active && unlocked) {
                    cloudPaired = true
                    cloudStatus = "Copia cifrada creada · revisión ${result.revision}"
                    cloudConflict = false
                    recoveryCode = null
                    message = null
                }
            } catch (_: Exception) {
                // Un fallo después de la transacción puede dejar una copia
                // remota válida: nunca asegurar que no se escribió nada.
                if (session === active && unlocked) {
                    message = "No se confirmó la copia. Conserva el código y comprueba " +
                        "el estado antes de reintentar o recuperar."
                }
            } finally {
                cloudBusy = false
            }
        }
    }

    private fun recoverFromCloud(code: String) {
        val active = session?.takeIf { unlocked && it.isOpen } ?: return
        if (cloudBusy) return
        cloudBusy = true
        lifecycleScope.launch {
            try {
                val (restored, result) = cloud.restore(active, code)
                if (unlocked && session === active) {
                    session = restored
                    records = result.entries
                    cloudPaired = true
                    cloudStatus = "Bóveda recuperada · revisión ${result.revision}"
                    message = null
                    armIdleTimeout()
                } else {
                    restored.close()
                }
            } catch (_: Exception) {
                message = "No se pudo recuperar: revisa el código, la red o la " +
                    "autenticación reciente de Android. Tu bóveda no se ha borrado."
            } finally {
                cloudBusy = false
                if (cloudPaired && unlocked) syncCloud()
            }
        }
    }

    private fun hasValidatedNetwork(): Boolean {
        val network = connectivity.activeNetwork ?: return false
        val caps = connectivity.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun syncCloud() {
        val active = session?.takeIf { unlocked && it.isOpen } ?: return
        if (!cloudPaired || cloudBusy || ownerAuth.authorizedUid == null) return
        if (!hasValidatedNetwork()) {
            cloudStatus = "Cambios locales pendientes · sin Internet"
            return
        }
        cloudBusy = true
        cloudStatus = "Sincronizando cifrado…"
        lifecycleScope.launch {
            try {
                val result = cloud.synchronize(active)
                if (session === active && unlocked) {
                    records = result.entries
                    cloudStatus = "Sincronizado · revisión ${result.revision}"
                    cloudConflict = false
                    message = null
                }
            } catch (conflict: CloudVaultService.Conflict) {
                cloudStatus = "Conflicto pendiente"
                cloudConflict = true
                message = "Hay ${conflict.count} registro(s) modificados en ambos " +
                    "dispositivos. No se ha sobrescrito ninguna versión."
            } catch (_: Exception) {
                cloudStatus = "Sin conexión o sincronización pendiente"
                message = "No se pudo sincronizar. Los cambios locales se han conservado."
            } finally {
                cloudBusy = false
            }
        }
    }

    private fun resolveCloudConflicts() {
        val active = session?.takeIf { unlocked && it.isOpen } ?: return
        if (!cloudConflict || cloudBusy || !hasValidatedNetwork()) return
        cloudBusy = true
        cloudStatus = "Conservando las versiones en conflicto…"
        lifecycleScope.launch {
            try {
                val result = cloud.synchronize(active, preserveConflictsAsCopies = true)
                if (session === active && unlocked) {
                    records = result.entries
                    cloudConflict = false
                    cloudStatus = "Ambas versiones conservadas · revisión ${result.revision}"
                    message = null
                }
            } catch (_: Exception) {
                message = "No se pudo resolver el conflicto. No se ha sobrescrito la bóveda."
                cloudStatus = "Conflicto pendiente"
            } finally {
                cloudBusy = false
            }
        }
    }


    /**
     * RG-03: consulta al servidor incluso si la bóveda está BLOQUEADA.
     * No muestra "revocado" por una caché local. La comprobación fallida
     * no puede devolver a un dispositivo el acceso a Firestore.
     */
    private fun refreshDeviceAccess() {
        if (!deviceAuthorization.enabled || deviceCheckInProgress) return
        deviceCheckInProgress = true
        lifecycleScope.launch {
            try {
                val info = if (ownerAuth.authorizedUid != null &&
                    deviceAuthorization.thisDeviceUid == null
                ) {
                    // Primera instalación: crear propuesta sin privilegios.
                    deviceAuthorization.registerIfNeeded()
                } else try {
                    // La revocación se consulta incluso tras cerrar Google.
                    deviceAuthorization.refreshMine()
                } catch (_: IllegalStateException) {
                    if (ownerAuth.authorizedUid == null) throw IllegalStateException(
                        "Conecta la cuenta propietaria para registrar esta instalación"
                    )
                    deviceAuthorization.registerIfNeeded()
                }
                deviceSelf = info
                when (info.status) {
                    DeviceStatus.PENDING -> {
                        deviceMessage = "Pendiente de autorización desde otro dispositivo"
                    }
                    DeviceStatus.AUTHORIZED -> {
                        deviceMessage = "Este dispositivo está autorizado"
                        deviceAuthorization.recordHeartbeat()
                    }
                    DeviceStatus.REVOKED -> {
                        lockVault()
                        deviceMessage = if (info.wipeRequested) {
                            // Primero bloquear; después escribir barrera y
                            // destruir la clave LOCAL antes del acuse.
                            storage.wipeAfterVerifiedRevocation()
                            try {
                                deviceAuthorization.acknowledgeWipe()
                                "Bóveda local borrada y revocación confirmada"
                            } catch (_: Exception) {
                                "Bóveda local borrada; confirmación pendiente"
                            }
                        } else {
                            "Acceso a la nube revocado. Bóveda bloqueada."
                        }
                        message = deviceMessage
                    }
                    DeviceStatus.WIPE_CONFIRMED -> {
                        lockVault()
                        deviceMessage = "Borrado local confirmado"
                        message = deviceMessage
                    }
                    DeviceStatus.REVOKE_PENDING -> {
                        lockVault()
                        deviceMessage = "Revocación pendiente"
                    }
                }
            } catch (_: Exception) {
                deviceMessage = "No se pudo comprobar el estado en el servidor"
                // La nube utiliza reglas RG-03 independientes: un fallo
                // de red no permite escrituras ni levantar una revocación.
            } finally {
                deviceCheckInProgress = false
            }
        }
    }

    private fun loadDeviceList() {
        if (!deviceAuthorization.enabled || deviceBusy || ownerAuth.authorizedUid == null) return
        deviceBusy = true
        lifecycleScope.launch {
            try {
                deviceSelf = deviceAuthorization.registerIfNeeded()
                deviceList = if (deviceSelf?.status == DeviceStatus.AUTHORIZED) {
                    deviceAuthorization.listDevices()
                } else emptyList()
                deviceMessage = if (deviceSelf?.status == DeviceStatus.PENDING) {
                    "Pendiente de aprobar. UID de esta instalación: " +
                        (deviceSelf?.authUid ?: "desconocido")
                } else "Inventario actualizado desde el servidor"
            } catch (_: Exception) {
                deviceMessage = "Inventario no disponible: verifica conexión y autorización"
            } finally {
                deviceBusy = false
            }
        }
    }

    private fun approveDevice(targetUid: String) {
        if (!deviceAuthorization.enabled || deviceBusy) return
        deviceBusy = true
        lifecycleScope.launch {
            try {
                deviceAuthorization.approveDevice(targetUid)
                deviceList = deviceAuthorization.listDevices()
                deviceMessage = "Dispositivo autorizado desde el servidor"
            } catch (_: Exception) {
                deviceMessage = "Autorización rechazada o conexión no disponible"
            } finally {
                deviceBusy = false
            }
        }
    }

    private fun revokeDevice(targetUid: String, wipeRequested: Boolean) {
        if (!deviceAuthorization.enabled || deviceBusy) return
        deviceBusy = true
        lifecycleScope.launch {
            try {
                deviceAuthorization.revokeDevice(targetUid, wipeRequested)
                deviceList = deviceAuthorization.listDevices()
                deviceMessage = if (wipeRequested) {
                    "Acceso revocado. Borrado local solicitado para su próxima conexión."
                } else "Acceso a la nube revocado."
            } catch (_: Exception) {
                deviceMessage = "No se pudo revocar el dispositivo: inténtalo de nuevo"
            } finally {
                deviceBusy = false
            }
        }
    }

    private fun copyPassword(password: String) {
        if (!unlocked || session?.isOpen != true) return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val token = "RobGuard:" + UUID.randomUUID().toString()
        val clip = ClipData.newPlainText(token, password)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        clipboard.setPrimaryClip(clip)
        clipboardToken = token
        clipboardExpiryMs = SystemClock.elapsedRealtime() + 30_000L
        handler.postDelayed({ clearOwnClipboard(token) }, 30_000L)
        armIdleTimeout()
    }

    private fun clearOwnClipboard(token: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        try {
            // Android 10+ puede devolver null cuando nuestra app no tiene el foco.
            // No reemplazar a ciegas contenido copiado por otras aplicaciones.
            val current = clipboard.primaryClipDescription ?: return
            if (current.label?.toString() != token) {
                if (clipboardToken == token) clipboardToken = null
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                clipboard.clearPrimaryClip()
            } else {
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
            if (clipboardToken == token) clipboardToken = null
        } catch (_: SecurityException) {
            // Restricciones de acceso al portapapeles sin foco: reintentar al volver.
        }
    }

    private fun armIdleTimeout() {
        handler.removeCallbacks(idleTimeout)
        if (unlocked) handler.postDelayed(idleTimeout, 120_000L)
    }

    private fun lockVault() {
        recoveryCode = null
        cloudConflict = false
        handler.removeCallbacks(idleTimeout)
        // Al ir a otra app se bloquea la bóveda, pero debe quedar tiempo para pegar.
        // El portapapeles sigue bajo el temporizador de 30 s independiente.
        session?.close()
        session = null
        records = emptyList()
        unlocked = false
    }
}
