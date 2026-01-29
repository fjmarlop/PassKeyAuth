package es.fjmarlop.corpsecauth.core.errors

/**
 * Excepción base para todos los errores de PasskeyAuth SDK.
 *
 * Todas las excepciones del SDK heredan de esta clase, permitiendo
 * un manejo consistente de errores y facilitando el logging.
 *
 * @property message Descripción del error
 * @property cause Excepción original que causó este error (opcional)
 */
sealed class PasskeyAuthException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {

    /**
     * Devuelve un mensaje user-friendly del error.
     * 
     * Por defecto retorna [message], pero las subclases pueden
     * sobrescribir para proporcionar mensajes más específicos.
     */
    open fun getUserMessage(): String = message ?: "Error desconocido"

    /**
     * Código de error único para logging y telemetría.
     */
    abstract val errorCode: String
}