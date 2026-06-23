package es.fjmarlop.corpsecauth.ui.signin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import es.fjmarlop.corpsecauth.ui.R
import es.fjmarlop.corpsecauth.ui.theme.PasskeyAuthColors
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
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(56.dp))
        header?.invoke()

        branding.logo?.let {
            Icon(
                painter = it,
                contentDescription = branding.brandName,
                tint = Color.Unspecified,
                modifier = Modifier.size(48.dp),
            )
        }

        Spacer(Modifier.weight(1f))

        when (state) {
            PasskeyUiState.Loading, PasskeyUiState.Success -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(52.dp),
                    color = colors.primary,
                    strokeWidth = 3.dp,
                )
            }
            PasskeyUiState.Idle -> ContentBlock(
                icon = Icons.Outlined.Fingerprint,
                iconTint = colors.primary,
                title = stringResource(R.string.passkey_signin_title),
                subtitle = stringResource(R.string.passkey_signin_subtitle),
                colors = colors,
            )
            is PasskeyUiState.Error -> ContentBlock(
                icon = Icons.Outlined.Warning,
                iconTint = colors.error,
                title = state.message,
                subtitle = null,
                colors = colors,
            )
            PasskeyUiState.NotEnrolled -> ContentBlock(
                icon = Icons.Outlined.TouchApp,
                iconTint = colors.primary,
                title = stringResource(R.string.passkey_not_enrolled_title),
                subtitle = stringResource(R.string.passkey_not_enrolled_subtitle),
                colors = colors,
            )
            PasskeyUiState.NoHardware -> ContentBlock(
                icon = Icons.Outlined.Block,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                title = stringResource(R.string.passkey_no_hardware_title),
                subtitle = stringResource(R.string.passkey_no_hardware_subtitle),
                colors = colors,
            )
        }

        Spacer(Modifier.weight(1f))

        when (state) {
            PasskeyUiState.Loading, PasskeyUiState.Success -> Unit
            PasskeyUiState.NoHardware -> {
                if (allowHostFallback) {
                    TextButton(onClick = onHostFallback) {
                        Text(stringResource(R.string.passkey_host_fallback_cta))
                    }
                }
            }
            else -> {
                val ctaLabel = when (state) {
                    PasskeyUiState.Idle -> stringResource(R.string.passkey_signin_cta)
                    is PasskeyUiState.Error -> stringResource(R.string.passkey_error_retry)
                    PasskeyUiState.NotEnrolled -> stringResource(R.string.passkey_not_enrolled_cta)
                    else -> ""
                }
                Button(
                    onClick = onPrimaryAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary,
                    ),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        text = ctaLabel,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                // Escape hatch en estado de error: evita el bucle de reintentos sin salida
                if (allowHostFallback && state is PasskeyUiState.Error) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = onHostFallback) {
                        Text(stringResource(R.string.passkey_host_fallback_cta))
                    }
                }
            }
        }

        Spacer(Modifier.height(48.dp))
        footer?.invoke()
    }
}

@Composable
private fun ContentBlock(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String?,
    colors: PasskeyAuthColors,
) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = iconTint,
        modifier = Modifier.size(72.dp),
    )
    Spacer(Modifier.height(32.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        color = colors.onSurface,
    )
    if (subtitle != null) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
