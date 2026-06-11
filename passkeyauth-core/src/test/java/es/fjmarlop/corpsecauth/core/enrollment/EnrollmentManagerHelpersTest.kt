package es.fjmarlop.corpsecauth.core.enrollment

import android.content.Context
import androidx.fragment.app.FragmentActivity
import com.google.common.truth.Truth.assertThat
import es.fjmarlop.corpsecauth.core.crypto.CryptoProvider
import es.fjmarlop.corpsecauth.core.fakes.FakeAuthBackend
import es.fjmarlop.corpsecauth.core.fakes.FakeBiometricAuthenticator
import es.fjmarlop.corpsecauth.core.fakes.FakeKeyStoreManager
import es.fjmarlop.corpsecauth.core.fakes.FakePasswordManagementBackend
import es.fjmarlop.corpsecauth.core.fakes.InMemoryDeviceRegistry
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
 * Tests de los **helpers** de [EnrollmentManager]: `isDeviceEnrolled`,
 * `validateEnrollment`, `unenrollDevice`. Complementan los tests del flujo
 * principal (`EnrollmentManagerHappyPathTest` y `EnrollmentManagerRollbackTest`).
 *
 * **Por que esta clase aparte y no en el happy path / rollback test:**
 * estos metodos NO emiten Flow ni tienen rollback transaccional; son helpers
 * sincronos de query y mantenimiento. Mezclarlos enturbiaria la plantilla
 * canonica de los otros dos ficheros.
 *
 * Patron seguido: mismo setup (`@Before` con fakes frescos), mismo estilo
 * BDD en espanol, misma verificacion triple (estado/contadores fakes/mocks).
 */
internal class EnrollmentManagerHelpersTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var authBackend: FakeAuthBackend
    private lateinit var passwordManagement: FakePasswordManagementBackend
    private lateinit var biometricAuthenticator: FakeBiometricAuthenticator
    private lateinit var keyStoreManager: FakeKeyStoreManager
    private lateinit var deviceRegistry: InMemoryDeviceRegistry
    private lateinit var secureStorage: SecureStorage
    private lateinit var cryptoProvider: CryptoProvider
    private lateinit var context: Context
    private lateinit var activity: FragmentActivity
    private lateinit var enrollmentManager: EnrollmentManager

    private val testUserId = "test-uid-12345"
    private val testEncryptedToken = "dummy-encrypted-base64-token"

    @Before
    fun setUp() {
        authBackend = FakeAuthBackend()
        passwordManagement = FakePasswordManagementBackend()
        biometricAuthenticator = FakeBiometricAuthenticator()
        keyStoreManager = FakeKeyStoreManager()
        deviceRegistry = InMemoryDeviceRegistry()
        secureStorage = mockk(relaxed = false)
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

    // ============================================================
    // isDeviceEnrolled
    // ============================================================

    @Test
    fun `dado token guardado clave y userId presentes cuando isDeviceEnrolled entonces true`() = runTest {
        coEvery { secureStorage.hasStoredSession() } returns true
        coEvery { secureStorage.loadUserId() } returns Result.success(testUserId)
        keyStoreManager.forceKeyExists()

        val result = enrollmentManager.isDeviceEnrolled()

        assertThat(result).isTrue()
    }

    @Test
    fun `dado token presente pero sin clave cuando isDeviceEnrolled entonces false`() = runTest {
        coEvery { secureStorage.hasStoredSession() } returns true
        coEvery { secureStorage.loadUserId() } returns Result.success(testUserId)
        // keyStoreManager sin clave (default del fake)

        val result = enrollmentManager.isDeviceEnrolled()

        assertThat(result).isFalse()
    }

    @Test
    fun `dado clave presente pero sin token cuando isDeviceEnrolled entonces false`() = runTest {
        coEvery { secureStorage.hasStoredSession() } returns false
        coEvery { secureStorage.loadUserId() } returns Result.success(testUserId)
        keyStoreManager.forceKeyExists()

        val result = enrollmentManager.isDeviceEnrolled()

        assertThat(result).isFalse()
    }

    @Test
    fun `dado token y clave pero sin userId cuando isDeviceEnrolled entonces false`() = runTest {
        coEvery { secureStorage.hasStoredSession() } returns true
        coEvery { secureStorage.loadUserId() } returns Result.success(null)
        keyStoreManager.forceKeyExists()

        val result = enrollmentManager.isDeviceEnrolled()

        assertThat(result).isFalse()
    }

    // ============================================================
    // validateEnrollment
    // ============================================================

    @Test
    fun `dado enrollment completo cuando validateEnrollment entonces success true sin limpiar`() = runTest {
        coEvery { secureStorage.hasStoredSession() } returns true
        coEvery { secureStorage.loadUserId() } returns Result.success(testUserId)
        coEvery { secureStorage.clear() } returns Result.success(Unit)
        keyStoreManager.forceKeyExists()

        val result = enrollmentManager.validateEnrollment()

        assertThat(result.getOrNull()).isTrue()
        // No limpieza disparada (enrollment ES valido)
        assertThat(keyStoreManager.deleteKeyCallCount).isEqualTo(0)
        coVerify(exactly = 0) { secureStorage.clear() }
    }

    @Test
    fun `dado enrollment incompleto cuando validateEnrollment entonces success false y limpia datos`() = runTest {
        coEvery { secureStorage.hasStoredSession() } returns true
        coEvery { secureStorage.loadUserId() } returns Result.success(null) // userId faltante
        coEvery { secureStorage.clear() } returns Result.success(Unit)
        keyStoreManager.forceKeyExists()

        val result = enrollmentManager.validateEnrollment()

        assertThat(result.getOrNull()).isFalse()
        // SI dispara limpieza: enrollment incompleto debe restaurar estado limpio
        assertThat(keyStoreManager.deleteKeyCallCount).isEqualTo(1)
        coVerify(exactly = 1) { secureStorage.clear() }
    }

    @Test
    fun `dado SecureStorage lanza cuando validateEnrollment entonces failure preservando excepcion`() = runTest {
        val storageException = RuntimeException("DataStore IO error")
        coEvery { secureStorage.hasStoredSession() } throws storageException

        val result = enrollmentManager.validateEnrollment()

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isSameInstanceAs(storageException)
    }

    // ============================================================
    // unenrollDevice
    // ============================================================

    @Test
    fun `dado enrollment activo cuando unenrollDevice entonces revoke deleteKey clear y signOut`() = runTest {
        coEvery { secureStorage.loadUserId() } returns Result.success(testUserId)
        coEvery { secureStorage.clear() } returns Result.success(Unit)
        keyStoreManager.forceKeyExists()
        // Vincula el device para que revokeDevice tenga estado que revocar
        deviceRegistry.bindDevice(testUserId)

        val result = enrollmentManager.unenrollDevice()

        assertThat(result.isSuccess).isTrue()

        // Verificacion de cada paso del unenroll
        assertThat(deviceRegistry.revokeDeviceCallCount).isEqualTo(1)
        assertThat(deviceRegistry.devicesFor(testUserId)?.isActive).isFalse()
        assertThat(keyStoreManager.deleteKeyCallCount).isEqualTo(1)
        coVerify(exactly = 1) { secureStorage.clear() }
        assertThat(authBackend.signOutCallCount).isEqualTo(1)
    }

    @Test
    fun `dado sin userId guardado cuando unenrollDevice entonces NO revoca pero limpia local y signOut`() = runTest {
        coEvery { secureStorage.loadUserId() } returns Result.success(null)
        coEvery { secureStorage.clear() } returns Result.success(Unit)
        keyStoreManager.forceKeyExists()

        val result = enrollmentManager.unenrollDevice()

        assertThat(result.isSuccess).isTrue()

        // revokeDevice NO se llama (no hay userId)
        assertThat(deviceRegistry.revokeDeviceCallCount).isEqualTo(0)
        // Pero el resto del cleanup SI ocurre
        assertThat(keyStoreManager.deleteKeyCallCount).isEqualTo(1)
        coVerify(exactly = 1) { secureStorage.clear() }
        assertThat(authBackend.signOutCallCount).isEqualTo(1)
    }

    /**
     * Hallazgo: `unenrollDevice` IGNORA los `Result<Unit>` que devuelven sus
     * colaboradores (deleteKey, clear, etc.) — solo el catch outer se activa
     * cuando algo LANZA. Esto significa que si `keyStoreManager.deleteKey()`
     * devuelve `Result.failure`, el unenroll continua y reporta SUCCESS.
     *
     * Smell que ya estaba documentado en bugfixes.md ("comentario incorrecto
     * sobre returns de SecureStorage"). Este test lo confirma para el caso
     * de unenrollDevice.
     *
     * Para realmente disparar el catch, el colaborador debe LANZAR (no devolver
     * failure). Test usa esa via.
     */
    @Test
    fun `dado SecureStorage lanza cuando unenrollDevice entonces failure preservando excepcion`() = runTest {
        val storageException = RuntimeException("DataStore IO error")
        coEvery { secureStorage.loadUserId() } throws storageException

        val result = enrollmentManager.unenrollDevice()

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isSameInstanceAs(storageException)
    }
}
