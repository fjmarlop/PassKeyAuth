package es.fjmarlop.corpsecauth.core.crypto

import es.fjmarlop.corpsecauth.core.errors.CryptoException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.crypto.Cipher

/**
 * Proveedor de operaciones criptograficas de alto nivel.
 *
 * Esta clase simplifica el cifrado y descifrado de datos usando
 * KeyStoreManager. Maneja internamente la obtencion de Ciphers
 * y la ejecucion de operaciones doFinal().
 *
 * SEGURIDAD: Todas las operaciones se ejecutan en Dispatchers.IO
 * para no bloquear el hilo principal.
 *
 * Ejemplo de uso:
 * ```kotlin
 * val cryptoProvider = CryptoProvider()
 *
 * // Cifrar
 * val encrypted = cryptoProvider.encrypt("token_secreto").getOrThrow()
 *
 * // Descifrar
 * val decrypted = cryptoProvider.decrypt(encrypted).getOrThrow()
 * ```
 *
 * @property keyStoreManager Gestor de claves en KeyStore
 */
internal class CryptoProvider(
    private val keyStoreManager: KeyStoreManager = KeyStoreManager.createDefault()
) {

    /**
     * Cifra texto plano usando AES-GCM.
     *
     * SEGURIDAD:
     * - Usa clave hardware-backed del KeyStore
     * - Requiere autenticacion biometrica previa (clave debe estar desbloqueada)
     * - Genera IV aleatorio por operacion (randomizedEncryption)
     * - Devuelve ciphertext + IV para almacenamiento
     *
     * @param plaintext Texto a cifrar (ej: token de sesion)
     * @return [Result.success] con [EncryptedData] o [Result.failure] con [CryptoException]
     *
     * @see decrypt
     */
    suspend fun encrypt(plaintext: String): Result<EncryptedData> = withContext(Dispatchers.IO) {
        try {
            // Validar input
            if (plaintext.isBlank()) {
                return@withContext Result.failure(
                    CryptoException.EncryptionFailed("Texto vacio, no se puede cifrar")
                )
            }

            // Obtener cipher inicializado para encriptar
            val cipher = keyStoreManager.getEncryptCipher().getOrElse { error ->
                println("❌ CryptoProvider: Error obteniendo cipher: ${error.message}")
                return@withContext Result.failure(error)
            }

            // Cifrar
            val plaintextBytes = plaintext.toByteArray(Charsets.UTF_8)
            val ciphertext = cipher.doFinal(plaintextBytes)

            // Obtener IV generado (necesario para descifrar)
            val iv = cipher.iv
                ?: return@withContext Result.failure(
                    CryptoException.EncryptionFailed("IV no generado por el cipher")
                )

            val encryptedData = EncryptedData(ciphertext, iv)
            println("🔐 CryptoProvider: Datos cifrados (${ciphertext.size} bytes)")

            Result.success(encryptedData)

        } catch (e: Exception) {
            println("❌ CryptoProvider: Error en cifrado: ${e.message}")
            Result.failure(
                CryptoException.EncryptionFailed(
                    "Error al cifrar datos: ${e.message}",
                    e
                )
            )
        }
    }

    /**
     * Descifra datos previamente cifrados.
     *
     * SEGURIDAD:
     * - Requiere el IV original usado en el cifrado
     * - Valida autenticidad de los datos (GCM authentication tag)
     * - Si los datos fueron manipulados, doFinal() lanzara excepcion
     *
     * @param encryptedData Datos cifrados (ciphertext + IV)
     * @return [Result.success] con el texto original o [Result.failure] con [CryptoException]
     *
     * @see encrypt
     */
    suspend fun decrypt(encryptedData: EncryptedData): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Validar input
            if (encryptedData.ciphertext.isEmpty()) {
                return@withContext Result.failure(
                    CryptoException.DecryptionFailed("Ciphertext vacio")
                )
            }

            if (encryptedData.iv.isEmpty()) {
                return@withContext Result.failure(
                    CryptoException.DecryptionFailed("IV vacio")
                )
            }

            // Obtener cipher inicializado para descifrar
            val cipher = keyStoreManager.getDecryptCipher(encryptedData.iv).getOrElse { error ->
                println("❌ CryptoProvider: Error obteniendo cipher: ${error.message}")
                return@withContext Result.failure(error)
            }

            // Descifrar
            val plaintextBytes = cipher.doFinal(encryptedData.ciphertext)
            val plaintext = String(plaintextBytes, Charsets.UTF_8)

            println("🔓 CryptoProvider: Datos descifrados exitosamente")

            Result.success(plaintext)

        } catch (e: javax.crypto.AEADBadTagException) {
            // GCM detecta que los datos fueron manipulados
            println("🚨 CryptoProvider: Datos manipulados o IV incorrecto")
            Result.failure(
                CryptoException.DecryptionFailed(
                    "Datos cifrados manipulados o IV incorrecto",
                    e
                )
            )
        } catch (e: Exception) {
            println("❌ CryptoProvider: Error en descifrado: ${e.message}")
            Result.failure(
                CryptoException.DecryptionFailed(
                    "Error al descifrar datos: ${e.message}",
                    e
                )
            )
        }
    }

    /**
     * Cifra y convierte a Base64 para almacenamiento directo.
     *
     * Util para guardar en DataStore o SharedPreferences.
     *
     * @param plaintext Texto a cifrar
     * @return [Result.success] con string Base64 o [Result.failure]
     */
    suspend fun encryptToBase64(plaintext: String): Result<String> {
        return encrypt(plaintext).mapCatching { encryptedData ->
            encryptedData.toBase64String()
        }
    }

    /**
     * Descifra desde Base64.
     *
     * @param base64String String Base64 con IV + ciphertext
     * @return [Result.success] con texto original o [Result.failure]
     */
    suspend fun decryptFromBase64(base64String: String): Result<String> {
        val encryptedData = EncryptedData.fromBase64String(base64String)
            ?: return Result.failure(
                CryptoException.DecryptionFailed("String Base64 invalido")
            )

        return decrypt(encryptedData)
    }

    /**
     * Verifica si hay una clave disponible en KeyStore.
     *
     * @return true si existe clave, false si no
     */
    fun hasKey(): Boolean {
        return keyStoreManager.hasKey()
    }

    /**
     * Elimina la clave del KeyStore.
     *
     * Usado en logout o re-enrollment.
     *
     * @return [Result.success] si se elimino, [Result.failure] si fallo
     */
    suspend fun deleteKey(): Result<Unit> {
        return keyStoreManager.deleteKey()
    }

    companion object {
        /**
         * Crea una instancia con configuracion por defecto.
         */
        fun createDefault() = CryptoProvider()

        /**
         * Crea una instancia con KeyStoreManager custom.
         *
         * Util para testing con mocks.
         */
        fun createWithKeyStore(keyStoreManager: KeyStoreManager) = CryptoProvider(keyStoreManager)
    }
}