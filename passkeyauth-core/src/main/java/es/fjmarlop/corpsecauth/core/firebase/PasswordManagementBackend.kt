package es.fjmarlop.corpsecauth.core.firebase

/**
 * Capability separada de [AuthBackend] para gestion client-side de password.
 *
 * Esta interfaz existe como **capability pattern** porque la invalidacion de password
 * temporal (paso 2 del enrollment, ADR-006) es una operacion muy especifica del modelo
 * Firebase Auth (cambio de password client-side via `FirebaseUser.updatePassword()`).
 *
 * Backends OAuth2/OIDC tipicos (Keycloak, Auth0) NO soportan este modelo:
 * - Keycloak: requiere admin API server-side con "required action" UPDATE_PASSWORD
 * - Auth0: requiere Management API server-side
 * - Backends custom: implementacion arbitraria, normalmente server-side
 *
 * **Por que esta separada de [AuthBackend]:**
 * Mantenerla aparte permite que un backend que no soporte cambio de password
 * client-side simplemente no inyecte esta dependencia, en lugar de tener una
 * operacion vacia o que lance excepcion en una interfaz mas grande.
 *
 * Ver ADR-010 (Path C, revision 2026-05-31) para justificacion completa.
 *
 * **Implementaciones:**
 * - [FirebaseAuthBackend]: tambien implementa esta interfaz (un solo objeto
 *   sirve ambas capabilities en el caso Firebase).
 * - `FakePasswordManagementBackend` (src/test/): fake para tests del paso 2.
 */
internal interface PasswordManagementBackend {

    /**
     * Invalida la password temporal del usuario actual reemplazandola por una
     * password aleatoria fuerte que el usuario NUNCA conoce (passwordless real).
     *
     * Implementacion: 32 caracteres con [java.security.SecureRandom] sobre charset
     * de mayusculas + minusculas + numeros + simbolos especiales (~200 bits entropia).
     *
     * Requiere usuario autenticado previamente via [AuthBackend.authenticate].
     *
     * @return [Result.success] o [Result.failure] con
     *         [es.fjmarlop.corpsecauth.core.errors.FirebaseException.PasswordChangeFailed].
     */
    suspend fun invalidateTemporaryPassword(): Result<Unit>

    companion object {
        /**
         * Crea la implementacion por defecto (Firebase).
         */
        fun createDefault(): PasswordManagementBackend = FirebaseAuthBackend.createDefault()
    }
}
