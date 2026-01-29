package es.fjmarlop.corpsecauth.core.models

import es.fjmarlop.corpsecauth.core.errors.PasskeyAuthException

/**
 * Estados del proceso de enrollment (registro inicial del dispositivo).
 *
 * El enrollment es el proceso donde un usuario nuevo configura por primera vez
 * la autenticacion biometrica en un dispositivo. Este proceso incluye:
 * 1. Login con credenciales temporales
 * 2. Cambio de contrasenia (si es temporal)
 * 3. Registro de biometria
 * 4. Device binding en Firebase
 *
 * Cada estado representa un paso especifico del flujo.
 */
sealed class EnrollmentState {
    /**
     * Estado inicial, esperando inicio del enrollment.
     */
    data object Idle : EnrollmentState()

    /**
     * Validando credenciales temporales con Firebase.
     *
     * @property email Email del usuario
     */
    data class ValidatingCredentials(val email: String) : EnrollmentState()

    /**
     * Usuario autenticado, esperando cambio de contrasenia.
     * 
     * @property isTemporaryPassword Si la contrasenia actual es temporal
     */
    data class RequiresPasswordChange(val isTemporaryPassword: Boolean) : EnrollmentState()

    /**
     * Generando clave criptografica en Android KeyStore.
     * 
     * SEGURIDAD: En este paso se crea la clave hardware-backed
     * que sera protegida por la biometria del usuario.
     */
    data object GeneratingCryptoKey : EnrollmentState()

    /**
     * Mostrando BiometricPrompt para registrar biometria.
     *
     * @property config Configuracion del prompt biometrico
     */
    data class AwaitingBiometric(val config: BiometricConfig) : EnrollmentState()

    /**
     * Biometria registrada, vinculando dispositivo a Firebase.
     * 
     * SEGURIDAD: Se guarda el deviceId en Firestore asociado al usuario.
     * Esto permite revocacion remota del dispositivo.
     */
    data object BindingDevice : EnrollmentState()

    /**
     * Enrollment completado exitosamente.
     *
     * @property user Usuario enrollado
     */
    data class Success(val user: AuthUser) : EnrollmentState()

    /**
     * Error durante el enrollment.
     *
     * @property exception Detalle del error
     * @property recoverableStep Paso al que se puede volver (opcional)
     */
    data class Error(
        val exception: PasskeyAuthException,
        val recoverableStep: EnrollmentState? = null
    ) : EnrollmentState()
}