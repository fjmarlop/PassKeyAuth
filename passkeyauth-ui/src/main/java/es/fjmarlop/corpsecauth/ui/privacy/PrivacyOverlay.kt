package es.fjmarlop.corpsecauth.ui.privacy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Overlay de privacidad (ADR-015, bloque A2).
 *
 * Cubre la pantalla con una superficie opaca cuando la app deja de estar en
 * primer plano (`ON_PAUSE`). Complementa a `FLAG_SECURE`: aunque el flag ya
 * oculta el contenido del app switcher del sistema, este overlay cierra el hueco
 * de launchers de terceros o servicios de accesibilidad que puedan capturar el
 * último frame antes de que el flag surta efecto.
 *
 * Colócalo como último hijo de un `Box` que envuelva el contenido, para que se
 * dibuje por encima:
 * ```
 * Box(Modifier.fillMaxSize()) {
 *     MiContenido()
 *     PrivacyOverlay(enabled = config.enablePrivacyOverlay)
 * }
 * ```
 */
@Composable
fun PrivacyOverlay(enabled: Boolean = true) {
    if (!enabled) return

    val lifecycleOwner = LocalLifecycleOwner.current
    var inForeground by remember { mutableStateOf(true) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> inForeground = true
                Lifecycle.Event.ON_PAUSE -> inForeground = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!inForeground) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Security,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            )
        }
    }
}
