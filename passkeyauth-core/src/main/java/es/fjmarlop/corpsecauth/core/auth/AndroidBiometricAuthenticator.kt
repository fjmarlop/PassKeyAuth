package es.fjmarlop.corpsecauth.core.auth

import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import es.fjmarlop.corpsecauth.PasskeyCapability
import es.fjmarlop.corpsecauth.core.crypto.KeyStoreManager
import es.fjmarlop.corpsecauth.core.errors.BiometricException
import es.fjmarlop.corpsecauth.core.models.BiometricConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import javax.crypto.Cipher
import kotlin.coroutines.resume

/**
 * Mapea el código de [BiometricManager.canAuthenticate] a [PasskeyCapability].
 * Fuente única de verdad compartida entre la ceremonia (lanzante) y
 * PasskeyAuth.checkCapability() (no-lanzante). Ver ADR-013.
 */
internal fun mapCanAuthenticateToCapability(canAuthenticate: Int): PasskeyCapability =
    when (canAuthenticate) {
        BiometricManager.BIOMETRIC_SUCCESS -> PasskeyCapability.Ready
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> PasskeyCapability.NotEnrolled
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> PasskeyCapability.NoHardware
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> PasskeyCapability.TemporarilyUnavailable
        BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> PasskeyCapability.SecurityUpdateRequired
        else -> PasskeyCapability.NoHardware
    }

/**
 * Implementacion Android de [BiometricAuthenticator] basada en [BiometricPrompt].
 *
 * Esta es la implementacion REAL que corre en device. Integra BiometricPrompt
 * con KeyStore y convierte el callback-based API en suspend functions.
 *
 * Para tests JVM, usar `FakeBiometricAuthenticator` (src/test/).
 *
 * SEGURIDAD: El Cipher solo se desbloquea DESPUES de autenticacion biometrica
 * exitosa. Esto garantiza que solo el usuario legitimo puede cifrar/descifrar datos.
 *
 * @property activity FragmentActivity para mostrar BiometricPrompt (requerido por ADR-007)
 * @property keyStoreManager Gestor de claves (opcional, usa default si no se proporciona)
 */
internal class AndroidBiometricAuthenticator(
    private val activity: FragmentActivity,
    private val keyStoreManager: KeyStoreManager = KeyStoreManager.createDefault()
) : BiometricAuthenticator {

    // COMPATIBILIDAD: mainExecutor requiere API 28
    // Para API 26-27 usamos Handler en el main thread
    private val executor: Executor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        activity.mainExecutor
    } else {
        MainThreadExecutor()
    }

    override fun validateBiometricCapabilities(): Result<Unit> {
        val biometricManager = BiometricManager.from(activity)
        val canAuthenticate = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        )

        return when (mapCanAuthenticateToCapability(canAuthenticate)) {
            PasskeyCapability.Ready -> {
                Result.success(Unit)
            }
            PasskeyCapability.NoHardware -> {
                Result.failure(
                    BiometricException.HardwareNotAvailable(
                        "Este dispositivo no tiene sensor biometrico"
                    )
                )
            }
            PasskeyCapability.TemporarilyUnavailable -> {
                Result.failure(
                    BiometricException.HardwareUnavailable(
                        "El sensor biometrico no esta disponible temporalmente"
                    )
                )
            }
            PasskeyCapability.NotEnrolled -> {
                Result.failure(
                    BiometricException.NoneEnrolled(
                        "No hay huellas digitales registradas en el dispositivo"
                    )
                )
            }
            PasskeyCapability.SecurityUpdateRequired -> {
                Result.failure(
                    BiometricException.SecurityUpdateRequired(
                        "Se requiere actualizacion de seguridad del sistema"
                    )
                )
            }
        }
    }

    override suspend fun authenticateForEncryption(
        config: BiometricConfig
    ): Result<Cipher> {
        validateBiometricCapabilities().onFailure { error ->
            return Result.failure(error)
        }

        val cipher = keyStoreManager.getEncryptCipher().getOrElse { error ->
            return Result.failure(
                BiometricException.CryptoError(
                    "Error preparando cifrado: ${error.message}",
                    error
                )
            )
        }

        return authenticateWithBiometric(cipher, config)
    }

    override suspend fun authenticateForDecryption(
        iv: ByteArray,
        config: BiometricConfig
    ): Result<Cipher> {
        validateBiometricCapabilities().onFailure { error ->
            return Result.failure(error)
        }

        val cipher = keyStoreManager.getDecryptCipher(iv).getOrElse { error ->
            return Result.failure(
                BiometricException.CryptoError(
                    "Error preparando descifrado: ${error.message}",
                    error
                )
            )
        }

        return authenticateWithBiometric(cipher, config)
    }

    /**
     * Muestra BiometricPrompt y espera autenticacion del usuario.
     *
     * Convierte el callback-based BiometricPrompt en una suspend function
     * que devuelve Result<Cipher>.
     *
     * SEGURIDAD: Usa suspendCancellableCoroutine para permitir
     * cancelacion limpia si el usuario sale de la pantalla.
     */
    private suspend fun authenticateWithBiometric(
        cipher: Cipher,
        config: BiometricConfig
    ): Result<Cipher> = suspendCancellableCoroutine { continuation ->

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(config.title)
            .apply {
                config.subtitle?.let { setSubtitle(it) }
                config.description?.let { setDescription(it) }
            }
            .setNegativeButtonText(config.negativeButtonText)
            .setConfirmationRequired(config.confirmationRequired)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        val authCallback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val authenticatedCipher = result.cryptoObject?.cipher
                if (authenticatedCipher != null) {
                    continuation.resume(Result.success(authenticatedCipher))
                } else {
                    continuation.resume(
                        Result.failure(
                            BiometricException.CryptoError("Cipher no disponible despues de autenticacion")
                        )
                    )
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val exception = when (errorCode) {
                    BiometricPrompt.ERROR_HW_UNAVAILABLE,
                    BiometricPrompt.ERROR_HW_NOT_PRESENT -> {
                        BiometricException.HardwareNotAvailable(errString.toString())
                    }

                    BiometricPrompt.ERROR_NO_BIOMETRICS -> {
                        BiometricException.NoneEnrolled(errString.toString())
                    }

                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON -> {
                        BiometricException.UserCancelled(errString.toString())
                    }

                    BiometricPrompt.ERROR_TIMEOUT -> {
                        BiometricException.Timeout(errString.toString())
                    }

                    BiometricPrompt.ERROR_LOCKOUT,
                    BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> {
                        BiometricException.AuthenticationFailed(
                            "Demasiados intentos fallidos. Dispositivo bloqueado."
                        )
                    }

                    else -> {
                        BiometricException.AuthenticationFailed(errString.toString())
                    }
                }

                continuation.resume(Result.failure(exception))
            }

            override fun onAuthenticationFailed() {
                // No resumir aqui - el usuario puede reintentar
            }
        }

        val biometricPrompt = BiometricPrompt(activity, executor, authCallback)

        continuation.invokeOnCancellation {
            biometricPrompt.cancelAuthentication()
        }

        try {
            val cryptoObject = BiometricPrompt.CryptoObject(cipher)
            biometricPrompt.authenticate(promptInfo, cryptoObject)
        } catch (e: Exception) {
            continuation.resume(
                Result.failure(
                    BiometricException.CryptoError(
                        "Error mostrando dialogo biometrico: ${e.message}",
                        e
                    )
                )
            )
        }
    }

    override suspend fun deleteKey(): Result<Unit> {
        return keyStoreManager.deleteKey()
    }

    override fun hasKey(): Boolean {
        return keyStoreManager.hasKey()
    }

    /**
     * Executor que ejecuta en el main thread.
     * Compatibilidad para API 26-27 (mainExecutor requiere API 28).
     */
    private class MainThreadExecutor : Executor {
        private val handler = Handler(Looper.getMainLooper())

        override fun execute(command: Runnable) {
            handler.post(command)
        }
    }
}
