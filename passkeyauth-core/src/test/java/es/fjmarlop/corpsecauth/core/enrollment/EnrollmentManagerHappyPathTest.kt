package es.fjmarlop.corpsecauth.core.enrollment

import android.content.Context
import androidx.fragment.app.FragmentActivity
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import es.fjmarlop.corpsecauth.core.crypto.CryptoProvider
import es.fjmarlop.corpsecauth.core.fakes.FakeAuthBackend
import es.fjmarlop.corpsecauth.core.fakes.FakeBiometricAuthenticator
import es.fjmarlop.corpsecauth.core.fakes.FakeKeyStoreManager
import es.fjmarlop.corpsecauth.core.fakes.FakePasswordManagementBackend
import es.fjmarlop.corpsecauth.core.fakes.InMemoryDeviceRegistry
import es.fjmarlop.corpsecauth.core.models.AuthSession
import es.fjmarlop.corpsecauth.core.models.AuthUser
import es.fjmarlop.corpsecauth.core.models.Credentials
import es.fjmarlop.corpsecauth.core.models.EnrollmentState
import es.fjmarlop.corpsecauth.core.storage.SecureStorage
import es.fjmarlop.corpsecauth.core.support.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * TEST PLANTILLA DE ORO (ADR-011): happy path completo de [EnrollmentManager.enrollDevice].
 *
 * Este test sirve como referencia para todos los demas tests del SDK. Si vas a
 * escribir un test nuevo, copia la estructura de este.
 *
 * **Patrones que demuestra:**
 * 1. Setup con fakes + mocks compartidos en `@Before` (un set fresco por test).
 * 2. Uso de [MainDispatcherRule] para que `Dispatchers.Main` no crashee.
 * 3. Uso de [runTest] (NO runBlocking) — provee dispatcher virtual y time skipping.
 * 4. Uso de Turbine para verificar emisiones secuenciales del Flow.
 * 5. Verificacion triple: emisiones, contadores de fakes, coVerify de mocks.
 * 6. Nombres descriptivos estilo BDD en espanol para reportes legibles.
 */
internal class EnrollmentManagerHappyPathTest {

    // === Reglas ===

    /**
     * Sustituye Dispatchers.Main por un TestDispatcher. Sin esto, cualquier uso
     * de Dispatchers.Main dentro del codigo bajo test lanza IllegalStateException
     * en JVM (no hay looper Android).
     */
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // === Fakes (uno por interfaz extraida en ADR-010) ===

    private lateinit var authBackend: FakeAuthBackend
    private lateinit var passwordManagement: FakePasswordManagementBackend
    private lateinit var biometricAuthenticator: FakeBiometricAuthenticator
    private lateinit var keyStoreManager: FakeKeyStoreManager
    private lateinit var deviceRegistry: InMemoryDeviceRegistry

    // === Mocks (para colaboradores SIN interfaz extraida) ===
    //
    // SecureStorage: no se abstrae a interfaz (ver ADR-010 — DataStore funciona en
    // Robolectric, asi que el test propio de SecureStorage va por Robolectric).
    // Aqui solo nos interesa que las llamadas se hagan, no probar SecureStorage en si.
    //
    // CryptoProvider: el EnrollmentManager lo recibe pero NUNCA lo usa (smell del
    // codigo actual). Lo inyectamos relaxed para que el constructor no falle.
    //
    // Context y FragmentActivity: el EnrollmentManager los guarda como properties
    // pero no los usa en enrollDevice(). Mockk relaxed devuelve defaults razonables.
    private lateinit var secureStorage: SecureStorage
    private lateinit var cryptoProvider: CryptoProvider
    private lateinit var context: Context
    private lateinit var activity: FragmentActivity

    // === Subject under test ===
    private lateinit var enrollmentManager: EnrollmentManager

    // === Datos de prueba ===
    private val testEmail = "test@empresa.com"
    private val testPassword = "TempPass123"
    private val testUser = AuthUser(
        uid = "firebase-uid-12345",
        email = testEmail,
        displayName = "Test User",
        isEmailVerified = true
    )
    private val testSession = AuthSession(
        user = testUser,
        idToken = "fake-jwt-id-token-payload"
    )

    @Before
    fun setUp() {
        // Instancias FRESCAS en cada test → aislamiento total entre tests.
        authBackend = FakeAuthBackend().apply {
            authenticateResult = Result.success(testSession)
        }
        passwordManagement = FakePasswordManagementBackend()
        biometricAuthenticator = FakeBiometricAuthenticator()
        keyStoreManager = FakeKeyStoreManager()
        deviceRegistry = InMemoryDeviceRegistry(
            deviceIdProvider = { "test-device-android-id-001" }
        )

        // SecureStorage: TODAS las funciones suspend devuelven Result<Unit> aunque
        // EnrollmentManager ignore el return de save{User,Device,Timestamp}.
        // Smell de codigo de produccion documentado en bugfixes.md (comentario
        // incorrecto en EnrollmentManager).
        secureStorage = mockk(relaxed = false) {
            coEvery { saveEncryptedToken(any()) } returns Result.success(Unit)
            coEvery { saveUserId(any()) } returns Result.success(Unit)
            coEvery { saveDeviceId(any()) } returns Result.success(Unit)
            coEvery { saveLastActivityTimestamp(any()) } returns Result.success(Unit)
        }

        // Inyecciones que no participan en enrollDevice() — relaxed = defaults.
        cryptoProvider = mockk(relaxed = true)
        context = mockk(relaxed = true)
        activity = mockk(relaxed = true)

        enrollmentManager = EnrollmentManager.createWithDependencies(
            context = context,
            activity = activity,
            authBackend = authBackend,
            passwordManagement = passwordManagement,
            biometricAuthenticator = biometricAuthenticator,
            cryptoProvider = cryptoProvider,
            deviceRegistry = deviceRegistry,
            secureStorage = secureStorage,
            keyStoreManager = keyStoreManager
        )
    }

    @Test
    fun `dado un enrollment exitoso emite todos los estados en orden y termina en Success`() = runTest {
        // ACT + ASSERT: usamos Turbine para verificar emisiones secuenciales.
        // Cada awaitItem() consume la siguiente emision; el test falla si la
        // emision no llega en un timeout o si el contenido no es el esperado.
        enrollmentManager.enrollDevice(testEmail, testPassword).test {

            // Estado 1: Idle (emision inicial del flujo)
            assertThat(awaitItem()).isEqualTo(EnrollmentState.Idle)

            // Estado 2: ValidatingCredentials con el email correcto (paso 1)
            val validatingState = awaitItem()
            assertThat(validatingState).isInstanceOf(
                EnrollmentState.ValidatingCredentials::class.java
            )
            assertThat((validatingState as EnrollmentState.ValidatingCredentials).email)
                .isEqualTo(testEmail)

            // NOTA: el paso 2 (RequiresPasswordChange) NO se emite porque esta
            // comentado en EnrollmentManager (ver TODO en el codigo). Cuando se
            // descomente, este test debera anadir su verificacion aqui.

            // Estado 3: GeneratingCryptoKey (paso 3)
            assertThat(awaitItem()).isEqualTo(EnrollmentState.GeneratingCryptoKey)

            // Estado 4: AwaitingBiometric (paso 4)
            assertThat(awaitItem()).isInstanceOf(
                EnrollmentState.AwaitingBiometric::class.java
            )

            // Estado 5: BindingDevice (paso 6 — el paso 5 cifrado no emite estado)
            assertThat(awaitItem()).isEqualTo(EnrollmentState.BindingDevice)

            // Estado 6: Success con el usuario correcto
            val finalState = awaitItem()
            assertThat(finalState).isInstanceOf(EnrollmentState.Success::class.java)
            assertThat((finalState as EnrollmentState.Success).user).isEqualTo(testUser)

            // Flow debe completar (sin mas emisiones).
            awaitComplete()
        }

        // ASSERT del lado de los fakes — verificamos QUE se llamaron cada uno UNA vez.
        // Esto cubre el contrato: "happy path llama a cada dependencia exactamente una vez".

        // 1. authBackend.authenticate() con EmailPassword del usuario
        assertThat(authBackend.authenticateCallCount).isEqualTo(1)
        val capturedCredentials = authBackend.lastCredentials
        assertThat(capturedCredentials).isInstanceOf(Credentials.EmailPassword::class.java)
        assertThat((capturedCredentials as Credentials.EmailPassword).email).isEqualTo(testEmail)
        assertThat(capturedCredentials.password).isEqualTo(testPassword)

        // 2. passwordManagement: NO debe llamarse (paso 2 comentado)
        assertThat(passwordManagement.invalidateCallCount).isEqualTo(0)

        // 3. keyStoreManager.generateKey() (paso 3)
        assertThat(keyStoreManager.generateKeyCallCount).isEqualTo(1)

        // 4. biometricAuthenticator.authenticateForEncryption() (paso 4)
        assertThat(biometricAuthenticator.encryptionCallCount).isEqualTo(1)

        // 5. deviceRegistry.bindDevice(uid) (paso 6)
        assertThat(deviceRegistry.bindDeviceCallCount).isEqualTo(1)
        assertThat(deviceRegistry.devicesFor(testUser.uid)?.isActive).isTrue()

        // ASSERT de NO-rollback — propiedad de seguridad clave del happy path:
        // si algo NO falla, NO se debe llamar a deleteKey ni signOut ni revokeDevice.
        assertThat(keyStoreManager.deleteKeyCallCount).isEqualTo(0)
        assertThat(authBackend.signOutCallCount).isEqualTo(0)
        assertThat(deviceRegistry.revokeDeviceCallCount).isEqualTo(0)

        // ASSERT del lado del SecureStorage (mock) — paso 7 + saves auxiliares.
        coVerify(exactly = 1) { secureStorage.saveEncryptedToken(any()) }
        coVerify(exactly = 1) { secureStorage.saveUserId(testUser.uid) }
        coVerify(exactly = 1) { secureStorage.saveDeviceId("test-device-android-id-001") }
        coVerify(exactly = 1) { secureStorage.saveLastActivityTimestamp(any()) }
    }
}
