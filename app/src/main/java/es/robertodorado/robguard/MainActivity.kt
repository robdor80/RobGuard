package es.robertodorado.robguard

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ningún contenido de la futura bóveda aparecerá en capturas o en Recientes.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent { RobGuardApp() }
    }
}
