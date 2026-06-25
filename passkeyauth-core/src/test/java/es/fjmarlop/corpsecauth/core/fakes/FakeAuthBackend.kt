package es.fjmarlop.corpsecauth.core.fakes

import es.fjmarlop.corpsecauth.AuthBackend
import es.fjmarlop.corpsecauth.AuthSession
import es.fjmarlop.corpsecauth.Credentials
import es.fjmarlop.corpsecauth.core.errors.FirebaseException
import es.fjmarlop.corpsecauth.core.models.AuthUser

/**
 * Fake JVM de [AuthBackend] para tests sin Firebase emulator.
 *
 * Mantiene estado in-memory de "usuario actual" y permite configurar resultados
 * de [authenticate].
 *
 * **Patron de uso:**
 * ```kotlin
 * val fake = FakeAuthBackend()
 *
 * // Configurar usuario que se devolvera tras login
 * fake.authenticateResult = Result.success(
 *     AuthSession(
 *         user = AuthUser("uid-123", "test@empresa.com", null, true),
 *         idToken = "fake-jwt-token"
 *     )
 * )
 *
 * // O simular fallo
 * fake.authenticateResult = Result.failure(
 *     FirebaseException.InvalidCredentials("test")
 * )
 *
 * // Verificar
 * assertThat(fake.signOutCallCount).isEqualTo(1) // por rollback
 * assertThat(fake.lastCredentials).isInstanceOf(Credentials.EmailPassword::class.java)
 * ```
 */
internal class FakeAuthBackend : AuthBackend {

    // === Configuracion ===

    /**
     * Resultado que devolvera [authenticate].
     * Si es exitoso, el usuario tambien se establece como "currentUser".
     */
    var authenticateResult: Result<AuthSession> = Result.failure(
        FirebaseException.AuthenticationFailed(
            "FakeAuthBackend: configurar authenticateResult antes de usar"
        )
    )

    // === Estado interno ===

    private var currentUser: AuthUser? = null

    // === Contadores y argumentos capturados ===

    var authenticateCallCount = 0
        private set
    var signOutCallCount = 0
        private set
    var getCurrentUserCallCount = 0
        private set

    var lastCredentials: Credentials? = null
        private set

    val credentialsHistory: MutableList<Credentials> = mutableListOf()

    // === Implementacion ===

    override suspend fun authenticate(credentials: Credentials): Result<AuthSession> {
        authenticateCallCount++
        lastCredentials = credentials
        credentialsHistory.add(credentials)

        return authenticateResult.also { result ->
            result.onSuccess { session -> currentUser = session.user }
        }
    }

    override fun getCurrentUser(): AuthUser? {
        getCurrentUserCallCount++
        return currentUser
    }

    override fun signOut() {
        signOutCallCount++
        currentUser = null
    }

    // === Helpers de test ===

    /**
     * Establece un usuario como "ya autenticado" sin pasar por [authenticate].
     * Util para tests del path "sesion existente".
     */
    fun forceCurrentUser(user: AuthUser) {
        currentUser = user
    }
}
