package es.robertodorado.robguard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.robertodorado.robguard.vault.PasswordGenerator
import es.robertodorado.robguard.vault.PasswordOptions
import es.robertodorado.robguard.vault.VaultEntry
import java.util.UUID

private val Navy = Color(0xFF081525)
private val Panel = Color(0xFF11283F)
private val Cyan = Color(0xFF4CDAF2)
private val SoftText = Color(0xFFAFC9D8)

internal enum class GuardSection(val title: String) {
    VAULT("Bóveda"),
    NEW("Nueva"),
    SETTINGS("Ajustes")
}

private val Palette = darkColorScheme(
    primary = Cyan,
    onPrimary = Navy,
    background = Navy,
    onBackground = Color(0xFFF3FAFF),
    surface = Panel,
    onSurface = Color(0xFFF3FAFF),
    surfaceVariant = Color(0xFF193652),
    onSurfaceVariant = SoftText
)

@Composable
internal fun RobGuardApp(
    unlocked: Boolean,
    entries: List<VaultEntry>,
    message: String?,
    onUnlock: () -> Unit,
    onLock: () -> Unit,
    onSave: (VaultEntry) -> Boolean,
    onDelete: (String) -> Boolean,
    onCopy: (String) -> Unit,
    googleConfigured: Boolean = false,
    googleAccount: String? = null,
    onConnectGoogle: () -> Unit = {},
    onDisconnectGoogle: () -> Unit = {},
    cloudPaired: Boolean = false,
    cloudConflict: Boolean = false,
    onResolveConflicts: () -> Unit = {},
    cloudBusy: Boolean = false,
    cloudStatus: String = "Sólo almacenamiento local",
    recoveryCode: String? = null,
    onPrepareBackup: () -> Unit = {},
    onConfirmBackup: () -> Unit = {},
    onCancelBackup: () -> Unit = {},
    onRecover: (String) -> Unit = {},
    onSyncCloud: () -> Unit = {}
) {
    MaterialTheme(colorScheme = Palette) {
        if (unlocked) {
            // Al bloquear se elimina TODO este subárbol y sus campos sensibles.
            key("vault-unlocked") {
                UnlockedApp(entries, message, onLock, onSave, onDelete, onCopy,
                    googleConfigured, googleAccount, onConnectGoogle, onDisconnectGoogle,
                    cloudPaired, cloudConflict, onResolveConflicts,
                    cloudBusy, cloudStatus, recoveryCode,
                    onPrepareBackup, onConfirmBackup, onCancelBackup, onRecover, onSyncCloud)
            }
        } else {
            LockedApp(message, onUnlock)
        }
    }
}

@Composable
private fun Header(locked: Boolean, onLock: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Image(
            painter = painterResource(R.drawable.ic_robguard_foreground),
            contentDescription = null,
            modifier = Modifier.size(48.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text("RobGuard", style = MaterialTheme.typography.headlineSmall)
            Text(if (locked) "Bóveda protegida" else "Bóveda local", color = SoftText,
                style = MaterialTheme.typography.bodySmall)
        }
        if (!locked) {
            TextButton(onClick = onLock) { Text("Bloquear") }
        }
    }
}

@Composable
private fun LockedApp(message: String?, onUnlock: () -> Unit) {
    Scaffold(containerColor = Navy, topBar = { Header(locked = true) }) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Image(painter = painterResource(R.drawable.ic_robguard_foreground),
                    contentDescription = null, modifier = Modifier.size(100.dp))
                Text("Tu bóveda está bloqueada", style = MaterialTheme.typography.headlineSmall)
                Text("Tus contraseñas están cifradas en este dispositivo. " +
                    "Desbloquea con la protección de Android.",
                    color = SoftText)
                Button(onClick = onUnlock) { Text("Desbloquear bóveda") }
                if (message != null) {
                    MessageCard(message)
                }
                Text("RG-01 · Sin sincronización ni recuperación entre dispositivos. " +
                    "No guardes todavía tus únicas copias de contraseñas importantes.",
                    color = SoftText, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun UnlockedApp(
    entries: List<VaultEntry>,
    message: String?,
    onLock: () -> Unit,
    onSave: (VaultEntry) -> Boolean,
    onDelete: (String) -> Boolean,
    onCopy: (String) -> Unit,
    googleConfigured: Boolean,
    googleAccount: String?,
    onConnectGoogle: () -> Unit,
    onDisconnectGoogle: () -> Unit,
    cloudPaired: Boolean,
    cloudConflict: Boolean,
    onResolveConflicts: () -> Unit,
    cloudBusy: Boolean,
    cloudStatus: String,
    recoveryCode: String?,
    onPrepareBackup: () -> Unit,
    onConfirmBackup: () -> Unit,
    onCancelBackup: () -> Unit,
    onRecover: (String) -> Unit,
    onSyncCloud: () -> Unit
) {
    var section by remember { mutableStateOf(GuardSection.VAULT) }
    var selected by remember { mutableStateOf<VaultEntry?>(null) }
    Scaffold(
        containerColor = Navy,
        topBar = { Header(locked = false, onLock = onLock) },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().background(Panel).padding(6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                GuardSection.entries.forEach { destination ->
                    TextButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (destination == GuardSection.NEW) selected = null
                            section = destination
                        }
                    ) {
                        Text(destination.title, color = if (section == destination) Cyan else SoftText)
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier.fillMaxSize().widthIn(max = 760.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (message != null) MessageCard(message)
                when (section) {
                    GuardSection.VAULT -> VaultList(entries) { item ->
                        selected = item
                        section = GuardSection.NEW
                    }
                    GuardSection.NEW -> {
                        key(selected?.id) {
                            EntryForm(
                                entry = selected,
                                onSave = { record ->
                                    if (onSave(record)) {
                                        selected = null
                                        section = GuardSection.VAULT
                                        true
                                    } else false
                                },
                                onCancel = {
                                    selected = null
                                    section = GuardSection.VAULT
                                },
                                onDelete = { id ->
                                    if (onDelete(id)) {
                                        selected = null
                                        section = GuardSection.VAULT
                                        true
                                    } else false
                                },
                                onCopy = onCopy
                            )
                        }
                    }
                    GuardSection.SETTINGS -> SettingsContent(onLock,
                        googleConfigured, googleAccount, onConnectGoogle, onDisconnectGoogle,
                        cloudPaired, cloudConflict, onResolveConflicts,
                        cloudBusy, cloudStatus, recoveryCode,
                        onPrepareBackup, onConfirmBackup, onCancelBackup, onRecover, onSyncCloud)
                }
            }
        }
    }
}

@Composable
private fun VaultList(entries: List<VaultEntry>, onOpen: (VaultEntry) -> Unit) {
    var search by remember { mutableStateOf("") }
    Text("Contraseñas", style = MaterialTheme.typography.headlineMedium)
    OutlinedTextField(
        value = search,
        onValueChange = { search = it },
        label = { Text("Buscar por nombre, usuario, web o categoría") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    val filtered = remember(entries, search) {
        entries.filter {
            search.isBlank() || listOf(it.title, it.username, it.website, it.category)
                .any { value -> value.contains(search.trim(), ignoreCase = true) }
        }.sortedBy { it.title.lowercase() }
    }
    if (filtered.isEmpty()) {
        InfoCard(
            if (entries.isEmpty()) "Tu bóveda está vacía"
            else "Sin coincidencias",
            if (entries.isEmpty()) "Pulsa Nueva para crear tu primera contraseña."
            else "Prueba con otro término de búsqueda."
        )
    } else {
        Text(filtered.size.toString() + " registro(s)", color = SoftText)
        filtered.forEach { entry ->
            Card(
                onClick = { onOpen(entry) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Panel)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(entry.title, style = MaterialTheme.typography.titleMedium, color = Cyan)
                    if (entry.username.isNotBlank()) Text(entry.username, color = SoftText)
                    if (entry.website.isNotBlank()) Text(entry.website, color = SoftText,
                        style = MaterialTheme.typography.bodySmall)
                    if (entry.category.isNotBlank()) Text(entry.category,
                        style = MaterialTheme.typography.labelSmall, color = SoftText)
                }
            }
        }
    }
}

@Composable
private fun EntryForm(
    entry: VaultEntry?,
    onSave: (VaultEntry) -> Boolean,
    onCancel: () -> Unit,
    onDelete: (String) -> Boolean,
    onCopy: (String) -> Unit
) {
    var title by remember { mutableStateOf(entry?.title.orEmpty()) }
    var username by remember { mutableStateOf(entry?.username.orEmpty()) }
    var password by remember { mutableStateOf(entry?.password.orEmpty()) }
    var website by remember { mutableStateOf(entry?.website.orEmpty()) }
    var category by remember { mutableStateOf(entry?.category.orEmpty()) }
    var notes by remember { mutableStateOf(entry?.notes.orEmpty()) }
    var passwordVisible by remember { mutableStateOf(false) }
    var showGenerator by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }

    Text(if (entry == null) "Nueva contraseña" else "Editar contraseña",
        style = MaterialTheme.typography.headlineMedium)
    OutlinedTextField(title, { title = it }, label = { Text("Nombre del servicio *") },
        singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(username, { username = it }, label = { Text("Usuario o correo") },
        singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(
        value = password, onValueChange = { password = it },
        label = { Text("Contraseña") },
        visualTransformation = if (passwordVisible) VisualTransformation.None
            else PasswordVisualTransformation(),
        trailingIcon = {
            TextButton(onClick = { passwordVisible = !passwordVisible }) {
                Text(if (passwordVisible) "Ocultar" else "Ver")
            }
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    if (password.isNotEmpty()) {
        Text("Evaluación orientativa: " + passwordQuality(password), color = SoftText,
            style = MaterialTheme.typography.bodySmall)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = { showGenerator = !showGenerator }) {
            Text(if (showGenerator) "Cerrar generador" else "Generar contraseña")
        }
        if (password.isNotEmpty()) {
            TextButton(onClick = { onCopy(password) }) { Text("Copiar") }
        }
    }
    if (showGenerator) {
        PasswordGeneratorPanel { generated ->
            password = generated
            passwordVisible = false
            showGenerator = false
        }
    }
    OutlinedTextField(website, { website = it }, label = { Text("Sitio web (opcional)") },
        singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(category, { category = it }, label = { Text("Categoría (opcional)") },
        singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(notes, { notes = it }, label = { Text("Notas privadas") },
        minLines = 3, modifier = Modifier.fillMaxWidth())
    if (formError != null) MessageCard(formError!!)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = {
                if (title.isBlank()) {
                    formError = "Introduce el nombre del servicio."
                } else {
                    val now = System.currentTimeMillis()
                    val candidate = VaultEntry(
                        id = entry?.id ?: UUID.randomUUID().toString(),
                        title = title.trim(),
                        username = username.trim(),
                        password = password,
                        website = website.trim(),
                        category = category.trim(),
                        notes = notes,
                        createdAt = entry?.createdAt ?: now,
                        updatedAt = now
                    )
                    formError = if (onSave(candidate)) null else "No se pudo guardar el registro."
                }
            }
        ) { Text("Guardar") }
        TextButton(onClick = onCancel) { Text("Cancelar") }
        if (entry != null) {
            TextButton(onClick = { showDelete = true }) { Text("Eliminar") }
        }
    }
    if (showDelete && entry != null) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("¿Eliminar este registro?") },
            text = { Text("La eliminación no se puede deshacer en RG-01.") },
            confirmButton = {
                TextButton(onClick = {
                    if (!onDelete(entry.id)) {
                        formError = "No se pudo eliminar."
                        showDelete = false
                    }
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun PasswordGeneratorPanel(onGenerated: (String) -> Unit) {
    var length by remember { mutableIntStateOf(20) }
    var lower by remember { mutableStateOf(true) }
    var upper by remember { mutableStateOf(true) }
    var digits by remember { mutableStateOf(true) }
    var symbols by remember { mutableStateOf(true) }
    var warning by remember { mutableStateOf<String?>(null) }
    Card(colors = CardDefaults.cardColors(containerColor = Panel),
        modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Generador criptográfico", style = MaterialTheme.typography.titleMedium)
            Text("Longitud: $length caracteres", color = SoftText)
            Slider(value = length.toFloat(), onValueChange = { length = it.toInt() },
                valueRange = 12f..64f, steps = 51)
            OptionCheckbox("Minúsculas", lower) { lower = it }
            OptionCheckbox("Mayúsculas", upper) { upper = it }
            OptionCheckbox("Números", digits) { digits = it }
            OptionCheckbox("Símbolos", symbols) { symbols = it }
            if (warning != null) Text(warning!!, color = MaterialTheme.colorScheme.error)
            Button(onClick = {
                try {
                    val generated = PasswordGenerator.generate(
                        PasswordOptions(length, lower, upper, digits, symbols)
                    )
                    warning = null
                    onGenerated(generated)
                } catch (_: IllegalArgumentException) {
                    warning = "Elige al menos un tipo de carácter."
                }
            }) { Text("Usar contraseña generada") }
        }
    }
}

@Composable
private fun OptionCheckbox(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label)
    }
}

private fun passwordQuality(password: String): String {
    val families = listOf(
        password.any(Char::isLowerCase),
        password.any(Char::isUpperCase),
        password.any(Char::isDigit),
        password.any { !it.isLetterOrDigit() }
    ).count { it }
    return when {
        password.length < 12 -> "corta (mejor 12+ caracteres)"
        password.length >= 20 && families >= 3 -> "longitud y variedad altas"
        password.length >= 16 && families >= 2 -> "longitud adecuada"
        else -> "mejorable; aumenta su longitud"
    }
}

@Composable
private fun SettingsContent(
    onLock: () -> Unit,
    googleConfigured: Boolean,
    googleAccount: String?,
    onConnectGoogle: () -> Unit,
    onDisconnectGoogle: () -> Unit,
    cloudPaired: Boolean,
    cloudConflict: Boolean,
    onResolveConflicts: () -> Unit,
    cloudBusy: Boolean,
    cloudStatus: String,
    recoveryCode: String?,
    onPrepareBackup: () -> Unit,
    onConfirmBackup: () -> Unit,
    onCancelBackup: () -> Unit,
    onRecover: (String) -> Unit,
    onSyncCloud: () -> Unit
) {
    var showRecoveryInput by remember { mutableStateOf(false) }
    var enteredRecoveryCode by remember { mutableStateOf("") }
    var confirmedCodeCopy by remember(recoveryCode) { mutableStateOf(false) }
    Text("Ajustes", style = MaterialTheme.typography.headlineMedium)
    InfoCard("Cifrado", "AES-256-GCM. Clave local protegida por Android Keystore.")
    InfoCard("Bloqueo", "Biometría o credencial de pantalla; bloqueo al salir o tras 2 minutos sin uso.")
    InfoCard("Copiar", "RobGuard intenta limpiar su copia a los 30 segundos y al volver a la app. " +
        "Android puede impedir comprobar el portapapeles en segundo plano.")
    InfoCard("Google y Firebase", when {
        !googleConfigured -> "Pendiente de configurar Firebase para la Release oficial."
        googleAccount != null -> "Cuenta autorizada: $googleAccount. " +
            if (cloudPaired) "Vinculación cifrada activa." else "Copia aún no vinculada."
        else -> "Conecta la única cuenta Google autorizada. La bóveda seguirá siendo local."
    })
    if (googleAccount == null) {
        Button(onClick = onConnectGoogle, enabled = googleConfigured) {
            Text("Conectar cuenta Google")
        }
    } else {
        TextButton(onClick = onDisconnectGoogle) { Text("Desconectar Google") }
    }
    InfoCard("Sincronización cifrada", cloudStatus)
    if (cloudConflict) {
        InfoCard("Conflicto entre dispositivos", "Hay modificaciones simultáneas. " +
            "Puedes conservar las dos versiones con nombres distintos para " +
            "revisarlas. Las eliminaciones enfrentadas a ediciones conservarán " +
            "la versión editada.")
        Button(onClick = onResolveConflicts, enabled = !cloudBusy) {
            Text("Conservar versiones en conflicto")
        }
    }
    if (googleAccount != null) {
        if (cloudPaired) {
            Button(onClick = onSyncCloud, enabled = !cloudBusy) { Text("Sincronizar ahora") }
        } else {
            Button(onClick = onPrepareBackup, enabled = !cloudBusy) {
                Text("Activar copia cifrada")
            }
            TextButton(onClick = { showRecoveryInput = true }, enabled = !cloudBusy) {
                Text("Recuperar bóveda desde otro dispositivo")
            }
        }
    } else {
        InfoCard("Sin conexión Google", "Los cambios se guardan localmente y cifrados.")
    }
    if (recoveryCode != null) {
        AlertDialog(
            onDismissRequest = onCancelBackup,
            title = { Text("Guarda tu código de recuperación") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Sin este código no podrás recuperar la bóveda en otro dispositivo. " +
                        "Google no puede proporcionártelo. Guárdalo fuera del móvil.")
                    Text(recoveryCode, color = Cyan)
                    Checkbox(checked = confirmedCodeCopy,
                        onCheckedChange = { confirmedCodeCopy = it })
                    Text("He guardado el código fuera de RobGuard")
                }
            },
            confirmButton = {
                Button(onClick = onConfirmBackup, enabled = confirmedCodeCopy && !cloudBusy) {
                    Text("Crear copia cifrada")
                }
            },
            dismissButton = { TextButton(onClick = onCancelBackup) { Text("Cancelar") } }
        )
    }
    if (showRecoveryInput) {
        AlertDialog(
            onDismissRequest = {
                enteredRecoveryCode = ""
                showRecoveryInput = false
            },
            title = { Text("Recuperar copia cifrada") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Solo sobre una bóveda local vacía. Requiere cuenta Google " +
                        "autorizada y autenticación reciente del dispositivo.")
                    OutlinedTextField(
                        value = enteredRecoveryCode,
                        onValueChange = { enteredRecoveryCode = it },
                        label = { Text("Código de recuperación") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val code = enteredRecoveryCode
                    enteredRecoveryCode = ""
                    showRecoveryInput = false
                    onRecover(code)
                }, enabled = enteredRecoveryCode.isNotBlank() && !cloudBusy) {
                    Text("Recuperar")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    enteredRecoveryCode = ""
                    showRecoveryInput = false
                }) { Text("Cancelar") }
            }
        )
    }
    InfoCard("Distribución", "Sólo RobPublisher → RS-05 → GitHub Release → RobStore.")
    Button(onClick = onLock) { Text("Bloquear ahora") }
}

@Composable
private fun MessageCard(message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel),
        modifier = Modifier.fillMaxWidth()) {
        Text(message, modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun InfoCard(title: String, detail: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, color = Cyan, style = MaterialTheme.typography.titleMedium)
            Text(detail, color = SoftText)
        }
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 850)
@Composable
private fun RobGuardPreview() = RobGuardApp(
    unlocked = false, entries = emptyList(), message = null,
    onUnlock = {}, onLock = {}, onSave = { false }, onDelete = { false }, onCopy = {}
)
