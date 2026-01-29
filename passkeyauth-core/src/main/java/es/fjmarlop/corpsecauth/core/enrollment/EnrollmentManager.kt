package es.fjmarlop.corpsecauth.core.enrollment

import android.content.Context
import androidx.fragment.app.FragmentActivity
import es.fjmarlop.corpsecauth.core.auth.BiometricAuthenticator
import es.fjmarlop.corpsecauth.core.crypto.CryptoProvider
import es.fjmarlop.corpsecauth.core.crypto.KeyStoreManager
import es.fjmarlop.corpsecauth.core.errors.EnrollmentException
import es.fjmarlop.corpsecauth.core.errors.PasskeyAuthException
import es.fjmarlop.corpsecauth.core.firebase.DeviceBindingManager
import es.fjmarlop.corpsecauth.core.firebase.FirebaseAuthManager
import es.fjmarlop.corpsecauth.core.models.BiometricConfig
import es.fjmarlop.corpsecauth.core.models.EnrollmentState
import es.fjmarlop.corpsecauth.core.storage.SecureStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await

internal class EnrollmentManager private constructor(
    private val context: Context,
    private val activity: FragmentActivity,
    private val firebaseAuthManager: FirebaseAuthManager,
    private val biometricAuthenticator: BiometricAuthenticator,
    private val cryptoProvider: CryptoProvider,
    private val deviceBindingManager: DeviceBindingManager,
    private val secureStorage: SecureStorage,
    private val keyStoreManager: KeyStoreManager
) {

    fun enrollDevice(
        email: String,
        temporaryPassword: String
    ): Flow<EnrollmentState> = flow {
        try {
            emit(EnrollmentState.Idle)

            // PASO 1
            println("🔐 EnrollmentManager: Paso 1 - Validando credenciales")
            emit(EnrollmentState.ValidatingCredentials(email))

            val firebaseUser = firebaseAuthManager.loginWithTemporaryCredentials(
                email = email,
                temporaryPassword = temporaryPassword
            ).getOrElse { error ->
                emit(EnrollmentState.Error(wrapException(error)))
                return@flow
            }

            // PASO 2
            /** TODO  Paso 2 comentado para no cambiar constantemente la contraseña para pruebas **/
            /*
            println("🔐 EnrollmentManager: Paso 2 - Invalidando password temporal")
            emit(EnrollmentState.RequiresPasswordChange(isTemporaryPassword = true))

            firebaseAuthManager.invalidateTemporaryPassword().getOrElse { error ->
                firebaseAuthManager.signOut()
                emit(EnrollmentState.Error(wrapException(error)))
                return@flow
            }
            */
            // PASO 3
            println("🔐 EnrollmentManager: Paso 3 - Generando clave en KeyStore")
            emit(EnrollmentState.GeneratingCryptoKey)

            keyStoreManager.generateKey().getOrElse { error ->
                firebaseAuthManager.signOut()
                emit(EnrollmentState.Error(wrapException(error)))
                return@flow
            }

            // PASO 4
            println("🔐 EnrollmentManager: Paso 4 - Esperando autenticacion biometrica")
            emit(EnrollmentState.AwaitingBiometric(BiometricConfig.Default))

            val authenticatedCipher = biometricAuthenticator.authenticateForEncryption(
                config = BiometricConfig.Default
            ).getOrElse { error ->
                keyStoreManager.deleteKey()
                firebaseAuthManager.signOut()
                emit(EnrollmentState.Error(wrapException(error)))
                return@flow
            }

            // PASO 5
            println("🔐 EnrollmentManager: Paso 5 - Cifrando token de sesion")

            val token = firebaseUser.getIdToken(false).await()?.token ?: ""
            val ciphertext = authenticatedCipher.doFinal(token.toByteArray(Charsets.UTF_8))
            val iv = authenticatedCipher.iv

            val encryptedBase64 = android.util.Base64.encodeToString(
                iv + ciphertext,
                android.util.Base64.NO_WRAP
            )

            // PASO 6
            println("🔐 EnrollmentManager: Paso 6 - Vinculando dispositivo en Firestore")
            emit(EnrollmentState.BindingDevice)

            val deviceId = deviceBindingManager.bindDevice(firebaseUser.uid).getOrElse { error ->
                keyStoreManager.deleteKey()
                secureStorage.clear()
                firebaseAuthManager.signOut()
                emit(EnrollmentState.Error(wrapException(error)))
                return@flow
            }

            // PASO 7
            println("🔐 EnrollmentManager: Paso 7 - Guardando en storage local")

            secureStorage.saveEncryptedToken(encryptedBase64).getOrElse { error ->
                keyStoreManager.deleteKey()
                deviceBindingManager.revokeDevice(firebaseUser.uid)
                firebaseAuthManager.signOut()
                emit(EnrollmentState.Error(wrapException(error)))
                return@flow
            }

            // saveUserId, saveDeviceId, saveLastActivityTimestamp son suspend pero NO retornan Result
            secureStorage.saveUserId(firebaseUser.uid)
            secureStorage.saveDeviceId(deviceId)
            secureStorage.saveLastActivityTimestamp(System.currentTimeMillis())

            println("✅ EnrollmentManager: Enrollment completado exitosamente")

            val authUser = firebaseAuthManager.getCurrentUser()!!
            emit(EnrollmentState.Success(authUser))

        } catch (e: Exception) {
            println("❌ EnrollmentManager: Error inesperado: ${e.message}")
            emit(EnrollmentState.Error(wrapException(e)))
        }
    }

    suspend fun isDeviceEnrolled(): Boolean {
        val hasToken = secureStorage.hasStoredSession()
        val hasKey = keyStoreManager.hasKey()
        val userId = secureStorage.loadUserId().getOrNull()
        return hasToken && hasKey && userId != null
    }

    suspend fun validateEnrollment(): Result<Boolean> {
        return try {
            val hasToken = secureStorage.hasStoredSession()
            val hasKey = keyStoreManager.hasKey()
            val userId = secureStorage.loadUserId().getOrNull()
            val isValid = hasToken && hasKey && userId != null

            if (!isValid) {
                println("⚠️ EnrollmentManager: Enrollment incompleto - limpiando datos")
                keyStoreManager.deleteKey()
                secureStorage.clear()
            }

            Result.success(isValid)
        } catch (e: Exception) {
            println("❌ EnrollmentManager: Error validando enrollment: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun unenrollDevice(): Result<Unit> {
        return try {
            println("🗑️ EnrollmentManager: Eliminando enrollment")
            val userId = secureStorage.loadUserId().getOrNull()

            if (userId != null) {
                deviceBindingManager.revokeDevice(userId)
            }

            keyStoreManager.deleteKey()
            secureStorage.clear()
            firebaseAuthManager.signOut()

            println("✅ EnrollmentManager: Enrollment eliminado")
            Result.success(Unit)
        } catch (e: Exception) {
            println("❌ EnrollmentManager: Error eliminando enrollment: ${e.message}")
            Result.failure(e)
        }
    }

    private fun wrapException(throwable: Throwable): PasskeyAuthException {
        return when (throwable) {
            is PasskeyAuthException -> throwable
            else -> EnrollmentException.EnrollmentFailed(
                "Error en enrollment: ${throwable.message}",
                throwable
            )
        }
    }

    companion object {
        fun createWithDependencies(
            context: Context,
            activity: FragmentActivity,
            firebaseAuthManager: FirebaseAuthManager,
            biometricAuthenticator: BiometricAuthenticator,
            cryptoProvider: CryptoProvider,
            deviceBindingManager: DeviceBindingManager,
            secureStorage: SecureStorage,
            keyStoreManager: KeyStoreManager
        ): EnrollmentManager {
            return EnrollmentManager(
                context, activity, firebaseAuthManager, biometricAuthenticator,
                cryptoProvider, deviceBindingManager, secureStorage, keyStoreManager
            )
        }
    }
}