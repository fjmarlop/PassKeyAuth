package es.fjmarlop.corpsecauth.core.errors

/**
 * Errores relacionados con autenticación biométrica.
 *
 * Estas excepciones cubren todos los casos de fallo de BiometricPrompt
 * y validación de capacidades biométricas del dispositivo.
 */
sealed class BiometricException(
    message: String,
    cause: Throwable? = null
) : PasskeyAuthException(message, cause) {

    /**
     * El dispositivo no tiene hardware biométrico.
     * 
     * Este error es crítico y no permite continuar con el SDK.
     */
    class HardwareNotAvailable(
        message: String = "Este dispositivo no tiene sensor biométrico"
    ) : BiometricException(message) {
        override val errorCode = "BIOMETRIC_NO_HARDWARE"
        override fun getUserMessage() = "Tu dispositivo no soporta autenticación biométrica"
    }

    /**
     * El hardware biométrico está temporalmente no disponible.
     * 
     * Puede ser por:
     * - Sensor ocupado por otra app
     * - Error temporal del sensor
     * - Actualizaciones del sistema
     */
    class HardwareUnavailable(
        message: String = "El sensor biométrico no está disponible"
    ) : BiometricException(message) {
        override val errorCode = "BIOMETRIC_HW_UNAVAILABLE"
        override fun getUserMessage() = "El sensor está ocupado, intenta de nuevo"
    }

    /**
     * No hay huellas digitales o biometría registrada en el dispositivo.
     * 
     * El usuario debe ir a Ajustes → Seguridad y configurar biometría.
     */
    class NoneEnrolled(
        message: String = "No hay huellas registradas"
    ) : BiometricException(message) {
        override val errorCode = "BIOMETRIC_NONE_ENROLLED"
        override fun getUserMessage() = 
            "Configura tu huella digital en Ajustes → Seguridad primero"
    }

    /**
     * La autenticación biométrica falló.
     * 
     * Causas comunes:
     * - Huella no reconocida
     * - Demasiados intentos fallidos
     * - Sensor sucio
     */
    class AuthenticationFailed(
        message: String = "Autenticación biométrica falló"
    ) : BiometricException(message) {
        override val errorCode = "BIOMETRIC_AUTH_FAILED"
        override fun getUserMessage() = "No se pudo verificar tu identidad"
    }

    /**
     * El usuario canceló la autenticación.
     * 
     * No es un error grave, solo indica que el usuario presionó
     * el botón de cancelar o tocó fuera del diálogo.
     */
    class UserCancelled(
        message: String = "Autenticación cancelada por el usuario"
    ) : BiometricException(message) {
        override val errorCode = "BIOMETRIC_USER_CANCELLED"
        override fun getUserMessage() = "Autenticación cancelada"
    }

    /**
     * Error al generar o acceder a la clave criptográfica en KeyStore.
     * 
     * SEGURIDAD: Este error es crítico porque significa que no podemos
     * cifrar/descifrar datos de forma segura.
     */
    class CryptoError(
        message: String,
        cause: Throwable? = null
    ) : BiometricException(message, cause) {
        override val errorCode = "BIOMETRIC_CRYPTO_ERROR"
        override fun getUserMessage() = "Error de seguridad en el dispositivo"
    }

    /**
     * El dispositivo requiere una actualización de seguridad.
     */
    class SecurityUpdateRequired(
        message: String = "Se requiere actualización de seguridad"
    ) : BiometricException(message) {
        override val errorCode = "BIOMETRIC_SECURITY_UPDATE"
        override fun getUserMessage() = "Actualiza tu dispositivo para continuar"
    }

    /**
     * Timeout esperando la autenticación biométrica.
     */
    class Timeout(
        message: String = "Tiempo de espera agotado"
    ) : BiometricException(message) {
        override val errorCode = "BIOMETRIC_TIMEOUT"
        override fun getUserMessage() = "La autenticación tardó demasiado"
    }
}