package es.fjmarlop.corpsecauth

import android.content.Context

/**
 * Handler de recuperación controlada y auditable.
 *
 * SEGURIDAD (ADR-013): la recuperación NUNCA es el PIN/patrón del dispositivo.
 * Es re-aprovisionamiento server-side (magic link / re-provisioning) con su log
 * y su política. Lo provee el dev integrador; null = sin recuperación in-app.
 */
fun interface RecoveryHandler {
    suspend fun recover(context: Context): Result<Unit>
}
