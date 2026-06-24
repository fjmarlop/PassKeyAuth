package es.fjmarlop.corpsecauth

import android.content.Context
import android.content.pm.PackageManager
import com.google.common.truth.Truth.assertThat
import es.fjmarlop.corpsecauth.core.crypto.CryptoProvider
import es.fjmarlop.corpsecauth.core.crypto.KeyStoreManager
import es.fjmarlop.corpsecauth.core.fakes.FakeKeyStoreManager
import es.fjmarlop.corpsecauth.core.fakes.InMemoryDeviceRegistry
import es.fjmarlop.corpsecauth.core.firebase.AuthBackend
import es.fjmarlop.corpsecauth.core.firebase.DeviceRegistry
import es.fjmarlop.corpsecauth.core.firebase.FirebaseAuthBackend
import es.fjmarlop.corpsecauth.core.models.AuthResult
import es.fjmarlop.corpsecauth.core.models.AuthUser
import es.fjmarlop.corpsecauth.core.storage.SecureStorage
import es.fjmarlop.corpsecauth.core.support.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Tests del facade [PasskeyAuth] (singleton `object`).
 *
 * **Reto:** [PasskeyAuth] tiene propiedades `by lazy { CompanionFactory() }`
 * que normalmente crean Firebase, AndroidKeyStore, DataStore reales — no
 * testeable en JVM.
 *
 * **Estrategia:** `mockkObject(...Companion)` intercepta cada factory para
 * devolver fakes/mocks. Cuando el `by lazy` se dispara, recibe el fake.
 * Sin tocar el codigo de produccion.
 *
 * ```kotlin
 * mockkObject(FirebaseAuthBackend.Companion)
 * every { FirebaseAuthBackend.createDefault() } returns fakeBackend
 * // ahora PasskeyAuth.firebaseAuthBackend === fakeBackend
 * ```
 *
 * **Aislamiento entre tests:** `PasskeyAuth.reset()` nulifica los backing
 * fields de las propiedades diferidas (patron resettable implementado en
 * produccion para soportar testing — ADR-011). Cada `@Before` recibe
 * factories frescas interceptadas por MockK.
 */
internal class PasskeyAuthFacadeTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fakeKeyStoreManager: FakeKeyStoreManager
    private lateinit var fakeDeviceRegistry: InMemoryDeviceRegistry
    private lateinit var firebaseAuthBackendMock: FirebaseAuthBackend
    private lateinit var secureStorageMock: SecureStorage
    private lateinit var cryptoProviderMock: CryptoProvider
    private lateinit var contextMock: Context

    private val testUser = AuthUser(
        uid = "uid-facade-test",
        email = "test@empresa.com",
        displayName = null,
        isEmailVerified = true
    )

    @Before
    fun setUp() {
        PasskeyAuth.reset()

        // Fakes
        fakeKeyStoreManager = FakeKeyStoreManager()
        fakeDeviceRegistry = InMemoryDeviceRegistry()

        // Mocks (componentes que el facade construye via Companion factories)
        firebaseAuthBackendMock = mockk(relaxed = true)
        secureStorageMock = mockk(relaxed = false)
        cryptoProviderMock = mockk(relaxed = true)
        contextMock = mockk(relaxed = true)

        // IntegrityGuard (parte de initialize) consulta paquetes instalados via
        // PackageManager. Con un contextMock relaxed, getPackageInfo devolvería un
        // mock en vez de lanzar NameNotFoundException — haciendo que TODO paquete
        // (incluidas apps de root) pareciera instalado. Estabilizamos el PM para
        // que reporte "no instalado", reflejando un entorno limpio.
        every { contextMock.applicationContext } returns contextMock
        val packageManagerMock = mockk<PackageManager>()
        every { contextMock.packageManager } returns packageManagerMock
        every { packageManagerMock.getPackageInfo(any<String>(), any<Int>()) } throws
            PackageManager.NameNotFoundException()

        // SecureStorage: comportamiento por defecto que TODOS los tests usan en
        // refreshAuthState (parte de initialize). No expone Result fallidos —
        // los tests que necesiten failure los reasignan en su propio cuerpo.
        coEvery { secureStorageMock.hasStoredSession() } returns false
        coEvery { secureStorageMock.loadUserId() } returns Result.success(null)
        coEvery { secureStorageMock.clearToken() } returns Result.success(Unit)
        coEvery { secureStorageMock.clear() } returns Result.success(Unit)
        coEvery { secureStorageMock.saveLastActivityTimestamp(any()) } returns Result.success(Unit)

        // Interceptar las companion factories — las propiedades `by lazy` del
        // facade recibiran nuestros fakes/mocks transparentemente.
        mockkObject(FirebaseAuthBackend.Companion)
        every { FirebaseAuthBackend.createDefault() } returns firebaseAuthBackendMock

        mockkObject(KeyStoreManager.Companion)
        every { KeyStoreManager.createDefault() } returns fakeKeyStoreManager
        every { KeyStoreManager.createWithStrongBox() } returns fakeKeyStoreManager

        mockkObject(CryptoProvider.Companion)
        every { CryptoProvider.createWithKeyStore(any()) } returns cryptoProviderMock

        mockkObject(SecureStorage.Companion)
        every { SecureStorage.create(any()) } returns secureStorageMock

        mockkObject(DeviceRegistry.Companion)
        every { DeviceRegistry.create(any()) } returns fakeDeviceRegistry
    }

    @After
    fun tearDown() {
        unmockkAll()
        PasskeyAuth.reset()
    }

    // ============================================================
    // Estado inicial / requireInitialized
    // ============================================================

    @Test
    fun `dado SDK no inicializado cuando isAuthenticated entonces false`() {
        // No llamamos a initialize — authState arranca en Loading (no Authenticated).
        assertThat(PasskeyAuth.isAuthenticated()).isFalse()
    }

    @Test
    fun `dado SDK no inicializado cuando getCurrentUser entonces null`() {
        assertThat(PasskeyAuth.getCurrentUser()).isNull()
    }

    @Test
    fun `dado SDK no inicializado cuando isDeviceEnrolled entonces false`() = runTest {
        assertThat(PasskeyAuth.isDeviceEnrolled()).isFalse()
    }

    @Test
    fun `dado SDK no inicializado cuando enrollDevice entonces IllegalStateException`() {
        val activityMock = mockk<androidx.fragment.app.FragmentActivity>(relaxed = true)
        assertThrows(IllegalStateException::class.java) {
            PasskeyAuth.enrollDevice(activityMock, "x@y.z", "tempPass")
        }
    }

    @Test
    fun `dado SDK no inicializado cuando unenrollDevice entonces IllegalStateException`() = runTest {
        assertThrows(IllegalStateException::class.java) {
            // unenrollDevice es suspend y llama requireInitialized — el throw ocurre dentro
            // del runTest, antes de cualquier suspend point.
            kotlinx.coroutines.runBlocking { PasskeyAuth.unenrollDevice() }
        }
    }

    // ============================================================
    // initialize
    // ============================================================

    @Test
    fun `dado SDK fresco cuando initialize entonces success`() = runTest {
        val result = PasskeyAuth.initialize(contextMock, PasskeyAuthConfig.Default)

        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun `dado SDK ya inicializado cuando initialize otra vez entonces failure IllegalStateException`() = runTest {
        PasskeyAuth.initialize(contextMock, PasskeyAuthConfig.Default).getOrThrow()

        val result = PasskeyAuth.initialize(contextMock, PasskeyAuthConfig.Default)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
    }

    // ============================================================
    // invalidateSession + estado de authState
    // ============================================================

    @Test
    fun `cuando invalidateSession entonces authState pasa a Unauthenticated`() {
        PasskeyAuth.invalidateSession()

        assertThat(PasskeyAuth.authState.value).isEqualTo(AuthResult.Unauthenticated)
        assertThat(PasskeyAuth.isAuthenticated()).isFalse()
    }

    // ============================================================
    // onAppForeground — timeout config logic
    // ============================================================

    @Test
    fun `dado SDK sin inicializar cuando onAppForeground entonces no crashea`() {
        // config = null → early return. No debe lanzar.
        PasskeyAuth.onAppForeground()

        // authState sigue en Loading (no se ha tocado).
        assertThat(PasskeyAuth.authState.value).isEqualTo(AuthResult.Loading)
    }

    @Test
    fun `dado timeout 0 cuando onAppForeground entonces invalidateSession inmediato`() = runTest {
        PasskeyAuth.initialize(contextMock, PasskeyAuthConfig.Debug).getOrThrow() // timeout = 0
        Thread.sleep(150) // dejar terminar refreshAuthState async

        PasskeyAuth.onAppForeground()

        assertThat(PasskeyAuth.authState.value).isEqualTo(AuthResult.Unauthenticated)
    }

    @Test
    fun `dado timeout negativo cuando onAppForeground entonces NO invalida`() = runTest {
        PasskeyAuth.initialize(
            contextMock,
            PasskeyAuthConfig.Custom(sessionTimeoutMinutes = -1)
        ).getOrThrow()
        Thread.sleep(150) // dejar terminar refreshAuthState async (ver test 5-min)

        // Establecer un estado autenticado simulado para detectar si se invalida.
        val authStateField = PasskeyAuth::class.java.getDeclaredField("_authState").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val flow = authStateField.get(PasskeyAuth) as kotlinx.coroutines.flow.MutableStateFlow<AuthResult>
        flow.value = AuthResult.Authenticated(testUser)

        PasskeyAuth.onAppForeground()

        // timeout = -1 → "deshabilitado", no toca authState.
        assertThat(PasskeyAuth.authState.value).isInstanceOf(AuthResult.Authenticated::class.java)
    }

    @Test
    fun `dado timeout 5 con timestamp viejo cuando onAppForeground entonces invalida sesion`() = runTest {
        PasskeyAuth.initialize(
            contextMock,
            PasskeyAuthConfig.Custom(sessionTimeoutMinutes = 5)
        ).getOrThrow()

        // Simulamos 10 minutos transcurridos: empujamos el lastActivityTimestamp
        // 10 min al pasado via reflection. Mas robusto que sleep.
        val timestampField = PasskeyAuth::class.java.getDeclaredField("lastActivityTimestamp").apply {
            isAccessible = true
        }
        timestampField.setLong(PasskeyAuth, System.currentTimeMillis() - (10L * 60_000))

        PasskeyAuth.onAppForeground()

        assertThat(PasskeyAuth.authState.value).isEqualTo(AuthResult.Unauthenticated)
    }

    @Test
    fun `dado timeout 5 con timestamp reciente cuando onAppForeground entonces NO invalida`() = runTest {
        PasskeyAuth.initialize(
            contextMock,
            PasskeyAuthConfig.Custom(sessionTimeoutMinutes = 5)
        ).getOrThrow()

        // initialize dispara refreshAuthState() async en Dispatchers.IO (no controlado
        // por nuestro test scheduler). Esperamos en wall-clock para que termine
        // antes de manipular authState — si no, sobreescribe nuestro Authenticated.
        Thread.sleep(150)

        val authStateField = PasskeyAuth::class.java.getDeclaredField("_authState").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val flow = authStateField.get(PasskeyAuth) as kotlinx.coroutines.flow.MutableStateFlow<AuthResult>
        flow.value = AuthResult.Authenticated(testUser)

        // timestamp recien actualizado por initialize → elapsed < 5min.
        PasskeyAuth.onAppForeground()

        assertThat(PasskeyAuth.authState.value).isInstanceOf(AuthResult.Authenticated::class.java)
    }

    @Test
    fun `dado justAuthenticated true cuando onAppForeground entonces NO invalida y resetea flag`() = runTest {
        PasskeyAuth.initialize(contextMock, PasskeyAuthConfig.Debug).getOrThrow() // timeout = 0

        // Empujamos `justAuthenticated = true`. Sin esto, timeout=0 invalidaria.
        val justAuthField = PasskeyAuth::class.java.getDeclaredField("justAuthenticated").apply {
            isAccessible = true
        }
        justAuthField.setBoolean(PasskeyAuth, true)

        // Authenticated para detectar la NO-invalidacion
        val authStateField = PasskeyAuth::class.java.getDeclaredField("_authState").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val flow = authStateField.get(PasskeyAuth) as kotlinx.coroutines.flow.MutableStateFlow<AuthResult>
        flow.value = AuthResult.Authenticated(testUser)

        PasskeyAuth.onAppForeground()

        // No se invalido aunque timeout=0 — justAuthenticated tiene precedencia
        assertThat(PasskeyAuth.authState.value).isInstanceOf(AuthResult.Authenticated::class.java)
        // Y el flag se reseteo a false
        assertThat(justAuthField.getBoolean(PasskeyAuth)).isFalse()
    }

    // ============================================================
    // onAppBackground
    // ============================================================

    @Test
    fun `cuando onAppBackground entonces actualiza lastActivityTimestamp y resetea justAuthenticated`() {
        val justAuthField = PasskeyAuth::class.java.getDeclaredField("justAuthenticated").apply {
            isAccessible = true
        }
        val timestampField = PasskeyAuth::class.java.getDeclaredField("lastActivityTimestamp").apply {
            isAccessible = true
        }

        justAuthField.setBoolean(PasskeyAuth, true)
        val before = System.currentTimeMillis()

        PasskeyAuth.onAppBackground()

        assertThat(justAuthField.getBoolean(PasskeyAuth)).isFalse()
        assertThat(timestampField.getLong(PasskeyAuth)).isAtLeast(before)
    }

    // ============================================================
    // logout
    // ============================================================

    @Test
    fun `cuando logout entonces limpia token, sign out y authState Unauthenticated`() = runTest {
        PasskeyAuth.initialize(contextMock, PasskeyAuthConfig.Default).getOrThrow()

        PasskeyAuth.logout()

        // logout() dispara un scope.launch en Dispatchers.IO (fire-and-forget).
        // Thread.sleep espera en wall-clock a que el hilo IO complete —
        // mismo patron que usan otros tests de este fichero para refreshAuthState.
        Thread.sleep(200)

        coVerify(atLeast = 1) { secureStorageMock.clearToken() }
        coVerify(atLeast = 1) { firebaseAuthBackendMock.signOut() }
        assertThat(PasskeyAuth.authState.value).isEqualTo(AuthResult.Unauthenticated)
    }
}
