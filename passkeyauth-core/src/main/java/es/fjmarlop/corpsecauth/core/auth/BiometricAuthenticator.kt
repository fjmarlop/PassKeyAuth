package es.fjmarlop.corpsecauth.core.auth

import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import es.fjmarlop.corpsecauth.core.crypto.KeyStoreManager
import es.fjmarlop.corpsecauth.core.errors.BiometricException
import es.fjmarlop.corpsecauth.core.models.BiometricConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import javax.crypto.Cipher
import kotlin.coroutines.resume

/**
 * Autenticador biometrico que integra BiometricPrompt con KeyStore.
 *
 * Esta clase es el corazon de la autenticacion passwordless. Maneja:
 * - Validacion de capacidades biometricas del dispositivo
 * - Presentacion de BiometricPrompt al usuario
 * - Integracion con Cipher protegido por biometria
 * - Conversion de callbacks a suspend functions
 *
 * SEGURIDAD: El Cipher solo se desbloquea DESPUES de autenticacion
 * biometrica exitosa. Esto garantiza que solo el usuario legitimo
 * puede cifrar/descifrar datos.
 *
 * Ejemplo de uso:
 * ```kotlin
 * val authenticator = BiometricAuthenticator(activity)
 *
 * // Para enrollment (generar clave + cifrar)
 * val cipher = authenticator.authenticateForEncryption(
 *     config = BiometricConfig.Enrollment
 * ).getOrThrow()
 *
 * // Para login (descifrar con clave existente)
 * val cipher = authenticator.authenticateForDecryption(
 *     iv = savedIV,
 *     config = BiometricConfig.Default
 * ).getOrThrow()
 * ```
 *
 * @property activity FragmentActivity para mostrar BiometricPrompt
 * @property keyStoreManager Gestor de claves (opcional, usa default si no se proporciona)
 */
internal class BiometricAuthenticator(
    private val activity: FragmentActivity,
    private val keyStoreManager: KeyStoreManager = KeyStoreManager.createDefault()
) {

    // COMPATIBILIDAD: mainExecutor requiere API 28
    // Para API 26-27 usamos ContextCompat.getMainExecutor
    private val executor: Executor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        activity.mainExecutor
    } else {
        // Fallback para API 26-27: ejecutar en main thread usando Handler
        MainThreadExecutor()
    }

    /**
     * Valida que el dispositivo tenga biometria disponible.
     *
     * SEGURIDAD: Esta validacion debe hacerse ANTES de intentar usar el SDK.
     * Si falla, la app no puede continuar.
     *
     * @return [Result.success] si biometria disponible, [Result.failure] con [BiometricException]
     */
    fun validateBiometricCapabilities(): Result<Unit> {
        val biometricManager = BiometricManager.from(activity)
        val canAuthenticate = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        )

        return when (canAuthenticate) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                println("✅ BiometricAuthenticator: Biometria STRONG disponible")
                Result.success(Unit)
            }

            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                println("❌ BiometricAuthenticator: Sin hardware biometrico")
                Result.failure(
                    BiometricException.HardwareNotAvailable(
                        "Este dispositivo no tiene sensor biometrico"
                    )
                )
            }

            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                println("⚠️ BiometricAuthenticator: Hardware no disponible")
                Result.failure(
                    BiometricException.HardwareUnavailable(
                        "El sensor biometrico no esta disponible temporalmente"
                    )
                )
            }

            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                println("⚠️ BiometricAuthenticator: Sin huellas registradas")
                Result.failure(
                    BiometricException.NoneEnrolled(
                        "No hay huellas digitales registradas en el dispositivo"
                    )
                )
            }

            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> {
                println("⚠️ BiometricAuthenticator: Actualizacion de seguridad requerida")
                Result.failure(
                    BiometricException.SecurityUpdateRequired(
                        "Se requiere actualizacion de seguridad del sistema"
                    )
                )
            }

            else -> {
                println("❌ BiometricAuthenticator: Biometria no disponible (codigo: $canAuthenticate)")
                Result.failure(
                    BiometricException.HardwareNotAvailable(
                        "Biometria no disponible (codigo: $canAuthenticate)"
                    )
                )
            }
        }
    }

    /**
     * Autentica al usuario para CIFRAR datos (enrollment).
     *
     * Flujo:
     * 1. Obtiene/crea clave en KeyStore
     * 2. Inicializa Cipher en modo ENCRYPT
     * 3. Muestra BiometricPrompt con CryptoObject
     * 4. Usuario autentica con huella
     * 5. Devuelve Cipher autenticado listo para cifrar
     *
     * SEGURIDAD: El Cipher devuelto YA ESTA AUTENTICADO.
     * No necesitas volver a autenticar para usarlo.
     *
     * @param config Configuracion del BiometricPrompt
     * @return [Result.success] con Cipher autenticado o [Result.failure] con [BiometricException]
     */
    suspend fun authenticateForEncryption(
        config: BiometricConfig = BiometricConfig.Default
    ): Result<Cipher> {
        println("🔐 BiometricAuthenticator: Iniciando autenticacion para cifrado")

        // Validar capacidades
        validateBiometricCapabilities().onFailure { error ->
            return Result.failure(error)
        }

        // Obtener cipher para cifrar
        val cipher = keyStoreManager.getEncryptCipher().getOrElse { error ->
            println("❌ BiometricAuthenticator: Error obteniendo cipher: ${error.message}")
            return Result.failure(
                BiometricException.CryptoError(
                    "Error preparando cifrado: ${error.message}",
                    error
                )
            )
        }

        // Autenticar con biometria
        return authenticateWithBiometric(cipher, config)
    }

    /**
     * Autentica al usuario para DESCIFRAR datos (login).
     *
     * Flujo:
     * 1. Obtiene clave existente de KeyStore
     * 2. Inicializa Cipher en modo DECRYPT con IV guardado
     * 3. Muestra BiometricPrompt con CryptoObject
     * 4. Usuario autentica con huella
     * 5. Devuelve Cipher autenticado listo para descifrar
     *
     * @param iv Vector de inicializacion guardado con los datos cifrados
     * @param config Configuracion del BiometricPrompt
     * @return [Result.success] con Cipher autenticado o [Result.failure] con [BiometricException]
     */
    suspend fun authenticateForDecryption(
        iv: ByteArray,
        config: BiometricConfig = BiometricConfig.Default
    ): Result<Cipher> {
        println("🔓 BiometricAuthenticator: Iniciando autenticacion para descifrado")

        // Validar capacidades
        validateBiometricCapabilities().onFailure { error ->
            return Result.failure(error)
        }

        // Obtener cipher para descifrar
        val cipher = keyStoreManager.getDecryptCipher(iv).getOrElse { error ->
            println("❌ BiometricAuthenticator: Error obteniendo cipher: ${error.message}")
            return Result.failure(
                BiometricException.CryptoError(
                    "Error preparando descifrado: ${error.message}",
                    error
                )
            )
        }

        // Autenticar con biometria
        return authenticateWithBiometric(cipher, config)
    }

    /**
     * Muestra BiometricPrompt y espera autenticacion del usuario.
     *
     * Esta funcion convierte el callback-based BiometricPrompt
     * en una suspend function que devuelve Result<Cipher>.
     *
     * SEGURIDAD: Usa suspendCancellableCoroutine para permitir
     * cancelacion limpia si el usuario sale de la pantalla.
     *
     * @param cipher Cipher inicializado (ENCRYPT o DECRYPT mode)
     * @param config Configuracion del dialogo
     * @return [Result.success] con Cipher autenticado o [Result.failure]
     */
    private suspend fun authenticateWithBiometric(
        cipher: Cipher,
        config: BiometricConfig
    ): Result<Cipher> = suspendCancellableCoroutine { continuation ->

        // Construir PromptInfo
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

        // Callback de autenticacion
        val authCallback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                println("✅ BiometricAuthenticator: Autenticacion exitosa")

                val authenticatedCipher = result.cryptoObject?.cipher
                if (authenticatedCipher != null) {
                    // Resumir coroutine con cipher autenticado
                    continuation.resume(Result.success(authenticatedCipher))
                } else {
                    // Esto no deberia pasar nunca, pero por si acaso
                    println("🚨 BiometricAuthenticator: Cipher null despues de autenticacion")
                    continuation.resume(
                        Result.failure(
                            BiometricException.CryptoError("Cipher no disponible despues de autenticacion")
                        )
                    )
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                println("❌ BiometricAuthenticator: Error de autenticacion ($errorCode): $errString")

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
                println("⚠️ BiometricAuthenticator: Intento fallido (usuario puede reintentar)")
            }
        }

        // Crear BiometricPrompt
        val biometricPrompt = BiometricPrompt(activity, executor, authCallback)

        // Cancelar prompt si la coroutine es cancelada
        continuation.invokeOnCancellation {
            println("🚫 BiometricAuthenticator: Autenticacion cancelada")
            biometricPrompt.cancelAuthentication()
        }

        // Mostrar prompt con CryptoObject
        try {
            val cryptoObject = BiometricPrompt.CryptoObject(cipher)
            biometricPrompt.authenticate(promptInfo, cryptoObject)
        } catch (e: Exception) {
            println("❌ BiometricAuthenticator: Error mostrando prompt: ${e.message}")
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

    /**
     * Elimina la clave del KeyStore.
     *
     * Usado en logout o cuando se requiere re-enrollment.
     */
    suspend fun deleteKey(): Result<Unit> {
        return keyStoreManager.deleteKey()
    }

    /**
     * Verifica si existe una clave en KeyStore.
     */
    fun hasKey(): Boolean {
        return keyStoreManager.hasKey()
    }

    /**
     * Executor que ejecuta en el main thread.
     * Compatibilidad para API 26-27.
     */
    private class MainThreadExecutor : Executor {
        private val handler = Handler(Looper.getMainLooper())

        override fun execute(command: Runnable) {
            handler.post(command)
        }
    }

    companion object {
        /**
         * Crea una instancia con KeyStoreManager por defecto.
         */
        fun create(activity: FragmentActivity) = BiometricAuthenticator(activity)

        /**
         * Crea una instancia con KeyStoreManager custom.
         *
         * Util para testing.
         */
        fun createWithKeyStore(
            activity: FragmentActivity,
            keyStoreManager: KeyStoreManager
        ) = BiometricAuthenticator(activity, keyStoreManager)
    }
}