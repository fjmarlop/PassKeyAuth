package es.fjmarlop.corpsecauth.sample

import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import es.fjmarlop.corpsecauth.PasskeyAuth
import es.fjmarlop.corpsecauth.PasskeyAuthConfig
import es.fjmarlop.corpsecauth.core.errors.IntegrityException
import es.fjmarlop.corpsecauth.sample.ui.navigation.AppNavigation
import es.fjmarlop.corpsecauth.sample.ui.screens.security.SecurityBlockScreen
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

    // Mensaje de bloqueo por integridad (root/emulador/hooking/debugger). Cuando
    // se establece, la UI muestra SecurityBlockScreen en lugar del nav graph y la
    // app no intentará operar con el SDK sin inicializar (evita el crash posterior).
    private val securityBlockMessage = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // SEGURIDAD (ADR-015): el host también debería marcar FLAG_SECURE en las
        // pantallas que muestran credenciales (CredentialsScreen). Las pantallas del
        // SDK ya lo aplican internamente en PasskeyAuthActivity.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        enableEdgeToEdge()

        // Inicializar PasskeyAuth con timeout configurable
        lifecycleScope.launch {
            PasskeyAuth.initialize(
                context = applicationContext,
                // Custom: emulatorPolicy=Warn por defecto, para no bloquear el demo
                // en emuladores. En producción usar Default (emulatorPolicy=Block).
                config = PasskeyAuthConfig.Custom(sessionTimeoutMinutes = 2)
            ).onFailure { error ->
                println("❌ Error inicializando PasskeyAuth: ${error.message}")
                // Si el entorno está comprometido, mostrar bloqueo amigable en vez
                // de dejar que la app falle al intentar enrolar/autenticar después.
                if (error is IntegrityException) {
                    securityBlockMessage.value = error.getUserMessage()
                }
            }
        }

        setContent {
            PasskeyAuthTheme {
                val blockMessage by securityBlockMessage
                if (blockMessage != null) {
                    SecurityBlockScreen(
                        message = blockMessage!!,
                        onClose = { finishAndRemoveTask() },
                    )
                } else {
                    AppNavigation()
                }
            }
        }
    }

    // SEGURIDAD (ADR-015, bloque E1): tapjacking — igual que PasskeyAuthActivity.
    // Protege CredentialsScreen (email + contraseña temporal).
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.flags and MotionEvent.FLAG_WINDOW_IS_OBSCURED != 0) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ev.flags and MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED != 0) return false
        return super.dispatchTouchEvent(ev)
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