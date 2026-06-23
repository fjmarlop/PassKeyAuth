package es.fjmarlop.corpsecauth.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.painter.Painter

/** Branding opcional. El logo es un slot Painter, NUNCA un resource hardcodeado (ADR-014). */
@Immutable
data class PasskeyAuthBranding(
    val logo: Painter? = null,
    val brandName: String? = null,
)
