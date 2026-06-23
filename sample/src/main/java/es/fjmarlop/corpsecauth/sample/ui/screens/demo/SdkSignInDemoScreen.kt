package es.fjmarlop.corpsecauth.sample.ui.screens.demo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import es.fjmarlop.corpsecauth.PasskeyAuthConfig
import es.fjmarlop.corpsecauth.ui.signin.PasskeySignInScreen
import es.fjmarlop.corpsecauth.ui.theme.PasskeyAuthTheme

/**
 * Demo de PasskeySignInScreen del módulo passkeyauth-ui (ADR-014).
 * Zero-config: PasskeyAuthTheme deriva del MaterialTheme del sample.
 */
@Composable
fun SdkSignInDemoScreen(
    activity: FragmentActivity,
    onAuthenticated: () -> Unit,
    onBack: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        PasskeyAuthTheme {
            PasskeySignInScreen(
                activity = activity,
                config = PasskeyAuthConfig.Default,
                onAuthenticated = onAuthenticated,
                onHostFallback = onBack,
            )
        }
    }
}
