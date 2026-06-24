package es.fjmarlop.corpsecauth.ui.enroll

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import es.fjmarlop.corpsecauth.PasskeyAuth
import es.fjmarlop.corpsecauth.core.models.EnrollmentState
import es.fjmarlop.corpsecauth.ui.privacy.PrivacyOverlay
import es.fjmarlop.corpsecauth.ui.signin.PasskeySignInScaffold
import es.fjmarlop.corpsecauth.ui.signin.PasskeyUiState

/**
 * Composable de enrollment. Reutiliza el scaffold y mapea EnrollmentState → PasskeyUiState.
 */
@Composable
fun PasskeyEnrollScreen(
    activity: FragmentActivity,
    email: String,
    temporaryPassword: String,
    onEnrolled: () -> Unit,
) {
    var state by remember { mutableStateOf<PasskeyUiState>(PasskeyUiState.Idle) }

    LaunchedEffect(email) {
        PasskeyAuth.enrollDevice(activity, email, temporaryPassword).collect { es ->
            state = when (es) {
                is EnrollmentState.Success -> { onEnrolled(); PasskeyUiState.Success }
                is EnrollmentState.Error -> PasskeyUiState.Error(
                    es.exception.message ?: "Error en enrollment"
                )
                EnrollmentState.Idle -> PasskeyUiState.Idle
                // ValidatingCredentials, RequiresPasswordChange, GeneratingCryptoKey,
                // AwaitingBiometric, BindingDevice → progreso genérico
                else -> PasskeyUiState.Loading
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        PasskeySignInScaffold(
            state = state,
            allowHostFallback = false,
            onPrimaryAction = {},
            onHostFallback = {},
        )
        // El enrollment maneja la ceremonia biométrica: overlay de privacidad siempre activo.
        PrivacyOverlay(enabled = true)
    }
}
