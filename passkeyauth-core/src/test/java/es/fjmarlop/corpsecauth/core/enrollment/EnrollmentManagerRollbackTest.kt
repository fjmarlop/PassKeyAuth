package es.fjmarlop.corpsecauth.core.enrollment

import android.content.Context
import androidx.fragment.app.FragmentActivity
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import es.fjmarlop.corpsecauth.core.crypto.CryptoProvider
import es.fjmarlop.corpsecauth.core.errors.BiometricException
import es.fjmarlop.corpsecauth.core.errors.CryptoException
import es.fjmarlop.corpsecauth.core.errors.DeviceException
import es.fjmarlop.corpsecauth.core.errors.FirebaseException
import io.mockk.every
import javax.crypto.Cipher
import javax.crypto.BadPaddingException
import es.fjmarlop.corpsecauth.core.fakes.FakeAuthBackend
import es.fjmarlop.corpsecauth.core.fakes.FakeBiometricAuthenticator
import es.fjmarlop.corpsecauth.core.fakes.FakeKeyStoreManager
import es.fjmarlop.corpsecauth.core.fakes.FakePasswordManagementBackend
import es.fjmarlop.corpsecauth.core.fakes.InMemoryDeviceRegistry
import es.fjmarlop.corpsecauth.core.models.AuthSession
import es.fjmarlop.corpsecauth.core.models.AuthUser
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
 * TEST PLANTILLA DE ORO 2 (ADR-011): rollback transaccional del enrollment.
 *
 * Complementa [EnrollmentManagerHappyPathTest] cubriendo el comportamiento opuesto:
 * cuando un paso del enrollment falla, las acciones de rollback se ejecutan en el
 * orden correcto y los pasos posteriores NO se llaman.
 *
 * **Por que este test es CRITICO:**
 * El rollback es la propiedad mas importante del [EnrollmentManager] (ADR-006).
 * Si un paso falla y el rollback no limpia el estado, el SDK queda en un estado
 * parcial inconsistente — lo que viola la garantía de "todo o nada".
 *
 * **Cobertura demostrada en este fichero (matriz COMPLETA de pasos con rollback):**
 *
 * | Paso que falla | Acciones de rollback esperadas              | Test                     |
 * |----------------|--------------------------------------------|--------------------------|
 * | 1 (login)      | (ninguna — no se ha tocado nada todavía)   | `dado login falla...`   |
 * | 3 (genKey)     | `signOut`                                   | `dado generateKey...`   |
 * | 4 (biometría)  | `deleteKey` + `signOut`                     | `dado biometria...`     |
 * | 5 (cifrado)    | `deleteKey` + `signOut`                     | `dado cipher doFinal...` |
 * | 6 (bindDevice) | `deleteKey` + `clear` + `signOut`           | `dado bindDevice...`    |
 * | 7 (storage)    | `deleteKey` + `revokeDevice` + `signOut`    | `dado saveEncrypted...` |
 *
 * El paso 2 (invalidateTemporaryPassword) esta comentado en código de producción
 * actualmente, asi que no se testea aquí. Cuando se descomente, añadir test.
 *
 * **Plantilla para tests futuros:** copiar este fichero para añadir mas escenarios
 * de rollback (paso 1, paso 3, paso 7, etc.). El patron es:
 *   1. Configurar `xxxResult = Result.failure(SpecificException(...))` en el fake del paso
 *   2. Verificar emisiones hasta Error
 *   3. Verificar contadores de rollback (que SI se llamaron)
 *   4. Verificar contadores de pasos posteriores (que NO se llamaron)
 *   5. Verificar el Error state contiene la excepción original (no perdida en el wrap)
 */
internal class EnrollmentManagerRollbackTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // === Fakes y mocks (mismo setup que happy path) ===

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

    // === Datos de prueba ===

    private val testEmail = "test@empresa.com"
    private val testPassword = "TempPass123"
    private val testUser = AuthUser(
        uid = "firebase-uid-12345",
        email = testEmail,
        displayName = "Test User",
        isEmailVerified = true
    )
    private val testSession = AuthSession(user = testUser, idToken = "fake-jwt-token")

    @Before
    fun setUp() {
        authBackend = FakeAuthBackend().apply {
            authenticateResult = Result.success(testSession)
        }
        passwordManagement = FakePasswordManagementBackend()
        biometricAuthenticator = FakeBiometricAuthenticator()
        keyStoreManager = FakeKeyStoreManager()
        deviceRegistry = InMemoryDeviceRegistry(
            deviceIdProvider = { "test-device-android-id-001" }
        )

        secureStorage = mockk(relaxed = false) {
            coEvery { saveEncryptedToken(any()) } returns Result.success(Unit)
            coEvery { saveUserId(any()) } returns Result.success(Unit)
            coEvery { saveDeviceId(any()) } returns Result.success(Unit)
            coEvery { saveLastActivityTimestamp(any()) } returns Result.success(Unit)
            coEvery { clear() } returns Result.success(Unit)
        }
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

    /**
     * **Plantilla CANÓNICA del patron rollback** — escenario más simple.
     *
     * Configuración: paso 4 (autenticación biométrica) falla por cancelación del usuario.
     * Rollback esperado según EnrollmentManager:
     *   - keyStoreManager.deleteKey() — la clave generada en paso 3 se borra
     *   - authBackend.signOut() — cerrar sesión Firebase iniciada en paso 1
     * Pasos NO ejecutados (5, 6, 7) — verificamos que sus dependencias NO se llaman.
     */
    @Test
    fun `dado biometria cancelada por usuario cuando enrolla entonces rollback borra clave y signOut`() = runTest {
        // ARRANGE: configurar paso 4 para fallar con BiometricException. UserCancelled
        val cancelException = BiometricException.UserCancelled("Usuario cancelo")
        biometricAuthenticator.encryptionResult = Result.failure(cancelException)

        // ACT + ASSERT emisiones del Flow
        enrollmentManager.enrollDevice(testEmail, testPassword).test {
            assertThat(awaitItem()).isEqualTo(EnrollmentState.Idle)
            assertThat(awaitItem()).isInstanceOf(EnrollmentState.ValidatingCredentials::class.java)
            assertThat(awaitItem()).isEqualTo(EnrollmentState.GeneratingCryptoKey)
            assertThat(awaitItem()).isInstanceOf(EnrollmentState.AwaitingBiometric::class.java)

            // El siguiente estado debe ser Error con la excepción original Preservada
            val errorState = awaitItem()
            assertThat(errorState).isInstanceOf(EnrollmentState.Error::class.java)
            val errorException = (errorState as EnrollmentState.Error).exception
            // BiometricException ES un PasskeyAuthException, asi que wrapException
            // lo devuelve sin envolver. Verificamos que es la misma instancia.
            assertThat(errorException).isSameInstanceAs(cancelException)

            awaitComplete()
        }

        // ASSERT rollback del lado de los fakes — propiedad clave del SDK:
        // las acciones de rollback se ejecutaron en orden y exactamente una vez.

        // 1. authBackend.signOut() debe haberse llamado UNA vez (rollback)
        assertThat(authBackend.signOutCallCount).isEqualTo(1)

        // 2. keyStoreManager.deleteKey() debe haberse llamado UNA vez (rollback)
        assertThat(keyStoreManager.deleteKeyCallCount).isEqualTo(1)

        // ASSERT que los pasos Posteriores al fallo NO se ejecutaron.
        // Esta es la garantía "todo o nada" del enrollment transaccional (ADR-006).

        // Paso 6 (bindDevice) NO se llamó
        assertThat(deviceRegistry.bindDeviceCallCount).isEqualTo(0)

        // Paso 7 (saveEncryptedToken) NO se llamó
        coVerify(exactly = 0) { secureStorage.saveEncryptedToken(any()) }
        coVerify(exactly = 0) { secureStorage.saveUserId(any()) }

        // passwordManagement (paso 2, comentado) NO se llamó
        assertThat(passwordManagement.invalidateCallCount).isEqualTo(0)
    }

    /**
     * **Plantilla CANÓNICA del patron rollback** — escenario más complejo.
     *
     * Configuración: paso 6 (bindDevice en Firestore) falla.
     * Rollback esperado según EnrollmentManager:
     *   - keyStoreManager.deleteKey() — la clave del paso 3
     *   - secureStorage.clear() — limpieza (aunque paso 7 no escribió aún, defensa en profundidad)
     *   - authBackend.signOut()
     * Paso 7 (saveEncryptedToken y demás) NO se llama.
     *
     * Demuestra como crece el rollback al avanzar pasos: más dependencias tocadas → más a limpiar.
     */
    @Test
    fun `dado bindDevice falla en Firestore cuando enrolla entonces rollback limpia clave storage y signOut`() = runTest {
        // ARRANGE: paso 6 falla
        val bindException = DeviceException.BindingFailed("Firestore unavailable")
        deviceRegistry.bindDeviceResult = Result.failure(bindException)

        // ACT + ASSERT emisiones
        enrollmentManager.enrollDevice(testEmail, testPassword).test {
            assertThat(awaitItem()).isEqualTo(EnrollmentState.Idle)
            assertThat(awaitItem()).isInstanceOf(EnrollmentState.ValidatingCredentials::class.java)
            assertThat(awaitItem()).isEqualTo(EnrollmentState.GeneratingCryptoKey)
            assertThat(awaitItem()).isInstanceOf(EnrollmentState.AwaitingBiometric::class.java)
            assertThat(awaitItem()).isEqualTo(EnrollmentState.BindingDevice)

            val errorState = awaitItem()
            assertThat(errorState).isInstanceOf(EnrollmentState.Error::class.java)
            val errorException = (errorState as EnrollmentState.Error).exception
            assertThat(errorException).isSameInstanceAs(bindException)

            awaitComplete()
        }

        // ASSERT rollback completo:
        assertThat(keyStoreManager.deleteKeyCallCount).isEqualTo(1)
        assertThat(authBackend.signOutCallCount).isEqualTo(1)
        coVerify(exactly = 1) { secureStorage.clear() }

        // Paso 7 NO se llamó
        coVerify(exactly = 0) { secureStorage.saveEncryptedToken(any()) }
        coVerify(exactly = 0) { secureStorage.saveUserId(any()) }
        coVerify(exactly = 0) { secureStorage.saveDeviceId(any()) }

        // bindDevice SI se llamó (es donde fallo), pero revokeDevice NO porque
        // el rollback del paso 6 no incluye revoke (no llego a registrarse exitosamente).
        assertThat(deviceRegistry.bindDeviceCallCount).isEqualTo(1)
        assertThat(deviceRegistry.revokeDeviceCallCount).isEqualTo(0)

        // Paso anterior (autenticación biométrica) si se llamó:
        assertThat(biometricAuthenticator.encryptionCallCount).isEqualTo(1)
    }

    /**
     * Rollback de PASO 1 (login con credenciales falla).
     *
     * Caso especial: no hay rollback porque no se ha tocado ningún estado todavía.
     * El test verifica que NINGÚN colaborador posterior se llama y que la excepción
     * original se propaga sin perdida.
     *
     * Importante para SDK enterprise: si las credenciales temporales son inválidas,
     * el usuario debe poder reintentar sin que el SDK haya consumido recursos.
     */
    @Test
    fun `dado login falla con credenciales invalidas entonces no se ejecuta ningun rollback`() = runTest {
        // ARRANGE: paso 1 falla
        val invalidCredentialsException =
            FirebaseException.InvalidCredentials("Password temporal incorrecta")
        authBackend.authenticateResult = Result.failure(invalidCredentialsException)

        // ACT + ASSERT emisiones
        enrollmentManager.enrollDevice(testEmail, testPassword).test {
            assertThat(awaitItem()).isEqualTo(EnrollmentState.Idle)
            assertThat(awaitItem()).isInstanceOf(EnrollmentState.ValidatingCredentials::class.java)

            val errorState = awaitItem()
            assertThat(errorState).isInstanceOf(EnrollmentState.Error::class.java)
            val errorException = (errorState as EnrollmentState.Error).exception
            assertThat(errorException).isSameInstanceAs(invalidCredentialsException)

            awaitComplete()
        }

        // ASSERT: el authBackend SI fue invocado (es donde fallamos)
        assertThat(authBackend.authenticateCallCount).isEqualTo(1)

        // ASSERT: NINGUNA acción de rollback ni paso posterior se ejecutó
        // Esto es la propiedad clave de "fallo temprano = limpio".
        assertThat(authBackend.signOutCallCount).isEqualTo(0)
        assertThat(keyStoreManager.generateKeyCallCount).isEqualTo(0)
        assertThat(keyStoreManager.deleteKeyCallCount).isEqualTo(0)
        assertThat(biometricAuthenticator.encryptionCallCount).isEqualTo(0)
        assertThat(deviceRegistry.bindDeviceCallCount).isEqualTo(0)
        assertThat(deviceRegistry.revokeDeviceCallCount).isEqualTo(0)
        assertThat(passwordManagement.invalidateCallCount).isEqualTo(0)
        coVerify(exactly = 0) { secureStorage.saveEncryptedToken(any()) }
        coVerify(exactly = 0) { secureStorage.clear() }
    }

    /**
     * Rollback de PASO 3 (generateKey en KeyStore falla).
     *
     * Caso interesante: StrongBox obligatorio en device sin StrongBox lanzaría
     * [CryptoException.StrongBoxNotAvailable]. El rollback debe deshacer el único
     * efecto colateral hasta ahora: la sesión Firebase abierta en paso 1.
     *
     * Importante: no se llama deleteKey porque la generación falla — no hay clave que borrar.
     */
    @Test
    fun `dado generateKey falla por StrongBox no disponible entonces rollback solo hace signOut`() = runTest {
        // ARRANGE: paso 3 falla
        val strongBoxException =
            CryptoException.StrongBoxNotAvailable("StrongBox requerido pero no presente")
        keyStoreManager.generateKeyResult = Result.failure(strongBoxException)

        // ACT + ASSERT emisiones
        enrollmentManager.enrollDevice(testEmail, testPassword).test {
            assertThat(awaitItem()).isEqualTo(EnrollmentState.Idle)
            assertThat(awaitItem()).isInstanceOf(EnrollmentState.ValidatingCredentials::class.java)
            assertThat(awaitItem()).isEqualTo(EnrollmentState.GeneratingCryptoKey)

            val errorState = awaitItem()
            assertThat(errorState).isInstanceOf(EnrollmentState.Error::class.java)
            val errorException = (errorState as EnrollmentState.Error).exception
            assertThat(errorException).isSameInstanceAs(strongBoxException)

            awaitComplete()
        }

        // ASSERT rollback mínimo: solo signOut (no hay clave que borrar)
        assertThat(authBackend.signOutCallCount).isEqualTo(1)
        assertThat(keyStoreManager.deleteKeyCallCount).isEqualTo(0)

        // ASSERT pasos posteriores no se ejecutaron
        assertThat(biometricAuthenticator.encryptionCallCount).isEqualTo(0)
        assertThat(deviceRegistry.bindDeviceCallCount).isEqualTo(0)
        coVerify(exactly = 0) { secureStorage.saveEncryptedToken(any()) }

        // ASSERT pasos previos SI se ejecutaron
        assertThat(authBackend.authenticateCallCount).isEqualTo(1)
        assertThat(keyStoreManager.generateKeyCallCount).isEqualTo(1)
    }

    /**
     * Rollback de PASO 7 (saveEncryptedToken falla) — escenario más critico.
     *
     * Es el rollback con MÁS acciones porque el flujo llego a vincular el device
     * en Firestore (paso 6) pero no pudo persistir localmente. El rollback debe:
     *   - `deleteKey` — quitar la clave del KeyStore
     *   - `deviceRegistry.revokeDevice` — desvincular el device del usuario en Firestore
     *     (importante: usa session.user.uid, NO el deviceId)
     *   - `authBackend.signOut` — cerrar sesión Firebase
     *
     * También verificamos que los OTROS save* del storage NO se llamaron (porque vienen
     * después de saveEncryptedToken en el código).
     */
    @Test
    fun `dado saveEncryptedToken falla entonces rollback elimina key revoca device y signOut`() = runTest {
        // ARRANGE: paso 7 falla
        val storageException = RuntimeException("DataStore IO error")
        coEvery { secureStorage.saveEncryptedToken(any()) } returns Result.failure(storageException)

        // ACT + ASSERT emisiones (flujo entero hasta paso 7)
        enrollmentManager.enrollDevice(testEmail, testPassword).test {
            assertThat(awaitItem()).isEqualTo(EnrollmentState.Idle)
            assertThat(awaitItem()).isInstanceOf(EnrollmentState.ValidatingCredentials::class.java)
            assertThat(awaitItem()).isEqualTo(EnrollmentState.GeneratingCryptoKey)
            assertThat(awaitItem()).isInstanceOf(EnrollmentState.AwaitingBiometric::class.java)
            assertThat(awaitItem()).isEqualTo(EnrollmentState.BindingDevice)

            val errorState = awaitItem()
            assertThat(errorState).isInstanceOf(EnrollmentState.Error::class.java)
            // NOTA: storageException es un RuntimeException puro (no PasskeyAuthException),
            // asi que wrapException SI lo envuelve en EnrollmentException. EnrollmentFailed.
            // No usamos isSameInstanceAs aquí — verificamos que la causa está preservada.
            val wrappedException = (errorState as EnrollmentState.Error).exception
            assertThat(wrappedException.cause).isSameInstanceAs(storageException)

            awaitComplete()
        }

        // ASSERT rollback COMPLETO (el más grande del SDK):
        assertThat(keyStoreManager.deleteKeyCallCount).isEqualTo(1)
        assertThat(deviceRegistry.revokeDeviceCallCount).isEqualTo(1)
        assertThat(authBackend.signOutCallCount).isEqualTo(1)

        // ASSERT revokeDevice se llamó con el userId correcto (NO con el deviceId)
        // — error frecuente al implementar rollback de paso 7.
        assertThat(deviceRegistry.devicesFor(testUser.uid)?.isActive).isFalse()

        // ASSERT los save* posteriores a saveEncryptedToken NO se llamaron
        coVerify(exactly = 1) { secureStorage.saveEncryptedToken(any()) } // el que fallo
        coVerify(exactly = 0) { secureStorage.saveUserId(any()) }
        coVerify(exactly = 0) { secureStorage.saveDeviceId(any()) }
        coVerify(exactly = 0) { secureStorage.saveLastActivityTimestamp(any()) }

        // ASSERT todos los pasos previos SI ocurrieron
        assertThat(authBackend.authenticateCallCount).isEqualTo(1)
        assertThat(keyStoreManager.generateKeyCallCount).isEqualTo(1)
        assertThat(biometricAuthenticator.encryptionCallCount).isEqualTo(1)
        assertThat(deviceRegistry.bindDeviceCallCount).isEqualTo(1)
    }

    /**
     * Rollback de PASO 5 (cipher.doFinal falla durante el cifrado).
     *
     * **Test de regresión** que protege contra el bug detectado durante el commit
     * inicial de testing (ver bugfixes.md): el paso 5 cifraba con `cipher.doFinal()`
     * pero NO tenia try/catch — si la operación lanzaba (BadPaddingException,
     * IllegalBlockSizeException, etc.) la excepción caía al catch outer del flow
     * que NO ejecutaba rollback. Esto violaba la garantía "todo o nada" del ADR-006.
     *
     * **Fix aplicado:** envolver el bloque de cifrado en try/catch que ejecuta el
     * rollback equivalente al del paso 4 (`deleteKey` + `signOut`) y emite
     * `EnrollmentState.Error` con [CryptoException.EncryptionFailed].
     *
     * **Estrategia del test:** inyectamos un Cipher mocker que lanza
     * `BadPaddingException` en `doFinal()`. El fake de biometric lo devuelve
     * en `encryptionResult`.
     */
    @Test
    fun `dado cipher doFinal falla en paso 5 entonces rollback borra clave y signOut`() = runTest {
        // ARRANGE: cipher mocker que lanza en doFinal
        val cipherException = BadPaddingException("Cipher invalido (simulado)")
        val failingCipher = mockk<Cipher>(relaxed = false)
        every { failingCipher.doFinal(any<ByteArray>()) } throws cipherException
        // iv se llama en la rama de éxito, no en la de fallo — pero por defensa
        // configuramos un valor por si el orden de evaluación cambia.
        every { failingCipher.iv } returns ByteArray(12)

        biometricAuthenticator.encryptionResult = Result.success(failingCipher)

        // ACT + ASSERT emisiones
        enrollmentManager.enrollDevice(testEmail, testPassword).test {
            assertThat(awaitItem()).isEqualTo(EnrollmentState.Idle)
            assertThat(awaitItem()).isInstanceOf(EnrollmentState.ValidatingCredentials::class.java)
            assertThat(awaitItem()).isEqualTo(EnrollmentState.GeneratingCryptoKey)
            assertThat(awaitItem()).isInstanceOf(EnrollmentState.AwaitingBiometric::class.java)

            // Estado siguiente: Error con CryptoException. EncryptionFailed envolviendo
            // la BadPaddingException original.
            val errorState = awaitItem()
            assertThat(errorState).isInstanceOf(EnrollmentState.Error::class.java)
            val errorException = (errorState as EnrollmentState.Error).exception
            assertThat(errorException).isInstanceOf(CryptoException.EncryptionFailed::class.java)
            assertThat(errorException.cause).isSameInstanceAs(cipherException)

            awaitComplete()
        }

        // ASSERT rollback paso 5 (equivalente al paso 4):
        assertThat(keyStoreManager.deleteKeyCallCount).isEqualTo(1)
        assertThat(authBackend.signOutCallCount).isEqualTo(1)

        // ASSERT pasos posteriores NO se ejecutaron
        assertThat(deviceRegistry.bindDeviceCallCount).isEqualTo(0)
        assertThat(deviceRegistry.revokeDeviceCallCount).isEqualTo(0)
        coVerify(exactly = 0) { secureStorage.saveEncryptedToken(any()) }
        coVerify(exactly = 0) { secureStorage.clear() }

        // ASSERT pasos previos SI ocurrieron
        assertThat(authBackend.authenticateCallCount).isEqualTo(1)
        assertThat(keyStoreManager.generateKeyCallCount).isEqualTo(1)
        assertThat(biometricAuthenticator.encryptionCallCount).isEqualTo(1)
    }
}
