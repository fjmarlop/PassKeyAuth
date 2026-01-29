package es.fjmarlop.corpsecauth.core.models

/**
 * Información del usuario autenticado.
 *
 * Representa los datos básicos del usuario obtenidos de Firebase Auth.
 * Esta clase es inmutable por diseño (data class) para garantizar
 * thread-safety en operaciones concurrentes.
 *
 * @property uid ID único del usuario en Firebase (no cambia nunca)
 * @property email Email corporativo del usuario
 * @property displayName Nombre completo del usuario (opcional)
 * @property photoUrl URL del avatar/foto (opcional)
 * @property isEmailVerified Si el email fue verificado en Firebase
 * 
 * Ejemplo:
 * ```kotlin
 * val user = AuthUser(
 *     uid = "abc123xyz",
 *     email = "usuario@empresa.com",
 *     displayName = "Juan Pérez",
 *     photoUrl = null,
 *     isEmailVerified = true
 * )
 * ```
 */
data class AuthUser(
    val uid: String,
    val email: String,
    val displayName: String? = null,
    val photoUrl: String? = null,
    val isEmailVerified: Boolean = false
) {
    /**
     * Valida que los datos del usuario sean correctos.
     *
     * @throws IllegalArgumentException si uid o email están vacíos
     */
    init {
        require(uid.isNotBlank()) { "uid no puede estar vacío" }
        require(email.isNotBlank()) { "email no puede estar vacío" }
    }

    /**
     * Devuelve el nombre a mostrar, priorizando displayName sobre email.
     */
    fun getDisplayNameOrEmail(): String = displayName?.takeIf { it.isNotBlank() } ?: email
}