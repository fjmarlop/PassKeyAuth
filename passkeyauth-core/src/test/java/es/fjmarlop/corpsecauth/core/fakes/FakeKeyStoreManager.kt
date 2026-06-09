package es.fjmarlop.corpsecauth.core.fakes

import es.fjmarlop.corpsecauth.core.crypto.KeyStoreManager
import es.fjmarlop.corpsecauth.core.errors.CryptoException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Fake JVM de [KeyStoreManager] para tests de [EnrollmentManager] y otros consumidores.
 *
 * Simula el comportamiento de AndroidKeyStore sin tocar hardware. Permite reproducir
 * los modos de fallo definidos en ADR-004:
 * - StrongBox no disponible (cuando `requireStrongBox` esta activo)
 * - Clave invalidada por biometric enrollment change
 * - Errores de cifrado / descifrado
 *
 * **Patron de uso:**
 * ```kotlin
 * val fake = FakeKeyStoreManager()
 *
 * // Simular fallo en paso 3 del enrollment
 * fake.generateKeyResult = Result.failure(
 *     CryptoException.StrongBoxNotAvailable("test")
 * )
 *
 * // Inyectar y verificar
 * val manager = EnrollmentManager.createWithDependencies(
 *     keyStoreManager = fake, // ...
 * )
 *
 * assertThat(fake.generateKeyCallCount).isEqualTo(1)
 * assertThat(fake.deleteKeyCallCount).isEqualTo(0) // no se llego a paso de rollback
 * ```
 */
internal class FakeKeyStoreManager : KeyStoreManager {

    // === Configuracion ===

    /** Resultado de [generateKey]. `null` = generar clave software real. */
    var generateKeyResult: Result<SecretKey>? = null

    /** Resultado de [getKey]. `null` = devolver clave actual si existe. */
    var getKeyResult: Result<SecretKey>? = null

    /** Resultado de [deleteKey]. */
    var deleteKeyResult: Result<Unit> = Result.success(Unit)

    /** Resultado de [getEncryptCipher]. `null` = construir Cipher software real. */
    var encryptCipherResult: Result<Cipher>? = null

    /** Resultado de [getDecryptCipher]. `null` = construir Cipher software real. */
    var decryptCipherResult: Result<Cipher>? = null

    // === Estado interno ===

    private var currentKey: SecretKey? = null

    // === Contadores ===

    var generateKeyCallCount = 0
        private set
    var getKeyCallCount = 0
        private set
    var getOrCreateKeyCallCount = 0
        private set
    var deleteKeyCallCount = 0
        private set
    var hasKeyCallCount = 0
        private set
    var encryptCipherCallCount = 0
        private set
    var decryptCipherCallCount = 0
        private set

    val decryptCipherIvHistory: MutableList<ByteArray> = mutableListOf()

    // === Implementacion ===

    override suspend fun generateKey(): Result<SecretKey> {
        generateKeyCallCount++
        val result = generateKeyResult ?: Result.success(buildSoftwareKey())
        result.onSuccess { currentKey = it }
        return result
    }

    override suspend fun getKey(): Result<SecretKey> {
        getKeyCallCount++
        getKeyResult?.let { return it }
        return currentKey?.let { Result.success(it) }
            ?: Result.failure(CryptoException.KeyNotFound("Fake: no key"))
    }

    override suspend fun getOrCreateKey(): Result<SecretKey> {
        getOrCreateKeyCallCount++
        return if (hasKey()) getKey() else generateKey()
    }

    override suspend fun deleteKey(): Result<Unit> {
        deleteKeyCallCount++
        if (deleteKeyResult.isSuccess) currentKey = null
        return deleteKeyResult
    }

    override fun hasKey(): Boolean {
        hasKeyCallCount++
        return currentKey != null
    }

    override suspend fun getEncryptCipher(): Result<Cipher> {
        encryptCipherCallCount++
        encryptCipherResult?.let { return it }

        val key = currentKey ?: run {
            val newKey = buildSoftwareKey()
            currentKey = newKey
            newKey
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return Result.success(cipher)
    }

    override suspend fun getDecryptCipher(iv: ByteArray): Result<Cipher> {
        decryptCipherCallCount++
        decryptCipherIvHistory.add(iv)
        decryptCipherResult?.let { return it }

        val key = currentKey ?: return Result.failure(
            CryptoException.KeyNotFound("Fake: no key for decrypt")
        )
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = javax.crypto.spec.GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        return Result.success(cipher)
    }

    // === Helpers ===

    private fun buildSoftwareKey(): SecretKey {
        return KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    }

    /**
     * Helper de testing: forzar el estado "clave existe" sin generar una real.
     * Util para tests del path "ya hay enrollment".
     */
    fun forceKeyExists() {
        currentKey = buildSoftwareKey()
    }
}
