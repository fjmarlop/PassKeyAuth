package es.fjmarlop.corpsecauth.core.models

/**
 * Sesion autenticada devuelta por [es.fjmarlop.corpsecauth.core.firebase.AuthBackend.authenticate].
 *
 * Representa el resultado atomico de un login exitoso: el usuario y el token
 * de identidad que el SDK cifrara y almacenara.
 *
 * **Diseno (Path C de ADR-010 revision 2026-05-31):**
 * Los campos [refreshToken] y [expiresAt] son nullable para permitir el modelo
 * actual de Firebase (token unico auto-refrescado por el SDK de Firebase)
 * y el modelo OAuth2/OIDC futuro (access + refresh tokens con expiracion explicita).
 *
 * @property user Usuario autenticado.
 * @property idToken Token de identidad opaco. En Firebase es un JWT auto-refrescado.
 *                   En backends OAuth2 seria el `id_token`. El SDK lo trata como bytes opacos.
 * @property refreshToken Refresh token para backends OAuth2/OIDC. `null` para Firebase
 *                        (el SDK de Firebase maneja el refresh internamente).
 * @property expiresAt Epoch millis de expiracion del [idToken]. `null` si no aplica
 *                     (Firebase: auto-refresh; otros: si el backend no expone expiracion).
 */
internal data class AuthSession(
    val user: AuthUser,
    val idToken: String,
    val refreshToken: String? = null,
    val expiresAt: Long? = null
)
