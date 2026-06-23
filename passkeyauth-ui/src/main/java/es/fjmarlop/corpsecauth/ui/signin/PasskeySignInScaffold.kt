package es.fjmarlop.corpsecauth.ui.signin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import es.fjmarlop.corpsecauth.ui.R
import es.fjmarlop.corpsecauth.ui.theme.PasskeyAuthTheme

/**
 * Chrome de la pantalla de entrada: logo → icono → título → subtítulo → CTA.
 * UI pura sin lógica de auth (testeable con Compose UI test). Ver ADR-014.
 * Slots header/footer = escape hatch avanzado.
 */
@Composable
fun PasskeySignInScaffold(
    state: PasskeyUiState,
    allowHostFallback: Boolean,
    onPrimaryAction: () -> Unit,
    onHostFallback: () -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    val colors = PasskeyAuthTheme.colors
    val branding = PasskeyAuthTheme.branding

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        header?.invoke()
        branding.logo?.let {
            Icon(
                painter = it,
                contentDescription = branding.brandName,
                tint = Color.Unspecified,
            )
        }
        Spacer(Modifier.height(24.dp))

        when (state) {
            PasskeyUiState.Idle -> StateBlock(
                title = stringResource(R.string.passkey_signin_title),
                subtitle = stringResource(R.string.passkey_signin_subtitle),
                ctaLabel = stringResource(R.string.passkey_signin_cta),
                onCta = onPrimaryAction,
                primaryColor = colors.primary,
                onPrimaryColor = colors.onPrimary,
            )
            PasskeyUiState.Loading -> CircularProgressIndicator(color = colors.primary)
            is PasskeyUiState.Error -> StateBlock(
                title = state.message,
                subtitle = null,
                ctaLabel = stringResource(R.string.passkey_error_retry),
                onCta = onPrimaryAction,
                primaryColor = colors.primary,
                onPrimaryColor = colors.onPrimary,
            )
            PasskeyUiState.Success -> CircularProgressIndicator(color = colors.primary)
            PasskeyUiState.NotEnrolled -> StateBlock(
                title = stringResource(R.string.passkey_not_enrolled_title),
                subtitle = stringResource(R.string.passkey_not_enrolled_subtitle),
                ctaLabel = stringResource(R.string.passkey_not_enrolled_cta),
                onCta = onPrimaryAction,
                primaryColor = colors.primary,
                onPrimaryColor = colors.onPrimary,
            )
            PasskeyUiState.NoHardware -> {
                Text(stringResource(R.string.passkey_no_hardware_title))
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.passkey_no_hardware_subtitle))
                if (allowHostFallback) {
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = onHostFallback) {
                        Text(stringResource(R.string.passkey_host_fallback_cta))
                    }
                }
            }
        }
        footer?.invoke()
    }
}

@Composable
private fun StateBlock(
    title: String,
    subtitle: String?,
    ctaLabel: String,
    onCta: () -> Unit,
    primaryColor: Color,
    onPrimaryColor: Color,
) {
    Text(title)
    subtitle?.let {
        Spacer(Modifier.height(8.dp))
        Text(it)
    }
    Spacer(Modifier.height(24.dp))
    Button(
        onClick = onCta,
        colors = ButtonDefaults.buttonColors(
            containerColor = primaryColor,
            contentColor = onPrimaryColor,
        ),
    ) { Text(ctaLabel) }
}
