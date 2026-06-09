package es.fjmarlop.corpsecauth.core.auth

import androidx.fragment.app.FragmentActivity
import es.fjmarlop.corpsecauth.core.crypto.KeyStoreManager
import es.fjmarlop.corpsecauth.core.errors.BiometricException
import es.fjmarlop.corpsecauth.core.models.BiometricConfig
import javax.crypto.Cipher

/**
 * Contrato de autenticacion biometrica del SDK.
 *
 * Esta interfaz abstrae la frontera con [androidx.biometric.BiometricPrompt]
 * para permitir testing JVM del [es.fjmarlop.corpsecauth.core.enrollment.EnrollmentManager]
 * y otros consumidores sin depender de hardware ni Activity real.
 *
 * Ver ADR-010 para la justificacion arquitectonica.
 *
 * **Implementaciones:**
 * - [AndroidBiometricAuthenticator]: implementacion real con BiometricPrompt (production).
 * - `FakeBiometricAuthenticator` (src/test/): fake configurable para tests JVM.
 *
 * **Garantias del contrato:**
 * - Todo Cipher devuelto por [authenticateForEncryption] / [authenticateForDecryption]
 *   esta AUTENTICADO. No requiere re-autenticacion.
 * - Los errores se mapean a [BiometricException] (no se propagan excepciones crudas).
 * - Las funciones suspend son cancelables: cancelar la coroutine cancela el prompt.
 */
internal interface BiometricAuthenticator {

    /**
     * Valida que el dispositivo tenga biometria STRONG (Class 3) disponible.
     *
     * Debe llamarse ANTES de [authenticateForEncryption] o [authenticateForDecryption].
     *
     * @return [Result.success] si biometria disponible, [Result.failure] con [BiometricException].
     */
    fun validateBiometricCapabilities(): Result<Unit>

    /**
     * Autentica al usuario para CIFRAR datos (flujo de enrollment).
     *
     * Flujo:
     * 1. Obtiene/crea clave en KeyStore
     * 2. Inicializa Cipher en modo ENCRYPT
     * 3. Muestra BiometricPrompt con CryptoObject
     * 4. Usuario autentica con huella
     * 5. Devuelve Cipher autenticado listo para cifrar
     *
     * @param config Configuracion del BiometricPrompt (titulo, subtitulo, etc.).
     * @return [Result.success] con Cipher autenticado o [Result.failure] con [BiometricException].
     */
    suspend fun authenticateForEncryption(
        config: BiometricConfig = BiometricConfig.Default
    ): Result<Cipher>

    /**
     * Autentica al usuario para DESCIFRAR datos (flujo de login).
     *
     * Flujo:
     * 1. Obtiene clave existente de KeyStore
     * 2. Inicializa Cipher en modo DECRYPT con IV guardado
     * 3. Muestra BiometricPrompt con CryptoObject
     * 4. Usuario autentica con huella
     * 5. Devuelve Cipher autenticado listo para descifrar
     *
     * @param iv Vector de inicializacion guardado con los datos cifrados.
     * @param config Configuracion del BiometricPrompt.
     * @return [Result.success] con Cipher autenticado o [Result.failure] con [BiometricException].
     */
    suspend fun authenticateForDecryption(
        iv: ByteArray,
        config: BiometricConfig = BiometricConfig.Default
    ): Result<Cipher>

    /**
     * Elimina la clave del KeyStore.
     *
     * Usado en logout, rollback de enrollment o cuando se requiere re-enrollment.
     */
    suspend fun deleteKey(): Result<Unit>

    /**
     * Verifica si existe una clave en KeyStore.
     */
    fun hasKey(): Boolean

    companion object {
        /**
         * Crea una instancia con KeyStoreManager por defecto.
         *
         * Equivale a la implementacion Android para uso en produccion.
         */
        fun create(activity: FragmentActivity): BiometricAuthenticator =
            AndroidBiometricAuthenticator(activity)

        /**
         * Crea una instancia con KeyStoreManager custom.
         *
         * Util para inyectar un KeyStoreManager configurado (ej. con StrongBox requerido).
         */
        fun createWithKeyStore(
            activity: FragmentActivity,
            keyStoreManager: KeyStoreManager
        ): BiometricAuthenticator =
            AndroidBiometricAuthenticator(activity, keyStoreManager)
    }
}
