package es.robertodorado.robguard.devices

/**
 * Estado de una instalación, procedente de una fuente de confianza.
 * Estos estados NO son decisiones de autorización local: el servidor
 * debe denegar por sí mismo cualquier acceso de una instalación revocada.
 */
internal enum class DeviceStatus {
    PENDING,
    AUTHORIZED,
    REVOKE_PENDING,
    REVOKED,
    WIPE_CONFIRMED
}

internal data class DeviceRecord(
    val installationId: String,
    val alias: String,
    val status: DeviceStatus,
    val enrolledAtMillis: Long,
    val lastServerSeenAtMillis: Long?
) {
    init {
        require(installationId.matches(Regex("[a-zA-Z0-9_-]{16,128}"))) {
            "Identificador de instalación inválido"
        }
        require(alias.isNotBlank() && alias.length <= 64) {
            "Alias inválido"
        }
        require(enrolledAtMillis > 0L) { "Fecha de alta inválida" }
        require(lastServerSeenAtMillis == null || lastServerSeenAtMillis >= enrolledAtMillis) {
            "Última conexión anterior al alta"
        }
    }
}

/**
 * Máquina de estados pura para eventos VERIFICADOS, no para peticiones del usuario.
 * La transición de autorización real se valida en servidor.
 */
internal object DeviceLifecycle {
    fun acceptsVerifiedTransition(from: DeviceStatus, to: DeviceStatus): Boolean =
        when (from) {
            DeviceStatus.PENDING -> to == DeviceStatus.AUTHORIZED
            DeviceStatus.AUTHORIZED -> to == DeviceStatus.REVOKE_PENDING ||
                to == DeviceStatus.REVOKED
            DeviceStatus.REVOKE_PENDING ->
                to == DeviceStatus.REVOKED
            DeviceStatus.REVOKED -> to == DeviceStatus.WIPE_CONFIRMED
            DeviceStatus.WIPE_CONFIRMED -> false
        }

    fun mayRequestVaultAccess(status: DeviceStatus): Boolean =
        status == DeviceStatus.AUTHORIZED
}
