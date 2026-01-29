package es.fjmarlop.corpsecauth.core.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import es.fjmarlop.corpsecauth.core.errors.CryptoException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Gestor de claves en Android KeyStore.
 *
 * Maneja la generacion, obtencion y eliminacion de claves AES-256-GCM
 * almacenadas en hardware (StrongBox o TEE).
 *
 * SEGURIDAD:
 * - Las claves NUNCA salen del KeyStore (hardware-backed)
 * - StrongBox proporciona aislamiento hardware completo (Pixel 3+, S9+)
 * - TEE (Trusted Execution Environment) es el fallback seguro
 * - Claves protegidas con biometria
 * - Invalidacion automatica si cambia la biometria
 *
 * @property userAuthenticationValiditySeconds Segundos que la clave permanece
 *           desbloqueada despues de autenticacion biometrica (0 = siempre requiere auth)
 * @property requireStrongBox Si true, falla si StrongBox no disponible
 */
internal class KeyStoreManager(
    private val userAuthenticationValiditySeconds: Int = 0,
    private val requireStrongBox: Boolean = false
) {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    /**
     * Genera una nueva clave AES en KeyStore.
     *
     * SEGURIDAD: La clave se protege con biometria y se almacena
     * en hardware siguiendo esta estrategia:
     * 1. Intenta StrongBox (si requireStrongBox = true, falla si no disponible)
     * 2. Intenta StrongBox opcional (si falla, continua con paso 3)
     * 3. Usa TEE como fallback seguro
     *
     * @return [Result.success] con la clave generada o [Result.failure]
     */
    suspend fun generateKey(): Result<SecretKey> = withContext(Dispatchers.IO) {
        try {
            // MODO 1: StrongBox OBLIGATORIO
            if (requireStrongBox) {
                println("ðŸ” KeyStoreManager: Modo StrongBox OBLIGATORIO")
                return@withContext generateKeyWithStrongBox().onFailure { error ->
                    println("âŒ KeyStoreManager: StrongBox no disponible pero es obligatorio")
                    return@withContext Result.failure(
                        CryptoException.StrongBoxNotAvailable(
                            "StrongBox requerido pero no disponible en este dispositivo"
                        )
                    )
                }
            }

            // MODO 2: StrongBox OPCIONAL con fallback a TEE
            println("ðŸ” KeyStoreManager: Intentando StrongBox con fallback a TEE...")
            
            // Intentar StrongBox primero
            val strongBoxResult = generateKeyWithStrongBox()
            if (strongBoxResult.isSuccess) {
                println("âœ… KeyStoreManager: Clave generada con StrongBox")
                return@withContext strongBoxResult
            }

            // Fallback a TEE
            println("âš ï¸ KeyStoreManager: StrongBox no disponible, usando TEE como fallback")
            val teeResult = generateKeyWithTEE()
            if (teeResult.isSuccess) {
                println("âœ… KeyStoreManager: Clave generada con TEE")
                return@withContext teeResult
            }

            // Si ambos fallan
            println("âŒ KeyStoreManager: Fallo tanto StrongBox como TEE")
            Result.failure(
                CryptoException.KeyGenerationFailed(
                    "No se pudo generar clave ni con StrongBox ni con TEE"
                )
            )

        } catch (e: Exception) {
            println("âŒ KeyStoreManager: Error inesperado generando clave: ${e.message}")
            Result.failure(
                CryptoException.KeyGenerationFailed("Error generando clave: ${e.message}", e)
            )
        }
    }

    /**
     * Intenta generar clave con StrongBox.
     *
     * SEGURIDAD: StrongBox proporciona:
     * - Chip dedicado separado del procesador principal
     * - Resistencia a ataques de canal lateral
     * - Proteccion contra glitching y voltage tampering
     *
     * Disponible en: Pixel 3+, Samsung S9+, algunos flagship recientes
     */
    private fun generateKeyWithStrongBox(): Result<SecretKey> {
        return try {
            // StrongBox requiere API 28+
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                return Result.failure(
                    Exception("StrongBox requiere Android 9.0 (API 28) o superior")
                )
            }

            println("ðŸ” KeyStoreManager: Generando clave con StrongBox...")

            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )

            val builder = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
                .setRandomizedEncryptionRequired(true)
                .setIsStrongBoxBacked(true) // â† STRONGBOX

            // Configurar timeout si estÃ¡ especificado
            if (userAuthenticationValiditySeconds > 0) {
                builder.setUserAuthenticationValidityDurationSeconds(userAuthenticationValiditySeconds)
            }

            keyGenerator.init(builder.build())
            val key = keyGenerator.generateKey()

            Result.success(key)

        } catch (e: Exception) {
            println("âš ï¸ KeyStoreManager: StrongBox fallÃ³: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Genera clave con TEE (Trusted Execution Environment).
     *
     * SEGURIDAD: TEE proporciona:
     * - Ejecucion aislada del OS principal
     * - Almacenamiento seguro de claves
     * - Proteccion contra malware del OS
     *
     * Disponible en: Practicamente todos los dispositivos Android modernos
     */
    private fun generateKeyWithTEE(): Result<SecretKey> {
        return try {
            println("ðŸ” KeyStoreManager: Generando clave con TEE...")

            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )

            val builder = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
                .setRandomizedEncryptionRequired(true)
                // NO setIsStrongBoxBacked â†’ usa TEE por defecto

            // Configurar timeout si estÃ¡ especificado
            if (userAuthenticationValiditySeconds > 0) {
                builder.setUserAuthenticationValidityDurationSeconds(userAuthenticationValiditySeconds)
            }

            keyGenerator.init(builder.build())
            val key = keyGenerator.generateKey()

            Result.success(key)

        } catch (e: Exception) {
            println("âŒ KeyStoreManager: TEE fallÃ³: ${e.message}")
            Result.failure(
                CryptoException.KeyGenerationFailed("Error generando clave con TEE: ${e.message}", e)
            )
        }
    }

    /**
     * Obtiene la clave existente del KeyStore.
     *
     * @return [Result.success] con la clave o [Result.failure] si no existe
     */
    suspend fun getKey(): Result<SecretKey> = withContext(Dispatchers.IO) {
        try {
            val key = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
                ?: return@withContext Result.failure(
                    CryptoException.KeyNotFound("Clave no encontrada en KeyStore")
                )

            Result.success(key)

        } catch (e: Exception) {
            println("âŒ KeyStoreManager: Error obteniendo clave: ${e.message}")
            Result.failure(
                CryptoException.KeyNotFound(KEY_ALIAS)
            )
        }
    }

    /**
     * Obtiene la clave existente o genera una nueva si no existe.
     *
     * @return [Result.success] con la clave o [Result.failure]
     */
    suspend fun getOrCreateKey(): Result<SecretKey> {
        return if (hasKey()) {
            getKey()
        } else {
            generateKey()
        }
    }

    /**
     * Obtiene un Cipher configurado para CIFRAR.
     *
     * IMPORTANTE: Este cipher debe autenticarse con biometria
     * antes de poder usarlo (via BiometricPrompt).
     *
     * @return [Result.success] con Cipher o [Result.failure]
     */
    suspend fun getEncryptCipher(): Result<Cipher> = withContext(Dispatchers.IO) {
        try {
            val key = getOrCreateKey().getOrElse { error ->
                return@withContext Result.failure(error)
            }

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)

            Result.success(cipher)

        } catch (e: Exception) {
            println("âŒ KeyStoreManager: Error creando cipher de cifrado: ${e.message}")
            Result.failure(
                CryptoException.EncryptionFailed("Error creando cipher: ${e.message}", e)
            )
        }
    }

    /**
     * Obtiene un Cipher configurado para DESCIFRAR.
     *
     * @param iv Vector de inicializacion usado al cifrar
     * @return [Result.success] con Cipher o [Result.failure]
     */
    suspend fun getDecryptCipher(iv: ByteArray): Result<Cipher> = withContext(Dispatchers.IO) {
        try {
            val key = getKey().getOrElse { error ->
                return@withContext Result.failure(error)
            }

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = javax.crypto.spec.GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)

            Result.success(cipher)

        } catch (e: Exception) {
            println("âŒ KeyStoreManager: Error creando cipher de descifrado: ${e.message}")
            Result.failure(
                CryptoException.DecryptionFailed("Error creando cipher: ${e.message}", e)
            )
        }
    }

    /**
     * Elimina la clave del KeyStore.
     *
     * Usado en logout o cuando se requiere re-enrollment.
     *
     * @return [Result.success] o [Result.failure]
     */
    suspend fun deleteKey(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (hasKey()) {
                keyStore.deleteEntry(KEY_ALIAS)
                println("ðŸ—‘ï¸ KeyStoreManager: Clave eliminada")
            }
            Result.success(Unit)

        } catch (e: Exception) {
            println("âŒ KeyStoreManager: Error eliminando clave: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Verifica si existe una clave en KeyStore.
     *
     * @return true si existe, false si no
     */
    fun hasKey(): Boolean {
        return keyStore.containsAlias(KEY_ALIAS)
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "passkeyauth_master_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"

        /**
         * Crea KeyStoreManager con configuracion por defecto.
         *
         * - Sin timeout (siempre requiere biometria)
         * - StrongBox opcional (usa TEE si no disponible)
         */
        fun createDefault() = KeyStoreManager(
            userAuthenticationValiditySeconds = 0,
            requireStrongBox = false
        )

        /**
         * Crea KeyStoreManager con timeout de autenticacion.
         *
         * Despues de autenticar, la clave permanece desbloqueada
         * por el tiempo especificado.
         *
         * @param seconds Segundos que la clave permanece desbloqueada
         */
        fun createWithTimeout(seconds: Int) = KeyStoreManager(
            userAuthenticationValiditySeconds = seconds,
            requireStrongBox = false
        )

        /**
         * Crea KeyStoreManager que REQUIERE StrongBox.
         *
         * Falla si el dispositivo no tiene StrongBox.
         * Solo para apps enterprise criticas.
         */
        fun createWithStrongBox() = KeyStoreManager(
            userAuthenticationValiditySeconds = 0,
            requireStrongBox = true
        )
    }
}