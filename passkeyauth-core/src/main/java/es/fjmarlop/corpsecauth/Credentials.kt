package es.fjmarlop.corpsecauth

/**
 * Credenciales del usuario para [AuthBackend.authenticate].
 *
 * Sealed class para soportar multiples mecanismos sin romper la firma del contrato.
 *
 * Subtipos futuros previstos: `AuthorizationCode` (OAuth2), `DeviceCode`, `MagicLink`.
 */
sealed class Credentials {

    /**
     * Credenciales email + password para el flujo de enrollment con credenciales
     * temporales proporcionadas por IT (ADR-006).
     */
    data class EmailPassword(
        val email: String,
        val password: String
    ) : Credentials()
}
