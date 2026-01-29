package es.fjmarlop.corpsecauth.core.errors

/**
 * Excepciones relacionadas con Firebase.
 */
sealed class FirebaseException(
    message: String,
    cause: Throwable? = null
) : PasskeyAuthException(message, cause) {

    override val errorCode: String
        get() = when (this) {
            is UserNotFound -> "FIREBASE_USER_NOT_FOUND"
            is InvalidCredentials -> "FIREBASE_INVALID_CREDENTIALS"
            is AuthenticationFailed -> "FIREBASE_AUTH_FAILED"
            is PasswordChangeFailed -> "FIREBASE_PASSWORD_CHANGE_FAILED"
            is NetworkError -> "FIREBASE_NETWORK_ERROR"
            is DeviceAlreadyBound -> "FIREBASE_DEVICE_ALREADY_BOUND"
        }

    class UserNotFound(message: String) : FirebaseException(message)
    
    class InvalidCredentials(message: String) : FirebaseException(message)
    
    class AuthenticationFailed(message: String, cause: Throwable? = null) : FirebaseException(message, cause)
    
    class PasswordChangeFailed(message: String, cause: Throwable? = null) : FirebaseException(message, cause)
    
    class NetworkError(message: String, cause: Throwable? = null) : FirebaseException(message, cause)
    
    class DeviceAlreadyBound(deviceId: String) : FirebaseException(
        "Dispositivo $deviceId ya vinculado a otro usuario"
    )
}