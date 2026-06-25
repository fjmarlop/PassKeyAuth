package es.fjmarlop.corpsecauth

import android.content.Context
import android.content.pm.PackageManager
import com.google.common.truth.Truth.assertThat
import es.fjmarlop.corpsecauth.core.crypto.CryptoProvider
import es.fjmarlop.corpsecauth.core.crypto.KeyStoreManager
import es.fjmarlop.corpsecauth.core.fakes.FakeAuthBackend
import es.fjmarlop.corpsecauth.core.fakes.FakeKeyStoreManager
import es.fjmarlop.corpsecauth.core.fakes.InMemoryDeviceRegistry
import es.fjmarlop.corpsecauth.core.firebase.FirebaseAuthBackend
import es.fjmarlop.corpsecauth.core.firebase.FirestoreDeviceRegistry
import es.fjmarlop.corpsecauth.core.models.AuthUser
import es.fjmarlop.corpsecauth.core.storage.SecureStorage
import es.fjmarlop.corpsecauth.core.support.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Tests que verifican la inyeccion de backend custom en [PasskeyAuth.initialize].
 *
 * Cubre cuatro escenarios de ADR-016:
 *  1. [authBackend] custom → Firebase nunca instanciado.
 *  2. [deviceRegistry] custom → Firestore nunca instanciado.
 *  3. [getCurrentUser] devuelve el usuario del backend inyectado.
 *  4. Sin inyeccion → backward compat (initialize sigue siendo exitoso).
 */
internal class PasskeyAuthFacadeBackendInjectionTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fakeKeyStoreManager: FakeKeyStoreManager
    private lateinit var firebaseAuthBackendMock: FirebaseAuthBackend
    private lateinit var firestoreDeviceRegistryMock: FirestoreDeviceRegistry
    private lateinit var secureStorageMock: SecureStorage
    private lateinit var cryptoProviderMock: CryptoProvider
    private lateinit var contextMock: Context

    @Before
    fun setUp() {
        PasskeyAuth.reset()

        fakeKeyStoreManager = FakeKeyStoreManager()
        firebaseAuthBackendMock = mockk(relaxed = true)
        firestoreDeviceRegistryMock = mockk(relaxed = true)
        secureStorageMock = mockk(relaxed = false)
        cryptoProviderMock = mockk(relaxed = true)
        contextMock = mockk(relaxed = true)

        // PackageManager estabilizado: no paquetes de root instalados.
        every { contextMock.applicationContext } returns contextMock
        val packageManagerMock = mockk<PackageManager>()
        every { contextMock.packageManager } returns packageManagerMock
        every { packageManagerMock.getPackageInfo(any<String>(), any<Int>()) } throws
            PackageManager.NameNotFoundException()

        // SecureStorage: defaults usados por refreshAuthState en initialize().
        coEvery { secureStorageMock.hasStoredSession() } returns false
        coEvery { secureStorageMock.loadUserId() } returns Result.success(null)
        coEvery { secureStorageMock.clearToken() } returns Result.success(Unit)
        coEvery { secureStorageMock.clear() } returns Result.success(Unit)
        coEvery { secureStorageMock.saveLastActivityTimestamp(any()) } returns Result.success(Unit)

        // Interceptar companion factories para evitar inicializacion real de
        // Firebase / AndroidKeyStore / DataStore.
        mockkObject(FirebaseAuthBackend.Companion)
        every { FirebaseAuthBackend.createDefault() } returns firebaseAuthBackendMock

        mockkObject(FirestoreDeviceRegistry.Companion)
        every { FirestoreDeviceRegistry.create(any()) } returns firestoreDeviceRegistryMock

        mockkObject(KeyStoreManager.Companion)
        every { KeyStoreManager.createDefault() } returns fakeKeyStoreManager
        every { KeyStoreManager.createWithStrongBox() } returns fakeKeyStoreManager

        mockkObject(CryptoProvider.Companion)
        every { CryptoProvider.createWithKeyStore(any()) } returns cryptoProviderMock

        mockkObject(SecureStorage.Companion)
        every { SecureStorage.create(any()) } returns secureStorageMock
    }

    @After
    fun tearDown() {
        PasskeyAuth.reset()
        unmockkAll()
    }

    // ============================================================
    // Test 1: authBackend custom → Firebase no se usa
    // ============================================================

    @Test
    fun `dado authBackend custom cuando initialize entonces FirebaseAuthBackend createDefault nunca se llama`() = runTest {
        val customBackend = FakeAuthBackend()

        PasskeyAuth.initialize(
            context = contextMock,
            config = PasskeyAuthConfig.Custom(
                rootPolicy = RootPolicy.Allow,
                emulatorPolicy = EmulatorPolicy.Allow
            ),
            authBackend = customBackend
        ).getOrThrow()

        // Acceder a getCurrentUser() dispara el getter authBackend; con custom inyectado
        // nunca debe tocar firebaseAuthBackend ni llamar a createDefault().
        PasskeyAuth.getCurrentUser()

        verify(exactly = 0) { FirebaseAuthBackend.createDefault() }
    }

    // ============================================================
    // Test 2: deviceRegistry custom → Firestore no se usa
    // ============================================================

    @Test
    fun `dado deviceRegistry custom cuando initialize entonces FirestoreDeviceRegistry create nunca se llama`() = runTest {
        val customRegistry = InMemoryDeviceRegistry()

        PasskeyAuth.initialize(
            context = contextMock,
            config = PasskeyAuthConfig.Custom(
                rootPolicy = RootPolicy.Allow,
                emulatorPolicy = EmulatorPolicy.Allow
            ),
            deviceRegistry = customRegistry
        ).getOrThrow()

        verify(exactly = 0) { FirestoreDeviceRegistry.create(any()) }
    }

    // ============================================================
    // Test 3: getCurrentUser devuelve usuario del backend inyectado
    // ============================================================

    @Test
    fun `dado authBackend con usuario cuando initialize entonces getCurrentUser devuelve ese usuario`() = runTest {
        val expectedUser = AuthUser(
            uid = "uid-custom-backend",
            email = "custom@empresa.com",
            displayName = "Custom User",
            isEmailVerified = true
        )
        val customBackend = FakeAuthBackend().apply { forceCurrentUser(expectedUser) }

        PasskeyAuth.initialize(
            context = contextMock,
            config = PasskeyAuthConfig.Custom(
                rootPolicy = RootPolicy.Allow,
                emulatorPolicy = EmulatorPolicy.Allow
            ),
            authBackend = customBackend
        ).getOrThrow()

        val actualUser = PasskeyAuth.getCurrentUser()

        assertThat(actualUser).isEqualTo(expectedUser)
    }

    // ============================================================
    // Test 4: sin inyeccion → backward compat
    // ============================================================

    @Test
    fun `dado sin inyeccion cuando initialize entonces result isSuccess`() = runTest {
        val result = PasskeyAuth.initialize(
            context = contextMock,
            config = PasskeyAuthConfig.Custom(
                rootPolicy = RootPolicy.Allow,
                emulatorPolicy = EmulatorPolicy.Allow
            )
        )

        assertThat(result.isSuccess).isTrue()
    }
}
