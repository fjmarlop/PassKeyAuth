package es.fjmarlop.corpsecauth.ui.signin

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import es.fjmarlop.corpsecauth.PasskeyAuth
import es.fjmarlop.corpsecauth.PasskeyAuthConfig
import kotlinx.coroutines.launch

/**
 * Composable primitivo de sign-in (capa pública). La app lo mete en su nav graph.
 * Dirige estados con checkCapability() y delega la ceremonia en PasskeyAuth.authenticate().
 * El BiometricPrompt real lo pinta el sistema; esto es el chrome (ADR-014).
 */
@Composable
fun PasskeySignInScreen(
    activity: FragmentActivity,
    config: PasskeyAuthConfig,
    onAuthenticated: () -> Unit,
    onHostFallback: () -> Unit = {},
) {
    val context = LocalContext.current
    var state by remember { mutableStateOf<PasskeyUiState>(PasskeyUiState.Loading) }
    val scope = rememberCoroutineScope()

    // Vuelve a checkCapability() si el usuario registra biometría en Settings
    val enrollLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { state = PasskeyUiState.from(PasskeyAuth.checkCapability(context)) }

    LaunchedEffect(Unit) {
        state = PasskeyUiState.from(PasskeyAuth.checkCapability(context))
    }

    PasskeySignInScaffold(
        state = state,
        allowHostFallback = config.allowHostFallback,
        onHostFallback = onHostFallback,
        onPrimaryAction = {
            when (state) {
                PasskeyUiState.NotEnrolled -> {
                    // ACTION_BIOMETRIC_ENROLL requiere API 30+; fallback a Security Settings
                    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Intent(Settings.ACTION_BIOMETRIC_ENROLL)
                    } else {
                        Intent(Settings.ACTION_SECURITY_SETTINGS)
                    }
                    enrollLauncher.launch(intent)
                }
                else -> scope.launch {
                    state = PasskeyUiState.Loading
                    PasskeyAuth.authenticate(activity)
                        .onSuccess { state = PasskeyUiState.Success; onAuthenticated() }
                        .onFailure { state = PasskeyUiState.Error(it.message ?: "Error de autenticación") }
                }
            }
        },
    )
}
