package es.robertodorado.robguard

import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import es.robertodorado.robguard.vault.VaultEntry
import es.robertodorado.robguard.vault.VaultSession
import es.robertodorado.robguard.vault.VaultStorage
import java.util.UUID

class MainActivity : FragmentActivity() {
    private val storage by lazy { VaultStorage(this) }
    private var session: VaultSession? = null
    private var unlocked by mutableStateOf(false)
    private var records by mutableStateOf<List<VaultEntry>>(emptyList())
    private var message by mutableStateOf<String?>(null)
    private val handler = Handler(Looper.getMainLooper())
    private val idleTimeout = Runnable { lockVault() }
    private var clipboardToken: String? = null
    private var started = false
    private val legacyCredentialLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && started) openAuthorizedVault()
        else if (started) message = "No se ha completado la autenticación."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            RobGuardApp(
                unlocked = unlocked,
                entries = records,
                message = message,
                onUnlock = ::authenticate,
                onLock = ::lockVault,
                onSave = ::saveEntry,
                onDelete = ::deleteEntry,
                onCopy = ::copyPassword
            )
        }
    }

    override fun onStart() {
        // El resultado de PIN/patrón puede despacharse dentro de super.onStart().
        started = true
        super.onStart()
    }

    override fun onStop() {
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
        val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        val authenticators = if (modern) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        }
        val manager = BiometricManager.from(this)
        if (!modern && manager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            requestLegacyCredential()
            return
        }
        if (modern && manager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            message = "Android no permite la autenticación segura configurada en este dispositivo."
            return
        }
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    if (started) openAuthorizedVault()
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
            records = opened.records
            unlocked = true
            message = null
            armIdleTimeout()
        } catch (_: Exception) {
            lockVault()
            message = "No se ha podido abrir la bóveda. No se han eliminado datos. " +
                "Comprueba el bloqueo del dispositivo y evita reinstalar la aplicación."
        }
    }

    private fun saveEntry(entry: VaultEntry): Boolean {
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
            true
        } catch (_: Exception) {
            message = "No se ha guardado el registro. La bóveda anterior permanece intacta."
            false
        }
    }

    private fun deleteEntry(id: String): Boolean {
        val active = session?.takeIf { unlocked && it.isOpen } ?: return false
        if (records.none { it.id == id }) return false
        return try {
            val updated = records.filterNot { it.id == id }
            storage.save(active, updated)
            records = updated
            armIdleTimeout()
            message = null
            true
        } catch (_: Exception) {
            message = "No se pudo borrar el registro."
            false
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
        handler.postDelayed({ clearOwnClipboard(token) }, 30_000L)
        armIdleTimeout()
    }

    private fun clearOwnClipboard(token: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (clipboard.primaryClipDescription?.label?.toString() != token) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            clipboard.clearPrimaryClip()
        } else {
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
        if (clipboardToken == token) clipboardToken = null
    }

    private fun armIdleTimeout() {
        handler.removeCallbacks(idleTimeout)
        if (unlocked) handler.postDelayed(idleTimeout, 120_000L)
    }

    private fun lockVault() {
        handler.removeCallbacks(idleTimeout)
        // Al ir a otra app se bloquea la bóveda, pero debe quedar tiempo para pegar.
        // El portapapeles sigue bajo el temporizador de 30 s independiente.
        session?.close()
        session = null
        records = emptyList()
        unlocked = false
    }
}
