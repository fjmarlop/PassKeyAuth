package es.fjmarlop.corpsecauth.core.firebase

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import es.fjmarlop.corpsecauth.core.errors.FirebaseException
import es.fjmarlop.corpsecauth.core.models.AuthUser
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Gestor de autenticacion con Firebase.
 *
 * Maneja login, logout, cambio de password, y obtencion del usuario actual.
 *
 * SEGURIDAD: Implementa passwordless real invalidando credenciales temporales
 * con passwords aleatorias que el usuario nunca conoce.
 */
internal class FirebaseAuthManager private constructor(
    private val auth: FirebaseAuth
) {

    /**
     * Realiza login con credenciales temporales.
     *
     * @param email Email del usuario
     * @param temporaryPassword Password temporal proporcionada por IT
     * @return [Result.success] con FirebaseUser o [Result.failure]
     */
    suspend fun loginWithTemporaryCredentials(
        email: String,
        temporaryPassword: String
    ): Result<FirebaseUser> = suspendCoroutine { continuation ->
        println("🔐 FirebaseAuthManager: Iniciando login con credenciales temporales")

        auth.signInWithEmailAndPassword(email, temporaryPassword)
            .addOnSuccessListener { authResult ->
                val user = authResult.user
                if (user != null) {
                    println("✅ FirebaseAuthManager: Login exitoso (uid: ${user.uid})")
                    continuation.resume(Result.success(user))
                } else {
                    println("❌ FirebaseAuthManager: Usuario nulo despues de login")
                    continuation.resume(
                        Result.failure(
                            FirebaseException.AuthenticationFailed("Usuario nulo despues de autenticacion")
                        )
                    )
                }
            }
            .addOnFailureListener { exception ->
                println("❌ FirebaseAuthManager: Error en login: ${exception.message}")
                val authException = when (exception) {
                    is FirebaseAuthInvalidUserException -> FirebaseException.UserNotFound(
                        "Usuario no encontrado"
                    )
                    is FirebaseAuthInvalidCredentialsException -> FirebaseException.InvalidCredentials(
                        "Credenciales invalidas"
                    )
                    else -> FirebaseException.AuthenticationFailed(
                        exception.message ?: "Error de autenticacion"
                    )
                }
                continuation.resume(Result.failure(authException))
            }
    }

    /**
     * Invalida la password temporal con una password aleatoria fuerte.
     *
     * SEGURIDAD: Genera password de 32 caracteres con:
     * - Mayusculas, minusculas, numeros, simbolos
     * - Usando SecureRandom (cryptographically strong)
     * - Usuario NUNCA conoce esta password
     *
     * Esto convierte el sistema en passwordless real: solo biometria.
     *
     * @return [Result.success] o [Result.failure]
     */
    suspend fun invalidateTemporaryPassword(): Result<Unit> = suspendCoroutine { continuation ->
        println("🔐 FirebaseAuthManager: Invalidando password temporal con random")

        val currentUser = auth.currentUser
        if (currentUser == null) {
            continuation.resume(
                Result.failure(
                    FirebaseException.UserNotFound("No hay usuario autenticado")
                )
            )
            return@suspendCoroutine
        }

        // Generar password aleatoria fuerte de 32 chars
        val randomPassword = generateSecureRandomPassword(length = 32)

        currentUser.updatePassword(randomPassword)
            .addOnSuccessListener {
                println("✅ FirebaseAuthManager: Password temporal invalidada exitosamente")
                println("🔐 FirebaseAuthManager: Usuario ahora es passwordless (solo biometria)")
                continuation.resume(Result.success(Unit))
            }
            .addOnFailureListener { exception ->
                println("❌ FirebaseAuthManager: Error invalidando password: ${exception.message}")
                continuation.resume(
                    Result.failure(
                        FirebaseException.PasswordChangeFailed(
                            exception.message ?: "Error cambiando password"
                        )
                    )
                )
            }
    }

    /**
     * Genera una password aleatoria cryptographically strong.
     *
     * @param length Longitud de la password (default 32)
     * @return Password aleatoria
     */
    private fun generateSecureRandomPassword(length: Int = 32): String {
        val charset = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()-_=+[]{}|;:,.<>?"
        val random = SecureRandom()
        
        return (1..length)
            .map { charset[random.nextInt(charset.length)] }
            .joinToString("")
    }

    /**
     * Obtiene el usuario actual autenticado.
     *
     * @return [AuthUser] si hay sesion, null si no
     */
    fun getCurrentUser(): AuthUser? {
        val firebaseUser = auth.currentUser ?: return null

        return AuthUser(
            uid = firebaseUser.uid,
            email = firebaseUser.email ?: "",
            displayName = firebaseUser.displayName,
            isEmailVerified = firebaseUser.isEmailVerified
        )
    }

    /**
     * Cierra la sesion actual.
     */
    fun signOut() {
        println("🚪 FirebaseAuthManager: Cerrando sesion")
        auth.signOut()
    }

    companion object {
        /**
         * Crea FirebaseAuthManager con la instancia default de Firebase.
         */
        fun createDefault(): FirebaseAuthManager {
            return FirebaseAuthManager(FirebaseAuth.getInstance())
        }

        /**
         * Crea FirebaseAuthManager con instancia custom (testing).
         */
        internal fun createWithAuth(auth: FirebaseAuth): FirebaseAuthManager {
            return FirebaseAuthManager(auth)
        }
    }
}