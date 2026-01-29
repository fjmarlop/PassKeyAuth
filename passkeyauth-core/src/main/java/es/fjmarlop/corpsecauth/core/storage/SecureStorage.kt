package es.fjmarlop.corpsecauth.core.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Almacenamiento seguro para tokens cifrados.
 *
 * Esta clase es un wrapper de DataStore Preferences que almacena
 * tokens de sesion en formato cifrado (Base64).
 *
 * SEGURIDAD:
 * - Los tokens se guardan YA CIFRADOS con AES-GCM
 * - DataStore se almacena en almacenamiento privado de la app
 * - No usa SharedPreferences (mas seguro y async)
 * - Los datos persisten entre reinicios de la app
 *
 * Flujo tipico:
 * ```kotlin
 * // Guardar token cifrado
 * val encrypted = cryptoProvider.encrypt(token).getOrThrow()
 * secureStorage.saveEncryptedToken(encrypted.toBase64String())
 *
 * // Cargar token cifrado
 * val base64 = secureStorage.loadEncryptedToken().getOrThrow()
 * val encrypted = EncryptedData.fromBase64String(base64)
 * val token = cryptoProvider.decrypt(encrypted).getOrThrow()
 * ```
 *
 * @property context Context de la aplicacion
 */
internal class SecureStorage(private val context: Context) {

    // Extension property para DataStore
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
        name = DATASTORE_NAME
    )

    /**
     * Guarda un token cifrado en DataStore.
     *
     * El token debe estar en formato Base64 (output de EncryptedData.toBase64String())
     *
     * @param encryptedTokenBase64 Token cifrado en Base64
     * @return [Result.success] o [Result.failure]
     */
    suspend fun saveEncryptedToken(encryptedTokenBase64: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            println("💾 SecureStorage: Guardando token cifrado")

            context.dataStore.edit { preferences ->
                preferences[KEY_ENCRYPTED_TOKEN] = encryptedTokenBase64
            }

            println("✅ SecureStorage: Token guardado exitosamente")
            Result.success(Unit)

        } catch (e: Exception) {
            println("❌ SecureStorage: Error guardando token: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Carga el token cifrado de DataStore.
     *
     * @return [Result.success] con el token en Base64, o null si no existe
     */
    suspend fun loadEncryptedToken(): Result<String?> = withContext(Dispatchers.IO) {
        try {
            val preferences = context.dataStore.data.first()
            val token = preferences[KEY_ENCRYPTED_TOKEN]

            if (token != null) {
                println("✅ SecureStorage: Token cargado")
            } else {
                println("⚠️ SecureStorage: No hay token guardado")
            }

            Result.success(token)

        } catch (e: Exception) {
            println("❌ SecureStorage: Error cargando token: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Observa cambios en el token cifrado.
     *
     * Util para reactive UI que necesita saber cuando cambia el token.
     *
     * @return Flow que emite el token cifrado cada vez que cambia
     */
    fun observeEncryptedToken(): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            preferences[KEY_ENCRYPTED_TOKEN]
        }
    }

    /**
     * Guarda el deviceId asociado al usuario.
     *
     * Esto permite validar rapidamente si el dispositivo actual
     * esta vinculado sin hacer request a Firestore.
     *
     * @param deviceId ID del dispositivo actual
     * @return [Result.success] o [Result.failure]
     */
    suspend fun saveDeviceId(deviceId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            println("💾 SecureStorage: Guardando deviceId")

            context.dataStore.edit { preferences ->
                preferences[KEY_DEVICE_ID] = deviceId
            }

            Result.success(Unit)

        } catch (e: Exception) {
            println("❌ SecureStorage: Error guardando deviceId: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Carga el deviceId guardado.
     *
     * @return [Result.success] con deviceId, o null si no existe
     */
    suspend fun loadDeviceId(): Result<String?> = withContext(Dispatchers.IO) {
        try {
            val preferences = context.dataStore.data.first()
            val deviceId = preferences[KEY_DEVICE_ID]

            Result.success(deviceId)

        } catch (e: Exception) {
            println("❌ SecureStorage: Error cargando deviceId: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Guarda el userId (uid de Firebase).
     *
     * Permite saber quien esta enrollado sin hacer request a Firebase.
     *
     * @param userId UID del usuario en Firebase
     * @return [Result.success] o [Result.failure]
     */
    suspend fun saveUserId(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            println("💾 SecureStorage: Guardando userId")

            context.dataStore.edit { preferences ->
                preferences[KEY_USER_ID] = userId
            }

            Result.success(Unit)

        } catch (e: Exception) {
            println("❌ SecureStorage: Error guardando userId: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Carga el userId guardado.
     *
     * @return [Result.success] con userId, o null si no existe
     */
    suspend fun loadUserId(): Result<String?> = withContext(Dispatchers.IO) {
        try {
            val preferences = context.dataStore.data.first()
            val userId = preferences[KEY_USER_ID]

            Result.success(userId)

        } catch (e: Exception) {
            println("❌ SecureStorage: Error cargando userId: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Guarda el timestamp de la ultima actividad.
     *
     * Usado para implementar timeout de sesion.
     *
     * @param timestampMillis Timestamp en milisegundos
     * @return [Result.success] o [Result.failure]
     */
    suspend fun saveLastActivityTimestamp(timestampMillis: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            context.dataStore.edit { preferences ->
                preferences[KEY_LAST_ACTIVITY] = timestampMillis.toString()
            }

            Result.success(Unit)

        } catch (e: Exception) {
            println("❌ SecureStorage: Error guardando timestamp: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Carga el timestamp de la ultima actividad.
     *
     * @return [Result.success] con timestamp, o null si no existe
     */
    suspend fun loadLastActivityTimestamp(): Result<Long?> = withContext(Dispatchers.IO) {
        try {
            val preferences = context.dataStore.data.first()
            val timestampStr = preferences[KEY_LAST_ACTIVITY]
            val timestamp = timestampStr?.toLongOrNull()

            Result.success(timestamp)

        } catch (e: Exception) {
            println("❌ SecureStorage: Error cargando timestamp: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Verifica si hay datos de sesion guardados.
     *
     * @return true si hay token cifrado guardado, false si no
     */
    suspend fun hasStoredSession(): Boolean {
        return loadEncryptedToken().getOrNull() != null
    }

    /**
     * Elimina todos los datos almacenados.
     *
     * Usado en logout para limpiar completamente el storage.
     *
     * IMPORTANTE: Esto NO elimina las claves del KeyStore.
     * Para eso debes llamar a KeyStoreManager.deleteKey()
     *
     * @return [Result.success] o [Result.failure]
     */
    suspend fun clear(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            println("🗑️ SecureStorage: Limpiando storage")

            context.dataStore.edit { preferences ->
                preferences.clear()
            }

            println("✅ SecureStorage: Storage limpiado")
            Result.success(Unit)

        } catch (e: Exception) {
            println("❌ SecureStorage: Error limpiando storage: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Elimina solo el token cifrado (mantiene deviceId y userId).
     *
     * Util para forzar re-autenticacion sin perder el enrollment.
     *
     * @return [Result.success] o [Result.failure]
     */
    suspend fun clearToken(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            println("🗑️ SecureStorage: Eliminando token")

            context.dataStore.edit { preferences ->
                preferences.remove(KEY_ENCRYPTED_TOKEN)
                preferences.remove(KEY_LAST_ACTIVITY)
            }

            Result.success(Unit)

        } catch (e: Exception) {
            println("❌ SecureStorage: Error eliminando token: ${e.message}")
            Result.failure(e)
        }
    }

    companion object {
        private const val DATASTORE_NAME = "passkeyauth_secure_storage"

        private val KEY_ENCRYPTED_TOKEN = stringPreferencesKey("encrypted_token")
        private val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        private val KEY_USER_ID = stringPreferencesKey("user_id")
        private val KEY_LAST_ACTIVITY = stringPreferencesKey("last_activity")

        /**
         * Crea una instancia de SecureStorage.
         */
        fun create(context: Context) = SecureStorage(context.applicationContext)
    }
}