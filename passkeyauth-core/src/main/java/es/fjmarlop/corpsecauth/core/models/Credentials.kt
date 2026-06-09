package es.fjmarlop.corpsecauth.core.models

/**
 * Credenciales para [es.fjmarlop.corpsecauth.core.firebase.AuthBackend.authenticate].
 *
 * Sealed class para soportar multiples mecanismos de autenticacion en el futuro
 * sin romper la firma de [es.fjmarlop.corpsecauth.core.firebase.AuthBackend].
 *
 * **Diseno (Path C de ADR-010 revision 2026-05-31):**
 * Hoy solo existe [EmailPassword] porque el SDK solo soporta el flujo passwordless
 * basado en Firebase Email/Password. Subtipos futuros previstos:
 * - `AuthorizationCode` (OAuth2 auth code flow para Keycloak/Auth0)
 * - `DeviceCode` (OAuth2 device code flow)
 * - `MagicLink` (email magic link flow)
 *
 * Anadir un subtipo es no-breaking para los subtipos existentes; solo afecta a
 * los `when` exhaustivos en las implementaciones de [AuthBackend].
 */
internal sealed class Credentials {

    /**
     * Credenciales email + password para flujo de enrollment con credenciales
     * temporales proporcionadas por IT.
     *
     * @property email Email del usuario.
     * @property password Password temporal (sera invalidada por [PasswordManagementBackend]
     *                    en el paso 2 del enrollment, ADR-006).
     */
    data class EmailPassword(
        val email: String,
        val password: String
    ) : Credentials()
}
