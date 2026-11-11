package es.robertodorado.robguard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.robertodorado.robguard.devices.DeviceAuthorization
import es.robertodorado.robguard.devices.DeviceStatus
import java.text.DateFormat
import java.util.Date

/**
 * Inventario del servidor: NUNCA simular dispositivos ni afirmar que una
 * solicitud de borrado significa que el dispositivo fue borrado realmente.
 */
@Composable
internal fun DeviceSettingsPanel(
    enabled: Boolean,
    googleAccount: String?,
    currentDevice: DeviceAuthorization.DeviceInfo?,
    devices: List<DeviceAuthorization.DeviceInfo>,
    busy: Boolean,
    statusMessage: String,
    onRefresh: () -> Unit,
    onApprove: (String) -> Unit,
    onRevoke: (String, Boolean) -> Unit
) {
    var approveTarget by remember { mutableStateOf<DeviceAuthorization.DeviceInfo?>(null) }
    var revokeTarget by remember { mutableStateOf<DeviceAuthorization.DeviceInfo?>(null) }
    var remoteWipe by remember(revokeTarget) { mutableStateOf(false) }

    Text("Mis dispositivos", style = MaterialTheme.typography.titleLarge)
    if (!enabled) {
        Text("RG-03 se activará con las reglas y la Release oficiales. " +
            "La sincronización RG-02 sigue intacta.")
        return
    }
    if (googleAccount == null) {
        Text("Conecta la cuenta Google autorizada para gestionar instalaciones.")
        return
    }
    Text(statusMessage)
    TextButton(onClick = onRefresh, enabled = !busy) { Text("Actualizar dispositivos") }
    currentDevice?.let {
        Text("Este dispositivo: ${it.alias} · ${deviceLabel(it.status, it.wipeRequested)}",
            style = MaterialTheme.typography.titleMedium)
        if (it.status == DeviceStatus.PENDING) {
            Text("Primera vinculación: confirma este UID desde Firebase Console. " +
                "Los siguientes equipos se podrán aprobar desde uno autorizado.")
            Text(it.authUid, style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (devices.isEmpty() && currentDevice?.status == DeviceStatus.AUTHORIZED) {
        Text("El inventario todavía no se ha consultado al servidor.")
    }
    devices.filterNot { it.isThisDevice }.forEach { info ->
        Card(modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(info.alias, style = MaterialTheme.typography.titleMedium)
                Text(deviceLabel(info.status, info.wipeRequested))
                Text("Última conexión confirmada: " +
                    (info.lastSeenAtMillis?.let {
                        DateFormat.getDateTimeInstance().format(Date(it))
                    } ?: "sin datos"))
                if (info.status == DeviceStatus.PENDING) {
                    TextButton(onClick = { approveTarget = info }, enabled = !busy) {
                        Text("Autorizar")
                    }
                }
                if (info.status == DeviceStatus.AUTHORIZED) {
                    TextButton(onClick = { revokeTarget = info }, enabled = !busy) {
                        Text("Revocar acceso")
                    }
                }
            }
        }
    }
    approveTarget?.let { info ->
        AlertDialog(
            onDismissRequest = { approveTarget = null },
            title = { Text("¿Autorizar ${info.alias}?") },
            text = { Text("Comprueba personalmente que reconoces este equipo. " +
                "La autorización permitirá acceder a la bóveda cifrada " +
                "cuando se disponga del código de recuperación.") },
            confirmButton = {
                Button(onClick = {
                    onApprove(info.authUid)
                    approveTarget = null
                }, enabled = !busy) { Text("Autorizar") }
            },
            dismissButton = {
                TextButton(onClick = { approveTarget = null }) { Text("Cancelar") }
            }
        )
    }
    revokeTarget?.let { info ->
        AlertDialog(
            onDismissRequest = { revokeTarget = null },
            title = { Text("Revocar ${info.alias}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("El servidor bloqueará su acceso a Firebase. La copia " +
                        "local puede seguir disponible sin Internet.")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = remoteWipe,
                            onCheckedChange = { remoteWipe = it })
                        Text("Solicitar borrado al reconectar")
                    }
                    if (remoteWipe) Text(
                        "No es un borrado instantáneo: se ejecutará si la app " +
                            "recibe la orden. La destrucción física no se garantiza."
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onRevoke(info.authUid, remoteWipe)
                    revokeTarget = null
                }, enabled = !busy) { Text("Confirmar revocación") }
            },
            dismissButton = {
                TextButton(onClick = { revokeTarget = null }) { Text("Cancelar") }
            }
        )
    }
}

internal fun deviceLabel(status: DeviceStatus, wipeRequested: Boolean): String =
    when (status) {
        DeviceStatus.PENDING -> "Pendiente de aprobación"
        DeviceStatus.AUTHORIZED -> "Autorizado"
        DeviceStatus.REVOKE_PENDING -> "Revocación pendiente"
        DeviceStatus.REVOKED -> if (wipeRequested) "Revocado · borrado solicitado" else "Revocado"
        DeviceStatus.WIPE_CONFIRMED -> "Borrado comunicado por la aplicación"
    }
