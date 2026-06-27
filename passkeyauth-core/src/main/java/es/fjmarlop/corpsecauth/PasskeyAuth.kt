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
import es.fjmarlop.corpsecauth.core.firebase.FirebaseAuthBackend
import es.fjmarlop.corpsecauth.core.models.AuthResult
import es.fjmarlop.corpsecauth.core.models.AuthUser
import es.fjmarlop.corpsecauth.core.models.BiometricConfig
import es.fjmarlop.corpsecauth.core.models.EnrollmentState
import es.fjmarlop.corpsecauth.core.storage.SecureStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object PasskeyAuth {

    @Volatile private var isInitialized = false
    private var appContext: Context? = null
    private var config: PasskeyAuthConfig? = null

    @Volatile private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

    // Backends inyectados por el integrador via initialize() (ADR-016).
    // null = usar implementacion Firebase por defecto.
    @Volatile private var _customAuthBackend: AuthBackend? = null
    @Volatile private var _customDeviceRegistry: DeviceRegistry? = null

    private val authBackend: AuthBackend
        get() = _customAuthBackend ?: firebaseAuthBackend

    // Si el authBackend custom no implementa PasswordManagementBackend (p.ej. backends OAuth2/OIDC),
    // cae a firebaseAuthBackend como fallback. El paso de invalidacion de password temporal
    // (enrollment step 2) esta actualmente comentado en EnrollmentManager, por lo que esta
    // ruta no es activa. En v0.5.0 se revisara cuando se active el paso 2.
    private val passwordManagement: PasswordManagementBackend
        get() = (_customAuthBackend as? PasswordManagementBackend) ?: firebaseAuthBackend

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
        get() = _customDeviceRegistry
            ?: _deviceRegistry
            ?: DeviceRegistry.createDefault(requireContext()).also { _deviceRegistry = it }

    private var lastActivityTimestamp: Long = System.currentTimeMillis()
    private var justAuthenticated: Boolean = false

    private val _authState = MutableStateFlow<AuthResult>(AuthResult.Loading)

    val authState: StateFlow<AuthResult> = _authState.asStateFlow()

    /**
     * Consulta no-lanzante del estado de capacidad biométrica del dispositivo.
     *
     * Diseñado para ser llamado desde la UI antes de mostrar CTAs de autenticación.
     * No lanza excepciones — devuelve [PasskeyCapability] que la UI puede mapear a estado.
     *
     * @param context Contexto de la aplicación.
     * @return Estado de capacidad biométrica actual.
     * @see PasskeyCapability
     */
    fun checkCapability(context: Context): PasskeyCapability {
        val code = BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        return mapCanAuthenticateToCapability(code)
    }

    /**
     * Inicializa el SDK. Debe llamarse una sola vez, típicamente en [Application.onCreate].
     *
     * Ejecuta la comprobación de integridad del entorno según la [PasskeyAuthConfig] proporcionada.
     * Si la política es [RootPolicy.Block] y el dispositivo está rooteado, devuelve
     * [Result.failure] con [es.fjmarlop.corpsecauth.core.errors.IntegrityException.RootDetected].
     *
     * Firebase se usa como backend por defecto. Para inyectar un backend alternativo
     * (Keycloak, REST propio, etc.), pasa instancias de [AuthBackend] y/o [DeviceRegistry].
     *
     * @param context Contexto de la aplicación.
     * @param config Configuración del SDK. Por defecto [PasskeyAuthConfig.Default].
     * @param authBackend Backend de autenticación alternativo. `null` usa Firebase.
     * @param deviceRegistry Registry de dispositivos alternativo. `null` usa Firestore.
     * @return [Result.success] si la inicialización fue correcta,
     *         [Result.failure] con [es.fjmarlop.corpsecauth.core.errors.IntegrityException] si el entorno está comprometido,
     *         o [Result.failure] con [IllegalStateException] si ya estaba inicializado.
     */
    suspend fun initialize(
        context: Context,
        config: PasskeyAuthConfig = PasskeyAuthConfig.Default,
        authBackend: AuthBackend? = null,
        deviceRegistry: DeviceRegistry? = null
    ): Result<Unit> {
        return try {
            if (isInitialized) {
                return Result.failure(
                    IllegalStateException("PasskeyAuth ya esta inicializado")
                )
            }

            // Comprobación de integridad del entorno antes de operar (ADR-015).
            // Falla la inicialización si la política es Block y se detecta un
            // entorno comprometido (root, hooking, emulador o depurador en release).
            es.fjmarlop.corpsecauth.core.security.IntegrityGuard.check(
                context = context.applicationContext,
                rootPolicy = config.rootPolicy,
                emulatorPolicy = config.emulatorPolicy,
                isDebugBuild = es.fjmarlop.corpsecauth.core.BuildConfig.DEBUG,
            ).getOrElse { integrityError ->
                return Result.failure(integrityError)
            }

            appContext = context.applicationContext
            this.config = config
            _customAuthBackend = authBackend
            _customDeviceRegistry = deviceRegistry

            refreshAuthState()

            isInitialized = true
            lastActivityTimestamp = System.currentTimeMillis()

            Result.success(Unit)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Inicia el flujo de enrollment del dispositivo para el usuario dado.
     *
     * El enrollment es un proceso transaccional de 6 pasos (login, generación de clave,
     * biometría, cifrado del token, binding de dispositivo, almacenamiento local).
     * Si cualquier paso falla, los pasos anteriores se revierten automáticamente.
     *
     * Emite [es.fjmarlop.corpsecauth.core.models.EnrollmentState] a medida que el proceso avanza.
     * El llamador debe observar el Flow y reaccionar a
     * [es.fjmarlop.corpsecauth.core.models.EnrollmentState.Success] o
     * [es.fjmarlop.corpsecauth.core.models.EnrollmentState.Error].
     *
     * @param activity Activity requerida para mostrar el prompt biométrico (debe ser [androidx.fragment.app.FragmentActivity]).
     * @param email Email corporativo del usuario.
     * @param temporaryPassword Contraseña temporal asignada por IT para el primer acceso.
     * @return [kotlinx.coroutines.flow.Flow] de [es.fjmarlop.corpsecauth.core.models.EnrollmentState].
     * @throws IllegalStateException si el SDK no está inicializado.
     */
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

    /**
     * Autentica al usuario mediante biometría hardware-backed (BIOMETRIC_STRONG).
     *
     * Requiere que el dispositivo esté previamente enrollado con [enrollDevice].
     * Verifica que el dispositivo siga activo en el registry remoto antes de conceder
     * acceso — si fue revocado por IT, devuelve [Result.failure].
     *
     * @param activity Activity requerida para mostrar el prompt biométrico.
     * @return [Result.success] con [es.fjmarlop.corpsecauth.core.models.AuthUser] si la autenticación fue correcta,
     *         [Result.failure] con [es.fjmarlop.corpsecauth.core.errors.DeviceException.Revoked] si el dispositivo fue revocado,
     *         o [Result.failure] con [es.fjmarlop.corpsecauth.core.errors.BiometricException] si la biometría falló.
     * @throws IllegalStateException si el SDK no está inicializado.
     */
    suspend fun authenticate(activity: FragmentActivity): Result<AuthUser> {
        requireInitialized()

        return try {
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

            // Descifrar el token prueba que la biometría tuvo éxito y la clave es
            // válida. No reusamos el plaintext: lo borramos del heap de inmediato
            // (defensa frente a heap dumps — ADR-015, bloque D).
            val tokenBytes = cipher.doFinal(encryptedData.ciphertext)
            tokenBytes.fill(0)

            val currentUser = authBackend.getCurrentUser()
                ?: return Result.failure(
                    es.fjmarlop.corpsecauth.core.errors.FirebaseException.UserNotFound(
                        "No hay sesion de usuario activa — la sesion expiro o fue revocada"
                    )
                )

            val isDeviceValid = deviceRegistry.validateDevice(currentUser.uid).getOrElse {
                return Result.failure(it)
            }

            if (!isDeviceValid) {
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

            justAuthenticated = true
            lastActivityTimestamp = System.currentTimeMillis()

            Result.success(currentUser)

        } catch (e: Exception) {
            val authException = wrapToPasskeyAuthException(e)
            _authState.value = AuthResult.Error(authException)
            Result.failure(e)
        }
    }

    /**
     * Cierra la sesión del usuario sin eliminar el enrollment del dispositivo.
     *
     * Limpia el token local y cierra la sesión en el backend. El dispositivo
     * sigue vinculado — el usuario puede volver a autenticarse con [authenticate].
     * Para eliminar el enrollment completamente, usa [unenrollDevice].
     */
    fun logout() {
        scope.launch {
            try {
                secureStorage.clearToken()
                authBackend.signOut()
                _authState.value = AuthResult.Unauthenticated
            } catch (e: Exception) {
                // no-op
            }
        }
    }

    /**
     * Elimina el enrollment del dispositivo de forma completa.
     *
     * Revoca el binding remoto en el registry, borra la clave del AndroidKeyStore,
     * limpia el almacenamiento local cifrado y cierra la sesión del backend.
     * Después de esta llamada, el usuario deberá hacer [enrollDevice] de nuevo.
     *
     * @return [Result.success] si el unenrollment fue correcto.
     *         [Result.failure] si hubo un error irrecuperable (el estado local se limpia igualmente).
     * @throws IllegalStateException si el SDK no está inicializado.
     */
    suspend fun unenrollDevice(): Result<Unit> {
        requireInitialized()

        return try {
            val userId = secureStorage.loadUserId().getOrNull()

            if (userId != null) {
                deviceRegistry.revokeDevice(userId).onFailure { /* no-op */ }
            }

            keyStoreManager.deleteKey()
            secureStorage.clear()
            authBackend.signOut()

            _authState.value = AuthResult.Unauthenticated

            Result.success(Unit)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Comprueba si el dispositivo tiene un enrollment válido y completo.
     *
     * Verifica la presencia de los tres componentes: token cifrado en storage,
     * clave en AndroidKeyStore, e identificador de usuario.
     *
     * @return `true` si el dispositivo está enrollado, `false` en caso contrario.
     */
    suspend fun isDeviceEnrolled(): Boolean {
        if (!isInitialized) return false

        val hasToken = secureStorage.hasStoredSession()
        val hasKey = keyStoreManager.hasKey()
        val userId = secureStorage.loadUserId().getOrNull()

        return hasToken && hasKey && userId != null
    }

    /**
     * Devuelve el usuario actualmente autenticado según el backend, o `null` si no hay sesión.
     *
     * Esta llamada es síncrona y usa la caché del backend (Firebase Auth en la implementación
     * por defecto). Para backends OIDC sin caché, puede devolver `null` aunque haya una sesión
     * válida — en ese caso implementa un adaptador que mantenga el usuario en memoria.
     *
     * @return [es.fjmarlop.corpsecauth.core.models.AuthUser] si hay sesión activa,
     *         `null` si no hay usuario autenticado o el SDK no está inicializado.
     */
    fun getCurrentUser(): AuthUser? {
        if (!isInitialized) return null
        return authBackend.getCurrentUser()
    }

    /**
     * Indica si el estado de autenticación actual es [es.fjmarlop.corpsecauth.core.models.AuthResult.Authenticated].
     *
     * Equivale a `authState.value is AuthResult.Authenticated`.
     * Para observar cambios reactivos, suscríbete a [authState] en su lugar.
     *
     * @return `true` si el usuario está autenticado en este momento.
     */
    fun isAuthenticated(): Boolean {
        return authState.value is AuthResult.Authenticated
    }

    internal fun refreshAuthState() {
        scope.launch {
            try {
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
                    _authState.value = AuthResult.Unauthenticated
                    logout()
                    return@launch
                }

                _authState.value = AuthResult.Authenticated(currentUser)

            } catch (e: Exception) {
                val authException = wrapToPasskeyAuthException(e)
                _authState.value = AuthResult.Error(authException)
            }
        }
    }



    /**
     * Notifica al SDK que la app pasó a segundo plano.
     *
     * Registra el timestamp para el cálculo de timeout de sesión.
     * Debe llamarse desde el Activity raíz o Application en `onStop()`.
     *
     * @see PasskeyAuthConfig.sessionTimeoutMinutes
     * @see onAppForeground
     */
    fun onAppBackground() {
        lastActivityTimestamp = System.currentTimeMillis()
        justAuthenticated = false
    }

    /**
     * Notifica al SDK que la app volvió a primer plano.
     *
     * Evalúa si el tiempo transcurrido desde [onAppBackground] supera
     * [PasskeyAuthConfig.sessionTimeoutMinutes]. Si es así, invalida la sesión
     * y emite [es.fjmarlop.corpsecauth.core.models.AuthResult.Unauthenticated] en [authState].
     *
     * @see onAppBackground
     */
    fun onAppForeground() {
        if (justAuthenticated) {
            justAuthenticated = false
            lastActivityTimestamp = System.currentTimeMillis()
            return
        }

        val cfg = config ?: return

        if (cfg.sessionTimeoutMinutes == 0) {
            invalidateSession()
            return
        }

        if (cfg.sessionTimeoutMinutes < 0) {
            return
        }

        val now = System.currentTimeMillis()
        val elapsedMs = now - lastActivityTimestamp
        val timeoutMs = cfg.sessionTimeoutMinutes * 60 * 1000L

        if (elapsedMs > timeoutMs) {
            invalidateSession()
        }
    }

    internal fun invalidateSession() {
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
        return if (cfg.strongBox == StrongBoxPolicy.Required) {
            KeyStoreManager.createWithStrongBox()
        } else {
            KeyStoreManager.createDefault()
        }
    }

    internal fun reset() {
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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
        _customAuthBackend = null
        _customDeviceRegistry = null
    }
}
