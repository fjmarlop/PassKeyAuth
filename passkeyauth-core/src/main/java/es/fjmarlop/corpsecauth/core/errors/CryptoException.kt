package es.fjmarlop.corpsecauth.core.errors

/**
 * Errores relacionados con operaciones criptográficas.
 */
sealed class CryptoException(
    message: String,
    cause: Throwable? = null
) : PasskeyAuthException(message, cause) {

    /**
     * Error al generar clave en KeyStore.
     */
    class KeyGenerationFailed(
        message: String = "Error al generar clave criptográfica",
        cause: Throwable? = null
    ) : CryptoException(message, cause) {
        override val errorCode = "CRYPTO_KEY_GENERATION_FAILED"
        override fun getUserMessage() = "Error de seguridad al configurar el dispositivo"
    }

    /**
     * Clave no encontrada en KeyStore.
     */
    class KeyNotFound(
        alias: String
    ) : CryptoException("Clave '$alias' no encontrada en KeyStore") {
        override val errorCode = "CRYPTO_KEY_NOT_FOUND"
        override fun getUserMessage() = "Configuración de seguridad no válida"
    }

    /**
     * Error al cifrar datos.
     */
    class EncryptionFailed(
        message: String = "Error al cifrar datos",
        cause: Throwable? = null
    ) : CryptoException(message, cause) {
        override val errorCode = "CRYPTO_ENCRYPTION_FAILED"
        override fun getUserMessage() = "No se pudieron proteger los datos"
    }

    /**
     * Error al descifrar datos.
     */
    class DecryptionFailed(
        message: String = "Error al descifrar datos",
        cause: Throwable? = null
    ) : CryptoException(message, cause) {
        override val errorCode = "CRYPTO_DECRYPTION_FAILED"
        override fun getUserMessage() = "No se pudieron recuperar los datos"
    }

    /**
     * StrongBox no disponible cuando es requerido.
     */
    class StrongBoxNotAvailable(
        message: String = "StrongBox no disponible en este dispositivo"
    ) : CryptoException(message) {
        override val errorCode = "CRYPTO_STRONGBOX_NOT_AVAILABLE"
        override fun getUserMessage() = "Este dispositivo no cumple requisitos de seguridad"
    }
}