package es.fjmarlop.corpsecauth.core.errors

/**
 * Errores relacionados con device binding y validación de dispositivos.
 */
sealed class DeviceException(
    message: String,
    cause: Throwable? = null
) : PasskeyAuthException(message, cause) {

    /**
     * Dispositivo no registrado para este usuario.
     */
    class NotEnrolled(
        message: String = "Dispositivo no registrado"
    ) : DeviceException(message) {
        override val errorCode = "DEVICE_NOT_ENROLLED"
        override fun getUserMessage() = "Este dispositivo no está autorizado"
    }

    /**
     * Dispositivo revocado remotamente por el administrador.
     * 
     * SEGURIDAD: Este es el caso de uso principal del device binding.
     * Si IT revoca un dispositivo, el usuario no podrá usarlo más.
     */
    class Revoked(
        message: String = "Dispositivo revocado"
    ) : DeviceException(message) {
        override val errorCode = "DEVICE_REVOKED"
        override fun getUserMessage() = 
            "Este dispositivo fue desactivado. Contacta con IT"
    }

    /**
     * Error al vincular el dispositivo en Firestore.
     */
    class BindingFailed(
        message: String = "Error al vincular dispositivo",
        cause: Throwable? = null
    ) : DeviceException(message, cause) {
        override val errorCode = "DEVICE_BINDING_FAILED"
        override fun getUserMessage() = "No se pudo registrar el dispositivo"
    }

    /**
     * Error al validar el dispositivo actual.
     */
    class ValidationFailed(
        message: String = "Error al validar dispositivo",
        cause: Throwable? = null
    ) : DeviceException(message, cause) {
        override val errorCode = "DEVICE_VALIDATION_FAILED"
        override fun getUserMessage() = "No se pudo verificar el dispositivo"
    }
}