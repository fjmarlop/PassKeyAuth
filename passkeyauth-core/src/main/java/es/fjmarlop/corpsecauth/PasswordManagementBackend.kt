package es.fjmarlop.corpsecauth

/**
 * Capability opcional de gestion client-side de password temporal.
 *
 * Implementar esta interfaz junto con [AuthBackend] si el backend soporta
 * invalidacion de password temporal client-side (modelo Firebase Auth).
 *
 * Backends OAuth2/OIDC tipicos (Keycloak, Auth0) NO soportan este modelo
 * y pueden omitir esta interfaz. Ver ADR-016.
 */
interface PasswordManagementBackend {

    /**
     * Invalida la password temporal del usuario actual reemplazandola por una
     * password aleatoria fuerte que el usuario nunca conoce (passwordless real).
     *
     * @return [Result.success] o [Result.failure].
     */
    suspend fun invalidateTemporaryPassword(): Result<Unit>
}
