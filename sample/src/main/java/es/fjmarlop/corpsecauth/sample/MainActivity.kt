package es.fjmarlop.corpsecauth.sample

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import es.fjmarlop.corpsecauth.PasskeyAuth
import es.fjmarlop.corpsecauth.PasskeyAuthConfig
import es.fjmarlop.corpsecauth.sample.ui.navigation.AppNavigation
import es.fjmarlop.corpsecauth.sample.ui.theme.PasskeyAuthTheme
import kotlinx.coroutines.launch

/**
 * MainActivity de la aplicacion de ejemplo.
 *
 * IMPORTANTE: MainActivity DEBE extender FragmentActivity.
 *
 * BiometricPrompt internamente requiere FragmentActivity para mostrar
 * el dialogo de autenticacion biometrica. Si usas ComponentActivity
 * o AppCompatActivity obtendras ClassCastException en tiempo de ejecucion.
 *
 * ❌ NO usar:
 * - ComponentActivity
 * - AppCompatActivity
 *
 * ✅ USAR:
 * - FragmentActivity
 *
 * Razon tecnica: BiometricPrompt usa Fragment transactions internamente
 * para mostrar el dialogo de autenticacion, lo cual requiere que el host
 * sea FragmentActivity.
 */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar PasskeyAuth con timeout configurable
        lifecycleScope.launch {
            PasskeyAuth.initialize(
                context = applicationContext,
                config = PasskeyAuthConfig.Default  // 0 min = siempre pide huella
                // Otras opciones:
                // PasskeyAuthConfig.Default  // 2 min timeout
                // PasskeyAuthConfig.Custom(sessionTimeoutMinutes = 10)
            ).onFailure { error ->
                println("❌ Error inicializando PasskeyAuth: ${error.message}")
            }
        }

        setContent {
            PasskeyAuthTheme {
                AppNavigation()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Verificar si debe invalidar sesion por timeout
        if (!isChangingConfigurations) {
            PasskeyAuth.onAppForeground()  // Solo si NO es rotación
        }
    }

    override fun onStop() {
        super.onStop()

        // Marcar timestamp cuando app va a background
        if (!isChangingConfigurations) {
            PasskeyAuth.onAppBackground()
        }
    }
}