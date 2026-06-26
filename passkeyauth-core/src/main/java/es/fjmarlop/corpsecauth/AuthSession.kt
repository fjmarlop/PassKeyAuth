package es.fjmarlop.corpsecauth

import es.fjmarlop.corpsecauth.core.models.AuthUser

/**
 * Sesion autenticada resultante de un [AuthBackend.authenticate] exitoso.
 *
 * @property user Usuario autenticado.
 * @property idToken Token opaco para validacion server-side.
 * @property refreshToken Token de refresco (opcional; null si el backend gestiona refresco internamente).
 * @property expiresAt Timestamp Unix en ms de expiracion (null si el backend gestiona expiracion).
 */
data class AuthSession(
    val user: AuthUser,
    val idToken: String,
    val refreshToken: String? = null,
    val expiresAt: Long? = null
)
