package es.fjmarlop.corpsecauth.core.firebase

import es.fjmarlop.corpsecauth.core.models.AuthSession
import es.fjmarlop.corpsecauth.core.models.AuthUser
import es.fjmarlop.corpsecauth.core.models.Credentials

/**
 * Contrato de autenticacion contra un backend remoto.
 *
 * Esta interfaz abstrae la frontera con Firebase Auth (o cualquier backend
 * compatible) para permitir testing JVM y futura independencia de backend.
 *
 * Ver ADR-010 para la justificacion arquitectonica.
 *
 * **Implementaciones:**
 * - [FirebaseAuthBackend]: implementacion real con Firebase (production).
 * - `FakeAuthBackend` (src/test/): fake configurable para tests JVM.
 *
 * **NOTA DE EVOLUCION (Path C de ADR-010, revision 2026-05-31):**
 * Este contrato esta disenado para el modelo actual del SDK:
 * - Credenciales email+password (otros flujos OAuth2 cuando se anada subtipo a [Credentials])
 * - Token unico opaco (refresh + expiracion ya previstos como nullable en [AuthSession])
 *
 * **Limitaciones conocidas si se anade un backend OAuth2/OIDC (Keycloak, Auth0, custom):**
 * 1. La invalidacion de password temporal NO esta aqui — esta en [PasswordManagementBackend]
 *    como capability separada. Backends que no soporten cambio de password client-side
 *    pueden no inyectar la dependencia.
 * 2. `getCurrentUser()` es sincrono asumiendo cache local (modelo Firebase). Backends
 *    sin cache requeririan version suspend.
 * 3. Operaciones de refresh de token (`refreshSession()`) no estan modeladas — Firebase
 *    refresca internamente. Necesarias para OAuth2 explicito.
 *
 * **Garantias del contrato:**
 * - Toda operacion suspend respeta cancelacion de coroutines.
 * - Los errores se mapean a [es.fjmarlop.corpsecauth.core.errors.FirebaseException]
 *   (futuro: refactor a `AuthBackendException` generico cuando llegue segundo backend).
 */
internal interface AuthBackend {

    /**
     * Autentica al usuario con las credenciales proporcionadas.
     *
     * Atomico: si el login es exitoso devuelve [AuthSession] completa (usuario + token).
     * Si cualquier paso falla, devuelve [Result.failure] sin estado parcial.
     *
     * @param credentials Credenciales del usuario (ver subtipos de [Credentials]).
     * @return [Result.success] con [AuthSession] o [Result.failure].
     */
    suspend fun authenticate(credentials: Credentials): Result<AuthSession>

    /**
     * Devuelve el usuario actualmente autenticado, o `null` si no hay sesion.
     *
     * Asume cache local (modelo Firebase). Ver "limitaciones conocidas" arriba.
     */
    fun getCurrentUser(): AuthUser?

    /**
     * Cierra la sesion actual.
     *
     * No devuelve [Result] porque en Firebase no es operacion remota.
     * Si en el futuro un backend requiere llamada remota, considerar version suspend.
     */
    fun signOut()

    companion object {
        /**
         * Crea la implementacion por defecto (Firebase).
         *
         * Para inyectar instancia custom (testing, multi-tenant), usar el constructor
         * de [FirebaseAuthBackend] directamente.
         */
        fun createDefault(): AuthBackend = FirebaseAuthBackend.createDefault()
    }
}
