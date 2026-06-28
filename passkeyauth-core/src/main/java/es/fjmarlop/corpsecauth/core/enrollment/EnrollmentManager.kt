package es.fjmarlop.corpsecauth.core.enrollment

import android.content.Context
import androidx.fragment.app.FragmentActivity
import es.fjmarlop.corpsecauth.core.auth.BiometricAuthenticator
import es.fjmarlop.corpsecauth.core.crypto.CryptoProvider
import es.fjmarlop.corpsecauth.core.crypto.KeyStoreManager
import es.fjmarlop.corpsecauth.core.errors.CryptoException
import es.fjmarlop.corpsecauth.core.errors.EnrollmentException
import es.fjmarlop.corpsecauth.core.errors.PasskeyAuthException
import es.fjmarlop.corpsecauth.AuthBackend
import es.fjmarlop.corpsecauth.Credentials
import es.fjmarlop.corpsecauth.DeviceRegistry
import es.fjmarlop.corpsecauth.PasswordManagementBackend
import es.fjmarlop.corpsecauth.core.models.BiometricConfig
import es.fjmarlop.corpsecauth.core.models.EnrollmentState
import es.fjmarlop.corpsecauth.core.storage.SecureStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

internal class EnrollmentManager private constructor(
    private val context: Context,
    private val activity: FragmentActivity,
    private val authBackend: AuthBackend,
    private val passwordManagement: PasswordManagementBackend,
    private val biometricAuthenticator: BiometricAuthenticator,
    private val cryptoProvider: CryptoProvider,
    private val deviceRegistry: DeviceRegistry,
    private val secureStorage: SecureStorage,
    private val keyStoreManager: KeyStoreManager
) {

    fun enrollDevice(
        email: String,
        temporaryPassword: String
    ): Flow<EnrollmentState> = flow {
        try {
            emit(EnrollmentState.Idle)

            // PASO 1: Validar credenciales (login + obtener ID token, atomico)
            emit(EnrollmentState.ValidatingCredentials(email))

            val session = authBackend.authenticate(
                Credentials.EmailPassword(email = email, password = temporaryPassword)
            ).getOrElse { error ->
                emit(EnrollmentState.Error(wrapException(error)))
                return@flow
            }

            // PASO 2: Invalidar password temporal (passwordless real)
            emit(EnrollmentState.RequiresPasswordChange(isTemporaryPassword = true))

            passwordManagement.invalidateTemporaryPassword().getOrElse { error ->
                authBackend.signOut()
                emit(EnrollmentState.Error(wrapException(error)))
                return@flow
            }

            // PASO 3: Generar clave en KeyStore
            emit(EnrollmentState.GeneratingCryptoKey)

            keyStoreManager.generateKey().getOrElse { error ->
                authBackend.signOut()
                emit(EnrollmentState.Error(wrapException(error)))
                return@flow
            }

            // PASO 4: Autenticacion biometrica
            emit(EnrollmentState.AwaitingBiometric(BiometricConfig.Default))

            val authenticatedCipher = biometricAuthenticator.authenticateForEncryption(
                config = BiometricConfig.Default
            ).getOrElse { error ->
                keyStoreManager.deleteKey()
                authBackend.signOut()
                emit(EnrollmentState.Error(wrapException(error)))
                return@flow
            }

            // PASO 5: Cifrar token de sesion
            //
            // Rollback explicito (ADR-006): si cipher.doFinal lanza (cipher invalidado,
            // BadPaddingException, etc.) hay que limpiar la clave generada en paso 3 y
            // cerrar la sesion Firebase del paso 1. Sin este try/catch, la excepcion
            // caia al catch outer que solo emitia Error sin rollback — violacion de la
            // garantia "todo o nada" del enrollment transaccional.

            val encryptedBase64 = try {
                val token = session.idToken
                // Plaintext del token: lo borramos del heap tras cifrar (ADR-015, bloque D).
                val tokenBytes = token.toByteArray(Charsets.UTF_8)
                val ciphertext = authenticatedCipher.doFinal(tokenBytes)
                tokenBytes.fill(0)
                val iv = authenticatedCipher.iv

                // Usamos java.util.Base64 (no android.util.Base64) para portabilidad JVM.
                // Disponible desde API 26 = nuestro minSdk. Ver ADR-011.
                java.util.Base64.getEncoder().encodeToString(iv + ciphertext)
            } catch (e: Exception) {
                keyStoreManager.deleteKey()
                authBackend.signOut()
                emit(
                    EnrollmentState.Error(
                        CryptoException.EncryptionFailed(
                            "Cifrado fallo en paso 5: ${e.message}",
                            e
                        )
                    )
                )
                return@flow
            }

            // PASO 6: Vincular dispositivo en registry remoto
            emit(EnrollmentState.BindingDevice)

            val deviceId = deviceRegistry.bindDevice(session.user.uid).getOrElse { error ->
                keyStoreManager.deleteKey()
                secureStorage.clear()
                authBackend.signOut()
                emit(EnrollmentState.Error(wrapException(error)))
                return@flow
            }

            // PASO 7: Guardar en storage local cifrado

            secureStorage.saveEncryptedToken(encryptedBase64).getOrElse { error ->
                keyStoreManager.deleteKey()
                deviceRegistry.revokeDevice(session.user.uid)
                authBackend.signOut()
                emit(EnrollmentState.Error(wrapException(error)))
                return@flow
            }

            // saveUserId, saveDeviceId, saveLastActivityTimestamp son suspend pero NO retornan Result
            secureStorage.saveUserId(session.user.uid)
            secureStorage.saveDeviceId(deviceId)
            secureStorage.saveLastActivityTimestamp(System.currentTimeMillis())

            emit(EnrollmentState.Success(session.user))

        } catch (e: Exception) {
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
                keyStoreManager.deleteKey()
                secureStorage.clear()
            }

            Result.success(isValid)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun unenrollDevice(): Result<Unit> {
        return try {
            val userId = secureStorage.loadUserId().getOrNull()

            if (userId != null) {
                deviceRegistry.revokeDevice(userId)
            }

            keyStoreManager.deleteKey()
            secureStorage.clear()
            authBackend.signOut()

            Result.success(Unit)
        } catch (e: Exception) {
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
            authBackend: AuthBackend,
            passwordManagement: PasswordManagementBackend,
            biometricAuthenticator: BiometricAuthenticator,
            cryptoProvider: CryptoProvider,
            deviceRegistry: DeviceRegistry,
            secureStorage: SecureStorage,
            keyStoreManager: KeyStoreManager
        ): EnrollmentManager {
            return EnrollmentManager(
                context, activity, authBackend, passwordManagement, biometricAuthenticator,
                cryptoProvider, deviceRegistry, secureStorage, keyStoreManager
            )
        }
    }
}
