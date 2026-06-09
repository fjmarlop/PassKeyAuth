package es.fjmarlop.corpsecauth.core.fakes

import es.fjmarlop.corpsecauth.core.auth.BiometricAuthenticator
import es.fjmarlop.corpsecauth.core.errors.BiometricException
import es.fjmarlop.corpsecauth.core.models.BiometricConfig
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Fake JVM de [BiometricAuthenticator] para tests del [EnrollmentManager] y
 * otros consumidores.
 *
 * NO usa hardware ni Activity real. Los Ciphers devueltos son reales pero
 * inicializados con una clave AES generada en software (SunJCE provider).
 * Esto permite que el codigo bajo test ejecute `cipher.doFinal(...)` y `.iv`
 * sin instrumentacion Android.
 *
 * **Patron de uso:**
 * ```kotlin
 * val fake = FakeBiometricAuthenticator()
 *
 * // Configurar comportamiento
 * fake.capabilitiesResult = Result.success(Unit)
 * fake.encryptionResult = Result.failure(BiometricException.UserCancelled("cancelled"))
 *
 * // Inyectar
 * val manager = EnrollmentManager.createWithDependencies(
 *     biometricAuthenticator = fake,
 *     // ...
 * )
 *
 * // Verificar despues
 * assertThat(fake.encryptionCallCount).isEqualTo(1)
 * assertThat(fake.deleteKeyCallCount).isEqualTo(1) // por rollback
 * ```
 */
internal class FakeBiometricAuthenticator : BiometricAuthenticator {

    // === Configuracion del comportamiento ===

    /** Resultado que devolvera [validateBiometricCapabilities]. */
    var capabilitiesResult: Result<Unit> = Result.success(Unit)

    /**
     * Resultado que devolvera [authenticateForEncryption].
     * `null` significa "devolver Cipher real software-backed inicializado para ENCRYPT".
     */
    var encryptionResult: Result<Cipher>? = null

    /**
     * Resultado que devolvera [authenticateForDecryption].
     * `null` significa "devolver Cipher real software-backed inicializado para DECRYPT".
     */
    var decryptionResult: Result<Cipher>? = null

    /** Resultado que devolvera [deleteKey]. */
    var deleteKeyResult: Result<Unit> = Result.success(Unit)

    /** Estado interno: si "existe" clave. */
    var keyExistsState: Boolean = false

    // === Contadores para verificacion ===

    var validateCapabilitiesCallCount = 0
        private set
    var encryptionCallCount = 0
        private set
    var decryptionCallCount = 0
        private set
    var deleteKeyCallCount = 0
        private set
    var hasKeyCallCount = 0
        private set

    /** Argumentos recibidos en [authenticateForDecryption], en orden de llamada. */
    val decryptionIvHistory: MutableList<ByteArray> = mutableListOf()

    // === Estado del cipher de software (para retornos por defecto) ===

    private val softwareKey: SecretKey by lazy {
        KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    }

    // === Implementacion ===

    override fun validateBiometricCapabilities(): Result<Unit> {
        validateCapabilitiesCallCount++
        return capabilitiesResult
    }

    override suspend fun authenticateForEncryption(config: BiometricConfig): Result<Cipher> {
        encryptionCallCount++
        return encryptionResult ?: Result.success(buildSoftwareEncryptCipher())
    }

    override suspend fun authenticateForDecryption(
        iv: ByteArray,
        config: BiometricConfig
    ): Result<Cipher> {
        decryptionCallCount++
        decryptionIvHistory.add(iv)
        return decryptionResult ?: Result.success(buildSoftwareDecryptCipher(iv))
    }

    override suspend fun deleteKey(): Result<Unit> {
        deleteKeyCallCount++
        if (deleteKeyResult.isSuccess) keyExistsState = false
        return deleteKeyResult
    }

    override fun hasKey(): Boolean {
        hasKeyCallCount++
        return keyExistsState
    }

    // === Helpers para construir Ciphers reales software-backed ===

    private fun buildSoftwareEncryptCipher(): Cipher {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, softwareKey)
        return cipher
    }

    private fun buildSoftwareDecryptCipher(iv: ByteArray): Cipher {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = javax.crypto.spec.GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, softwareKey, spec)
        return cipher
    }
}
