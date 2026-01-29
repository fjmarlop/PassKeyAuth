package es.fjmarlop.corpsecauth.core.errors

/**
 * Errores relacionados con el proceso de enrollment.
 */
sealed class EnrollmentException(
    message: String,
    cause: Throwable? = null
) : PasskeyAuthException(message, cause) {

    /**
     * Error durante el proceso de enrollment.
     */
    class EnrollmentFailed(
        message: String,
        cause: Throwable? = null
    ) : EnrollmentException(message, cause) {
        override val errorCode = "ENROLLMENT_FAILED"
        override fun getUserMessage() = "Error durante el registro del dispositivo"
    }

    /**
     * Enrollment cancelado por el usuario.
     */
    class EnrollmentCancelled(
        message: String = "Enrollment cancelado por el usuario"
    ) : EnrollmentException(message) {
        override val errorCode = "ENROLLMENT_CANCELLED"
        override fun getUserMessage() = "Registro cancelado"
    }

    /**
     * Dispositivo ya enrollado.
     */
    class AlreadyEnrolled(
        message: String = "Dispositivo ya enrollado"
    ) : EnrollmentException(message) {
        override val errorCode = "ENROLLMENT_ALREADY_ENROLLED"
        override fun getUserMessage() = "Este dispositivo ya esta registrado"
    }
}