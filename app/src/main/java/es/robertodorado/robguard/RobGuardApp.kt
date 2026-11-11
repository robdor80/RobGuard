package es.robertodorado.robguard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.Image
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
fun RobGuardApp() {
    var section by rememberSaveable { mutableStateOf(GuardSection.VAULT) }
    MaterialTheme(colorScheme = Palette) {
        Scaffold(
            containerColor = Navy,
            topBar = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Image(painter = painterResource(R.drawable.ic_robguard_foreground),
                        contentDescription = null, modifier = Modifier.size(52.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("RobGuard", style = MaterialTheme.typography.headlineSmall)
                        Text("Tu bóveda personal", color = SoftText, style = MaterialTheme.typography.bodySmall)
                    }
                    Surface(shape = RoundedCornerShape(12.dp), color = Panel) {
                        Text("RG-00", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            color = Cyan, style = MaterialTheme.typography.labelMedium)
                    }
                }
            },
            bottomBar = {
                Row(modifier = Modifier.fillMaxWidth().background(Panel).padding(8.dp)) {
                    GuardSection.entries.forEach { destination ->
                        val active = destination == section
                        TextButton(
                            onClick = { section = destination },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColors(contentColor = if (active) Cyan else SoftText)
                        ) {
                            Text(destination.title, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding)
                    .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                when (section) {
                    GuardSection.VAULT -> {
                        Text("Contraseñas", style = MaterialTheme.typography.headlineMedium)
                        Card(colors = CardDefaults.cardColors(containerColor = Panel),
                            shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.fillMaxWidth().padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Image(painter = painterResource(R.drawable.ic_robguard_foreground),
                                    contentDescription = null, modifier = Modifier.size(94.dp))
                                Text("Tu bóveda estará aquí", style = MaterialTheme.typography.titleLarge)
                                Text("Estamos preparando el almacenamiento cifrado. Todavía no se pueden guardar contraseñas.",
                                    color = SoftText, style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(4.dp))
                                Button(onClick = { section = GuardSection.NEW }) {
                                    Text("Ver próxima función")
                                }
                            }
                        }
                    }
                    GuardSection.NEW -> {
                        Text("Nueva contraseña", style = MaterialTheme.typography.headlineMedium)
                        StatusCard("Disponible en RG-01",
                            "Aquí podrás guardar usuario, contraseña, web, notas y categoría. No hay formularios activos en esta fase.")
                    }
                    GuardSection.SETTINGS -> {
                        Text("Ajustes", style = MaterialTheme.typography.headlineMedium)
                        StatusCard("Acceso biométrico", "Pendiente para RG-01.")
                        StatusCard("Cuenta de Google y Firebase", "Pendiente para RG-02.")
                        StatusCard("Dispositivos autorizados", "Pendiente para RG-03.")
                        StatusCard("Distribución oficial", "Preparada para el protocolo RS-05; alta de firma aún pendiente.")
                    }
                }
                Text("RG-00 · Estructura inicial · No introducir contraseñas reales",
                    color = SoftText, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun StatusCard(title: String, detail: String) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Panel),
        modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = Cyan, style = MaterialTheme.typography.titleMedium)
            Text(detail, color = SoftText, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 850)
@Composable
private fun RobGuardPreview() = RobGuardApp()
