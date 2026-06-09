package es.fjmarlop.corpsecauth.core.crypto

import javax.crypto.Cipher
import javax.crypto.SecretKey

/**
 * Contrato de gestion de claves criptograficas del SDK.
 *
 * Esta interfaz abstrae la frontera con [java.security.KeyStore] / AndroidKeyStore
 * para permitir testing JVM del [es.fjmarlop.corpsecauth.core.enrollment.EnrollmentManager]
 * y otros consumidores sin depender de hardware real.
 *
 * Ver ADR-010 para la justificacion arquitectonica y ADR-004 para las decisiones
 * criptograficas (AES-256-GCM, StrongBox/TEE).
 *
 * **Implementaciones:**
 * - [AndroidKeyStoreManager]: implementacion real con AndroidKeyStore (production).
 * - `FakeKeyStoreManager` (src/test/): fake configurable para tests JVM,
 *   capaz de simular StrongBox disponible/no disponible, clave invalidada, etc.
 *
 * **Garantias del contrato:**
 * - Las claves devueltas son hardware-backed en produccion.
 * - Toda operacion suspend respeta cancelacion de coroutines.
 * - Los errores se mapean a [es.fjmarlop.corpsecauth.core.errors.CryptoException]
 *   (no se propagan excepciones crudas).
 */
internal interface KeyStoreManager {

    /**
     * Genera una nueva clave AES en el KeyStore.
     *
     * Implementa la estrategia StrongBox→TEE definida en ADR-004:
     * 1. Intenta StrongBox (si `requireStrongBox = true`, falla si no disponible)
     * 2. Intenta StrongBox opcional (si falla, continua con paso 3)
     * 3. Usa TEE como fallback seguro
     *
     * @return [Result.success] con la clave generada o [Result.failure].
     */
    suspend fun generateKey(): Result<SecretKey>

    /**
     * Obtiene la clave existente del KeyStore.
     *
     * @return [Result.success] con la clave o [Result.failure] si no existe.
     */
    suspend fun getKey(): Result<SecretKey>

    /**
     * Obtiene la clave existente o genera una nueva si no existe.
     */
    suspend fun getOrCreateKey(): Result<SecretKey>

    /**
     * Obtiene un Cipher configurado para CIFRAR (modo ENCRYPT).
     *
     * IMPORTANTE: El cipher debe autenticarse con biometria antes de usarse
     * (via BiometricPrompt). Ver [BiometricAuthenticator.authenticateForEncryption].
     */
    suspend fun getEncryptCipher(): Result<Cipher>

    /**
     * Obtiene un Cipher configurado para DESCIFRAR (modo DECRYPT).
     *
     * @param iv Vector de inicializacion usado al cifrar.
     */
    suspend fun getDecryptCipher(iv: ByteArray): Result<Cipher>

    /**
     * Elimina la clave del KeyStore.
     *
     * Usado en logout, rollback de enrollment o re-enrollment.
     */
    suspend fun deleteKey(): Result<Unit>

    /**
     * Verifica si existe una clave en el KeyStore.
     */
    fun hasKey(): Boolean

    companion object {
        /**
         * Crea KeyStoreManager con configuracion por defecto.
         *
         * - Sin timeout de autenticacion (siempre requiere biometria)
         * - StrongBox opcional (usa TEE si no disponible)
         */
        fun createDefault(): KeyStoreManager = AndroidKeyStoreManager(
            userAuthenticationValiditySeconds = 0,
            requireStrongBox = false
        )

        /**
         * Crea KeyStoreManager con timeout de autenticacion.
         *
         * Despues de autenticar, la clave permanece desbloqueada
         * por el tiempo especificado.
         *
         * @param seconds Segundos que la clave permanece desbloqueada.
         */
        fun createWithTimeout(seconds: Int): KeyStoreManager = AndroidKeyStoreManager(
            userAuthenticationValiditySeconds = seconds,
            requireStrongBox = false
        )

        /**
         * Crea KeyStoreManager que REQUIERE StrongBox.
         *
         * Falla si el dispositivo no tiene StrongBox.
         * Solo para apps enterprise criticas.
         */
        fun createWithStrongBox(): KeyStoreManager = AndroidKeyStoreManager(
            userAuthenticationValiditySeconds = 0,
            requireStrongBox = true
        )
    }
}
