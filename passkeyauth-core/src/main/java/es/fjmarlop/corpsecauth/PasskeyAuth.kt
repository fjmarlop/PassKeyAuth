package es.fjmarlop.corpsecauth

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.fragment.app.FragmentActivity
import es.fjmarlop.corpsecauth.core.auth.BiometricAuthenticator
import es.fjmarlop.corpsecauth.core.auth.mapCanAuthenticateToCapability
import es.fjmarlop.corpsecauth.core.crypto.CryptoProvider
import es.fjmarlop.corpsecauth.core.crypto.EncryptedData
import es.fjmarlop.corpsecauth.core.crypto.KeyStoreManager
import es.fjmarlop.corpsecauth.core.enrollment.EnrollmentManager
import es.fjmarlop.corpsecauth.core.errors.PasskeyAuthException
import es.fjmarlop.corpsecauth.core.firebase.AuthBackend
import es.fjmarlop.corpsecauth.core.firebase.DeviceRegistry
import es.fjmarlop.corpsecauth.core.firebase.FirebaseAuthBackend
import es.fjmarlop.corpsecauth.core.firebase.PasswordManagementBackend
import es.fjmarlop.corpsecauth.core.models.AuthResult
import es.fjmarlop.corpsecauth.core.models.AuthUser
import es.fjmarlop.corpsecauth.core.models.BiometricConfig
import es.fjmarlop.corpsecauth.core.models.EnrollmentState
import es.fjmarlop.corpsecauth.core.storage.SecureStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object PasskeyAuth {

    private var isInitialized = false
    private var appContext: Context? = null
    private var config: PasskeyAuthConfig? = null
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Composicion root: una sola instancia Firebase sirve ambas capabilities
    // (autenticacion y gestion de password). Se expone como dos getters tipados
    // a las interfaces correspondientes (ver ADR-010 Path C).
    //
    // Estas propiedades usan el patron "backing field nullable + getter" en lugar
    // de `by lazy` para que reset() pueda nulificarlas entre tests (ADR-011).
    // La semantica es identica: inicializacion diferida en el primer acceso.
    // Thread-safety: se asume acceso desde el hilo principal durante initialize().
    private var _firebaseAuthBackend: FirebaseAuthBackend? = null
    private val firebaseAuthBackend: FirebaseAuthBackend
        get() = _firebaseAuthBackend ?: FirebaseAuthBackend.createDefault().also { _firebaseAuthBackend = it }

    private val authBackend: AuthBackend get() = firebaseAuthBackend
    private val passwordManagement: PasswordManagementBackend get() = firebaseAuthBackend

    private var _keyStoreManager: KeyStoreManager? = null
    private val keyStoreManager: KeyStoreManager
        get() = _keyStoreManager ?: createKeyStoreManager().also { _keyStoreManager = it }

    private var _cryptoProvider: CryptoProvider? = null
    private val cryptoProvider: CryptoProvider
        get() = _cryptoProvider ?: CryptoProvider.createWithKeyStore(keyStoreManager).also { _cryptoProvider = it }

    private var _secureStorage: SecureStorage? = null
    private val secureStorage: SecureStorage
        get() = _secureStorage ?: SecureStorage.create(requireContext()).also { _secureStorage = it }

    private var _deviceRegistry: DeviceRegistry? = null
    private val deviceRegistry: DeviceRegistry
        get() = _deviceRegistry ?: DeviceRegistry.create(requireContext()).also { _deviceRegistry = it }

    private var lastActivityTimestamp: Long = System.currentTimeMillis()
    private var justAuthenticated: Boolean = false

    private val _authState = MutableStateFlow<AuthResult>(AuthResult.Loading)
    
    val authState: StateFlow<AuthResult> = _authState.asStateFlow()

    /**
     * Consulta no-lanzante del estado de capacidad biométrica.
     * La UI dirige estados leyendo esto, sin capturar excepciones. Ver ADR-013.
     */
    fun checkCapability(context: Context): PasskeyCapability {
        val code = BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        return mapCanAuthenticateToCapability(code)
    }

    suspend fun initialize(
        context: Context,
        config: PasskeyAuthConfig = PasskeyAuthConfig.Default
    ): Result<Unit> {
        return try {
            if (isInitialized) {
                return Result.failure(
                    IllegalStateException("PasskeyAuth ya esta inicializado")
                )
            }

            println("ðŸ” PasskeyAuth: Inicializando SDK...")

            appContext = context.applicationContext
            this.config = config

            refreshAuthState()

            isInitialized = true
            lastActivityTimestamp = System.currentTimeMillis()
            
            println("âœ… PasskeyAuth: Inicializado exitosamente")
            
            Result.success(Unit)

        } catch (e: Exception) {
            println("âŒ PasskeyAuth: Error en inicializacion: ${e.message}")
            Result.failure(e)
        }
    }

    fun enrollDevice(
        activity: FragmentActivity,
        email: String,
        temporaryPassword: String
    ): Flow<EnrollmentState> {
        requireInitialized()

        val enrollmentManager = EnrollmentManager.createWithDependencies(
            context = requireContext(),
            activity = activity,
            authBackend = authBackend,
            passwordManagement = passwordManagement,
            biometricAuthenticator = BiometricAuthenticator.create(activity),
            cryptoProvider = cryptoProvider,
            deviceRegistry = deviceRegistry,
            secureStorage = secureStorage,
            keyStoreManager = keyStoreManager
        )

        return enrollmentManager.enrollDevice(
            email = email,
            temporaryPassword = temporaryPassword
        )
    }

    suspend fun authenticate(activity: FragmentActivity): Result<AuthUser> {
        requireInitialized()

        return try {
            println("ðŸ” PasskeyAuth: Iniciando autenticacion")

            if (!isDeviceEnrolled()) {
                return Result.failure(
                    IllegalStateException("Dispositivo no enrollado. Llama a enrollDevice() primero.")
                )
            }

            val encryptedBase64 = secureStorage.loadEncryptedToken().getOrElse { error ->
                _authState.value = AuthResult.Unauthenticated
                return Result.failure(error)
            }

            if (encryptedBase64 == null) {
                _authState.value = AuthResult.Unauthenticated
                return Result.failure(
                    IllegalStateException("No hay token guardado")
                )
            }

            val encryptedData = EncryptedData.fromBase64String(encryptedBase64)
                ?: return Result.failure(
                    es.fjmarlop.corpsecauth.core.errors.CryptoException.DecryptionFailed(
                        "Token cifrado invalido"
                    )
                )

            val biometricAuthenticator = BiometricAuthenticator.create(activity)
            val cipher = biometricAuthenticator.authenticateForDecryption(
                iv = encryptedData.iv,
                config = BiometricConfig.Default
            ).getOrElse { error ->
                val authException = wrapToPasskeyAuthException(error)
                _authState.value = AuthResult.Error(authException)
                return Result.failure(error)
            }

            val tokenBytes = cipher.doFinal(encryptedData.ciphertext)
            val token = String(tokenBytes, Charsets.UTF_8)

            val currentUser = authBackend.getCurrentUser()
                ?: return Result.failure(
                    es.fjmarlop.corpsecauth.core.errors.FirebaseException.UserNotFound(
                        "Usuario no encontrado en Firebase"
                    )
                )

            val isDeviceValid = deviceRegistry.validateDevice(currentUser.uid).getOrElse {
                return Result.failure(it)
            }

            if (!isDeviceValid) {
                println("ðŸš¨ PasskeyAuth: Dispositivo revocado remotamente")
                _authState.value = AuthResult.Unauthenticated
                logout()
                return Result.failure(
                    es.fjmarlop.corpsecauth.core.errors.DeviceException.Revoked(
                        "Dispositivo revocado por administrador"
                    )
                )
            }

            secureStorage.saveLastActivityTimestamp(System.currentTimeMillis())

            _authState.value = AuthResult.Authenticated(currentUser)
            println("âœ… PasskeyAuth: Autenticacion exitosa")
            
            justAuthenticated = true
            lastActivityTimestamp = System.currentTimeMillis()

            Result.success(currentUser)

        } catch (e: Exception) {
            println("âŒ PasskeyAuth: Error en autenticacion: ${e.message}")
            val authException = wrapToPasskeyAuthException(e)
            _authState.value = AuthResult.Error(authException)
            Result.failure(e)
        }
    }

    fun logout() {
        println("ðŸšª PasskeyAuth: Cerrando sesion")
        
        scope.launch {
            try {
                secureStorage.clearToken()
                authBackend.signOut()
                _authState.value = AuthResult.Unauthenticated
                
                println("âœ… PasskeyAuth: Sesion cerrada")
            } catch (e: Exception) {
                println("âŒ PasskeyAuth: Error en logout: ${e.message}")
            }
        }
    }

    suspend fun unenrollDevice(): Result<Unit> {
        requireInitialized()

        return try {
            println("ðŸ—‘ï¸ PasskeyAuth: Eliminando enrollment")

            val userId = secureStorage.loadUserId().getOrNull()

            if (userId != null) {
                deviceRegistry.revokeDevice(userId).onFailure { error ->
                    println("âš ï¸ PasskeyAuth: Error revocando dispositivo: ${error.message}")
                }
            }

            keyStoreManager.deleteKey()
            secureStorage.clear()
            authBackend.signOut()

            _authState.value = AuthResult.Unauthenticated

            println("âœ… PasskeyAuth: Enrollment eliminado")
            Result.success(Unit)

        } catch (e: Exception) {
            println("âŒ PasskeyAuth: Error eliminando enrollment: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun isDeviceEnrolled(): Boolean {
        if (!isInitialized) return false

        val hasToken = secureStorage.hasStoredSession()
        val hasKey = keyStoreManager.hasKey()
        val userId = secureStorage.loadUserId().getOrNull()

        return hasToken && hasKey && userId != null
    }

    fun getCurrentUser(): AuthUser? {
        if (!isInitialized) return null
        return authBackend.getCurrentUser()
    }

    fun isAuthenticated(): Boolean {
        return authState.value is AuthResult.Authenticated
    }

    fun refreshAuthState() {
        scope.launch {
            try {
                println("ðŸ”„ PasskeyAuth: Refrescando estado de autenticacion")

                if (!isDeviceEnrolled()) {
                    _authState.value = AuthResult.Unauthenticated
                    return@launch
                }

                val currentUser = authBackend.getCurrentUser()
                if (currentUser == null) {
                    _authState.value = AuthResult.Unauthenticated
                    return@launch
                }

                val isDeviceValid = deviceRegistry.validateDevice(currentUser.uid)
                    .getOrElse { false }

                if (!isDeviceValid) {
                    println("ðŸš¨ PasskeyAuth: Dispositivo invalidado")
                    _authState.value = AuthResult.Unauthenticated
                    logout()
                    return@launch
                }

                _authState.value = AuthResult.Authenticated(currentUser)
                println("âœ… PasskeyAuth: Estado actualizado - Authenticated")

            } catch (e: Exception) {
                println("âŒ PasskeyAuth: Error refrescando estado: ${e.message}")
                val authException = wrapToPasskeyAuthException(e)
                _authState.value = AuthResult.Error(authException)
            }
        }
    }

    

    fun onAppBackground() {
        lastActivityTimestamp = System.currentTimeMillis()
        justAuthenticated = false
        println("🕐 PasskeyAuth: App a background (timestamp: $lastActivityTimestamp)")
    }

    fun onAppForeground() {
        if (justAuthenticated) {
            println("âœ… PasskeyAuth: Recien autenticado, ignorando verificacion de timeout")
            justAuthenticated = false
            lastActivityTimestamp = System.currentTimeMillis()
            return
        }

        val cfg = config ?: run {
            println("âš ï¸ PasskeyAuth: Config no inicializado")
            return
        }

        if (cfg.sessionTimeoutMinutes == 0) {
            println("ðŸ”’ PasskeyAuth: Timeout = 0, invalidando sesion")
            invalidateSession()
            return
        }

        if (cfg.sessionTimeoutMinutes < 0) {
            println("â³ PasskeyAuth: Timeout deshabilitado (testing mode)")
            return
        }

        val now = System.currentTimeMillis()
        val elapsedMs = now - lastActivityTimestamp
        val timeoutMs = cfg.sessionTimeoutMinutes * 60 * 1000L

        if (elapsedMs > timeoutMs) {
            val elapsedMin = elapsedMs / 60000
            println("ðŸ”’ PasskeyAuth: Timeout excedido (${elapsedMin}min > ${cfg.sessionTimeoutMinutes}min), invalidando sesion")
            invalidateSession()
        } else {
            val elapsedMin = elapsedMs / 60000
            println("âœ… PasskeyAuth: Dentro de timeout (${elapsedMin}min <= ${cfg.sessionTimeoutMinutes}min), manteniendo sesion")
        }
    }

    fun invalidateSession() {
        println("ðŸ”’ PasskeyAuth: Invalidando sesion (mantiene enrollment)")
        _authState.value = AuthResult.Unauthenticated
    }

    private fun wrapToPasskeyAuthException(throwable: Throwable): PasskeyAuthException {
        return when (throwable) {
            is PasskeyAuthException -> throwable
            else -> es.fjmarlop.corpsecauth.core.errors.EnrollmentException.EnrollmentFailed(
                "Error: ${throwable.message}",
                throwable
            )
        }
    }

    private fun requireInitialized() {
        if (!isInitialized) {
            throw IllegalStateException(
                "PasskeyAuth no esta inicializado. Llama a initialize() primero."
            )
        }
    }

    private fun requireContext(): Context {
        return appContext ?: throw IllegalStateException(
            "Context no disponible. PasskeyAuth no inicializado correctamente."
        )
    }

    private fun createKeyStoreManager(): KeyStoreManager {
        val cfg = config ?: PasskeyAuthConfig.Default
        return if (cfg.requireStrongBox) {
            KeyStoreManager.createWithStrongBox()
        } else {
            KeyStoreManager.createDefault()
        }
    }

    internal fun reset() {
        isInitialized = false
        appContext = null
        config = null
        _authState.value = AuthResult.Loading
        // Nulificar backing fields para que el proximo acceso re-evalue las
        // factories. Necesario para aislamiento de tests (ADR-011).
        _firebaseAuthBackend = null
        _keyStoreManager = null
        _cryptoProvider = null
        _secureStorage = null
        _deviceRegistry = null
    }
}