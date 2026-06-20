package es.fjmarlop.corpsecauth.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalPasskeyColors = compositionLocalOf<PasskeyAuthColors> {
    error("PasskeyAuthColors no provistos: envuelve el contenido en PasskeyAuthTheme { }")
}
private val LocalPasskeyBranding = staticCompositionLocalOf { PasskeyAuthBranding() }

/** Acceso a los tokens del SDK desde cualquier composable hijo. */
object PasskeyAuthTheme {
    val colors: PasskeyAuthColors
        @Composable get() = LocalPasskeyColors.current
    val branding: PasskeyAuthBranding
        @Composable get() = LocalPasskeyBranding.current
}

/**
 * Theme del SDK. Zero-config: si no se pasan [colors], derivan del MaterialTheme
 * del host. Tipografía/shapes se heredan de Material por defecto (ADR-014).
 */
@Composable
fun PasskeyAuthTheme(
    colors: PasskeyAuthColors = PasskeyAuthColors.fromMaterial(),
    branding: PasskeyAuthBranding = PasskeyAuthBranding(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalPasskeyColors provides colors,
        LocalPasskeyBranding provides branding,
        content = content,
    )
}
