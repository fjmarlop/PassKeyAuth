package es.fjmarlop.corpsecauth.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/** Tokens de color mínimos del SDK. Un solo acento (primary). Ver ADR-014. */
@Immutable
data class PasskeyAuthColors(
    val primary: Color,
    val onPrimary: Color,
    val surface: Color,
    val onSurface: Color,
    val error: Color,
) {
    companion object {
        /** Zero-config: deriva del MaterialTheme del host (se mimetiza con la app). */
        @Composable
        @ReadOnlyComposable
        fun fromMaterial(): PasskeyAuthColors = with(MaterialTheme.colorScheme) {
            PasskeyAuthColors(
                primary = primary,
                onPrimary = onPrimary,
                surface = surface,
                onSurface = onSurface,
                error = error,
            )
        }
    }
}
