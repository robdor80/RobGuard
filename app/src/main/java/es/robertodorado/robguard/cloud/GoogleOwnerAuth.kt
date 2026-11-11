package es.robertodorado.robguard.cloud

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import es.robertodorado.robguard.R
import kotlinx.coroutines.tasks.await

/**
 * Única cuenta Google. La verificación de UID se hace *después* de que
 * Firebase haya validado la identidad y antes de autorizar cualquier acceso.
 * Firestore debe repetir esta condición en reglas del servidor.
 */
internal class GoogleOwnerAuth(context: Context) {
    private val appContext = context.applicationContext
    private val apiKey = appContext.getString(R.string.rg02_firebase_api_key)
    private val appId = appContext.getString(R.string.rg02_firebase_app_id)
    private val projectId = appContext.getString(R.string.rg02_firebase_project_id)
    private val webClientId = appContext.getString(R.string.rg02_google_web_client_id)
    private val ownerUid = appContext.getString(R.string.rg02_owner_uid)

    val isConfigured: Boolean = listOf(apiKey, appId, projectId, webClientId, ownerUid)
        .all { it.isNotBlank() && !it.startsWith("CONFIGURE_") }

    private val firebaseApp: FirebaseApp? by lazy {
        if (!isConfigured) null else try {
            FirebaseApp.getApps(appContext)
                .firstOrNull { it.name == APP_NAME }
                ?: FirebaseApp.initializeApp(
                    appContext,
                    FirebaseOptions.Builder()
                        .setApiKey(apiKey)
                        .setApplicationId(appId)
                        .setProjectId(projectId)
                        .build(),
                    APP_NAME
                )
        } catch (_: IllegalArgumentException) {
            // Una configuración mal formada no puede bloquear la bóveda local.
            null
        }
    }

    private val auth: FirebaseAuth? by lazy { firebaseApp?.let(FirebaseAuth::getInstance) }

    val authorizedUid: String?
        get() {
            val current = auth?.currentUser ?: return null
            if (current.uid != ownerUid) {
                auth?.signOut()
                return null
            }
            return current.uid
        }

    val authorizedEmail: String?
        get() = if (authorizedUid == null) null else auth?.currentUser?.email

    val firestore: FirebaseFirestore?
        get() = if (authorizedUid != null) firebaseApp?.let(FirebaseFirestore::getInstance)
                else null

    suspend fun signIn(activity: Activity): String {
        check(isConfigured) { "Firebase no configurado" }
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = CredentialManager.create(activity)
            .getCredential(context = activity, request = request).credential
        require(credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Credencial inesperada"
        }
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        val result = requireNotNull(auth).signInWithCredential(
            GoogleAuthProvider.getCredential(idToken, null)
        ).await()
        val user = result.user
        if (user == null || user.uid != ownerUid) {
            auth?.signOut()
            throw SecurityException("La cuenta Google seleccionada no está autorizada")
        }
        return user.email ?: "Cuenta autorizada"
    }

    suspend fun signOut() {
        auth?.signOut()
        // El proveedor debe descartar el estado de credenciales de la app.
        try {
            CredentialManager.create(appContext)
                .clearCredentialState(ClearCredentialStateRequest())
        } catch (_: Exception) {
            // Sesión Firebase ya revocada localmente; ninguna bóveda se borra.
        }
    }

    companion object {
        private const val APP_NAME = "RobGuard"
    }
}
