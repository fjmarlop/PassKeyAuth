package es.fjmarlop.corpsecauth.core.models

/**
 * Configuración para el diálogo de autenticación biométrica.
 *
 * Define los textos y comportamiento del BiometricPrompt de Android.
 * Esta configuración es usada internamente por [BiometricAuthenticator].
 *
 * @property title Título principal del diálogo (obligatorio)
 * @property subtitle Subtítulo explicativo (opcional)
 * @property description Descripción adicional (opcional)
 * @property negativeButtonText Texto del botón de cancelación
 * @property confirmationRequired Si requiere confirmación explícita del usuario
 * 
 * Ejemplo:
 * ```kotlin
 * val config = BiometricConfig(
 *     title = "Autenticación requerida",
 *     subtitle = "Verifica tu identidad para continuar",
 *     negativeButtonText = "Usar contraseña"
 * )
 * ```
 *
 * @see androidx.biometric.BiometricPrompt
 */
data class BiometricConfig(
    val title: String,
    val subtitle: String? = null,
    val description: String? = null,
    val negativeButtonText: String = "Cancelar",
    val confirmationRequired: Boolean = false
) {
    init {
        require(title.isNotBlank()) { "title no puede estar vacío" }
    }

    companion object {
        /**
         * Configuración por defecto para autenticación.
         */
        val Default = BiometricConfig(
            title = "Autenticación requerida",
            subtitle = "Verifica tu identidad con biometría"
        )

        /**
         * Configuración para enrollment (primer registro).
         */
        val Enrollment = BiometricConfig(
            title = "Configurar biometría",
            subtitle = "Registra tu huella digital",
            description = "Usarás tu huella para acceder de forma segura",
            confirmationRequired = true
        )

        /**
         * Configuración para operaciones críticas (ej: cambio de contraseña).
         */
        val Critical = BiometricConfig(
            title = "Operación sensible",
            subtitle = "Confirma tu identidad",
            description = "Esta acción requiere verificación adicional",
            confirmationRequired = true
        )
    }
}