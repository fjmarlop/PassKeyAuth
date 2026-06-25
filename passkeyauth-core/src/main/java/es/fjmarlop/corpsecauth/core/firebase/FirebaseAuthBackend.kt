package es.fjmarlop.corpsecauth.core.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import es.fjmarlop.corpsecauth.AuthBackend
import es.fjmarlop.corpsecauth.AuthSession
import es.fjmarlop.corpsecauth.Credentials
import es.fjmarlop.corpsecauth.PasswordManagementBackend
import es.fjmarlop.corpsecauth.core.errors.FirebaseException
import es.fjmarlop.corpsecauth.core.models.AuthUser
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Implementacion Firebase de [AuthBackend] y [PasswordManagementBackend].
 *
 * Un solo objeto sirve ambas capabilities porque Firebase Auth maneja ambas
 * (authentication y password change client-side).
 *
 * SEGURIDAD:
 * - Implementa passwordless real invalidando credenciales temporales con
 *   passwords aleatorias de 32 chars que el usuario nunca conoce (ADR-006).
 * - Mapea excepciones de Firebase a [FirebaseException] del dominio del SDK.
 *
 * Para tests JVM, usar `FakeAuthBackend` / `FakePasswordManagementBackend` (src/test/).
 */
internal class FirebaseAuthBackend(
    private val auth: FirebaseAuth
) : AuthBackend, PasswordManagementBackend {

    override suspend fun authenticate(credentials: Credentials): Result<AuthSession> {
        return when (credentials) {
            is Credentials.EmailPassword -> authenticateWithEmailPassword(
                email = credentials.email,
                password = credentials.password
            )
        }
    }

    /**
     * Login Firebase con email+password + obtencion atomica del ID token.
     *
     * Si el login OK pero `getIdToken` falla, la sesion Firebase queda iniciada
     * (Firebase no provee rollback). El llamador (EnrollmentManager) maneja
     * el rollback con [signOut].
     */
    private suspend fun authenticateWithEmailPassword(
        email: String,
        password: String
    ): Result<AuthSession> = suspendCoroutine { continuation ->
        println("🔐 FirebaseAuthBackend: Iniciando login con email/password")

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { authResult ->
                val firebaseUser = authResult.user
                if (firebaseUser != null) {
                    println("✅ FirebaseAuthBackend: Login exitoso (uid: ${firebaseUser.uid})")
                    // Obtener ID token de forma atomica con el login
                    completeSessionWithToken(firebaseUser, continuation)
                } else {
                    continuation.resume(
                        Result.failure(
                            FirebaseException.AuthenticationFailed(
                                "Usuario nulo despues de autenticacion"
                            )
                        )
                    )
                }
            }
            .addOnFailureListener { exception ->
                println("❌ FirebaseAuthBackend: Error en login: ${exception.message}")
                continuation.resume(Result.failure(mapAuthException(exception)))
            }
    }

    /**
     * Obtiene el ID token de Firebase y construye [AuthSession].
     *
     * Firebase auto-refresca el token internamente, por eso [AuthSession.refreshToken]
     * queda `null` y [AuthSession.expiresAt] tambien (la responsabilidad de refresh
     * la mantiene el SDK de Firebase).
     */
    private fun completeSessionWithToken(
        firebaseUser: FirebaseUser,
        continuation: kotlin.coroutines.Continuation<Result<AuthSession>>
    ) {
        firebaseUser.getIdToken(false)
            .addOnSuccessListener { tokenResult ->
                val token = tokenResult.token
                if (token != null) {
                    val session = AuthSession(
                        user = firebaseUser.toAuthUser(),
                        idToken = token,
                        refreshToken = null, // Firebase auto-refresca, no exponemos refresh
                        expiresAt = null     // Firebase maneja expiracion internamente
                    )
                    continuation.resume(Result.success(session))
                } else {
                    continuation.resume(
                        Result.failure(
                            FirebaseException.AuthenticationFailed(
                                "ID token nulo despues de login"
                            )
                        )
                    )
                }
            }
            .addOnFailureListener { exception ->
                println("❌ FirebaseAuthBackend: Error obteniendo ID token: ${exception.message}")
                continuation.resume(
                    Result.failure(
                        FirebaseException.AuthenticationFailed(
                            "Error obteniendo ID token: ${exception.message}"
                        )
                    )
                )
            }
    }

    override suspend fun invalidateTemporaryPassword(): Result<Unit> = suspendCoroutine { continuation ->
        println("🔐 FirebaseAuthBackend: Invalidando password temporal con random")

        val currentUser = auth.currentUser
        if (currentUser == null) {
            continuation.resume(
                Result.failure(
                    FirebaseException.UserNotFound("No hay usuario autenticado")
                )
            )
            return@suspendCoroutine
        }

        val randomPassword = generateSecureRandomPassword(length = 32)

        currentUser.updatePassword(randomPassword)
            .addOnSuccessListener {
                println("✅ FirebaseAuthBackend: Password temporal invalidada")
                println("🔐 FirebaseAuthBackend: Usuario ahora es passwordless (solo biometria)")
                continuation.resume(Result.success(Unit))
            }
            .addOnFailureListener { exception ->
                println("❌ FirebaseAuthBackend: Error invalidando password: ${exception.message}")
                continuation.resume(
                    Result.failure(
                        FirebaseException.PasswordChangeFailed(
                            exception.message ?: "Error cambiando password"
                        )
                    )
                )
            }
    }

    override fun getCurrentUser(): AuthUser? {
        return auth.currentUser?.toAuthUser()
    }

    override fun signOut() {
        println("🚪 FirebaseAuthBackend: Cerrando sesion")
        auth.signOut()
    }

    /**
     * Mapea excepciones de Firebase Auth a [FirebaseException] del dominio.
     */
    private fun mapAuthException(exception: Exception): FirebaseException {
        return when (exception) {
            is FirebaseAuthInvalidUserException ->
                FirebaseException.UserNotFound("Usuario no encontrado")

            is FirebaseAuthInvalidCredentialsException ->
                FirebaseException.InvalidCredentials("Credenciales invalidas")

            else ->
                FirebaseException.AuthenticationFailed(
                    exception.message ?: "Error de autenticacion"
                )
        }
    }

    /**
     * Genera una password aleatoria cryptographically strong.
     *
     * SEGURIDAD: SecureRandom + 32 caracteres + charset extendido = ~200 bits entropia.
     */
    private fun generateSecureRandomPassword(length: Int = 32): String {
        val charset = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#\$%^&*()-_=+[]{}|;:,.<>?"
        val random = SecureRandom()

        return (1..length)
            .map { charset[random.nextInt(charset.length)] }
            .joinToString("")
    }

    /**
     * Convierte [FirebaseUser] al modelo del dominio [AuthUser].
     */
    private fun FirebaseUser.toAuthUser(): AuthUser = AuthUser(
        uid = uid,
        email = email ?: "",
        displayName = displayName,
        isEmailVerified = isEmailVerified
    )

    companion object {
        /**
         * Crea [FirebaseAuthBackend] con la instancia default de Firebase.
         */
        fun createDefault(): FirebaseAuthBackend {
            return FirebaseAuthBackend(FirebaseAuth.getInstance())
        }
    }
}
