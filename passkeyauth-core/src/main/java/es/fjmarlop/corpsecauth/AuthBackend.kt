package es.fjmarlop.corpsecauth

import es.fjmarlop.corpsecauth.core.models.AuthUser

/**
 * Contrato de autenticacion contra un backend remoto.
 *
 * Implementar esta interfaz para usar un backend alternativo a Firebase
 * (Keycloak, OIDC custom, REST propio). Ver ADR-016.
 *
 * **Implementacion de referencia incluida:**
 * - `FirebaseAuthBackend` (interno): Firebase Auth.
 *
 * **Ejemplo de uso con backend custom:**
 * ```kotlin
 * class MyKeycloakBackend : AuthBackend { ... }
 *
 * PasskeyAuth.initialize(
 *     context = this,
 *     authBackend = MyKeycloakBackend()
 * )
 * ```
 *
 * **Limitaciones conocidas para backends OAuth2/OIDC (ver ADR-016):**
 * - `getCurrentUser()` es sincrono (modelo Firebase con cache local). Backends
 *   sin cache requieren adaptador.
 * - La gestion de password temporal es una capability separada ([PasswordManagementBackend]).
 */
interface AuthBackend {

    /**
     * Autentica al usuario con las credenciales proporcionadas.
     *
     * @return [Result.success] con [AuthSession] o [Result.failure] con un [Throwable].
     */
    suspend fun authenticate(credentials: Credentials): Result<AuthSession>

    /**
     * Devuelve el usuario actualmente autenticado, o `null` si no hay sesion.
     */
    fun getCurrentUser(): AuthUser?

    /**
     * Cierra la sesion actual.
     */
    fun signOut()
}
