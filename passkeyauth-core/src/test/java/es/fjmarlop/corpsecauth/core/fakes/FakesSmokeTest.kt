package es.fjmarlop.corpsecauth.core.fakes

import com.google.common.truth.Truth.assertThat
import es.fjmarlop.corpsecauth.core.models.AuthSession
import es.fjmarlop.corpsecauth.core.models.AuthUser
import es.fjmarlop.corpsecauth.core.models.Credentials
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Smoke test que verifica que la infraestructura de testing funciona:
 * - JUnit 4 runner
 * - kotlinx-coroutines-test (`runTest`)
 * - Truth assertions
 * - Los 5 fakes son instanciables y sus contadores funcionan
 *
 * NO es un test del SDK propiamente dicho. Sirve como canary: si esto se
 * rompe, hay algo mal en la configuracion del modulo, no en el codigo de
 * produccion.
 */
internal class FakesSmokeTest {

    @Test
    fun `FakeBiometricAuthenticator devuelve Cipher por defecto y cuenta llamadas`() = runTest {
        val fake = FakeBiometricAuthenticator()

        val result = fake.authenticateForEncryption()

        assertThat(result.isSuccess).isTrue()
        assertThat(fake.encryptionCallCount).isEqualTo(1)
    }

    @Test
    fun `FakeKeyStoreManager genera y elimina clave`() = runTest {
        val fake = FakeKeyStoreManager()

        assertThat(fake.hasKey()).isFalse()
        fake.generateKey()
        assertThat(fake.hasKey()).isTrue()
        fake.deleteKey()
        assertThat(fake.hasKey()).isFalse()

        assertThat(fake.generateKeyCallCount).isEqualTo(1)
        assertThat(fake.deleteKeyCallCount).isEqualTo(1)
    }

    @Test
    fun `FakeAuthBackend captura credenciales y devuelve session configurada`() = runTest {
        val fake = FakeAuthBackend()
        val expectedUser = AuthUser(uid = "uid-1", email = "x@y.z", isEmailVerified = true)
        fake.authenticateResult = Result.success(
            AuthSession(user = expectedUser, idToken = "tok")
        )

        val result = fake.authenticate(Credentials.EmailPassword("x@y.z", "pw"))

        assertThat(result.getOrNull()?.user).isEqualTo(expectedUser)
        assertThat(fake.lastCredentials).isInstanceOf(Credentials.EmailPassword::class.java)
        assertThat(fake.getCurrentUser()).isEqualTo(expectedUser)
    }

    @Test
    fun `FakePasswordManagementBackend cuenta llamadas`() = runTest {
        val fake = FakePasswordManagementBackend()
        fake.invalidateTemporaryPassword()
        fake.invalidateTemporaryPassword()
        assertThat(fake.invalidateCallCount).isEqualTo(2)
    }

    @Test
    fun `InMemoryDeviceRegistry vincula y revoca`() = runTest {
        val registry = InMemoryDeviceRegistry(deviceIdProvider = { "dev-001" })

        val bound = registry.bindDevice("user-1").getOrThrow()
        assertThat(bound).isEqualTo("dev-001")
        assertThat(registry.devicesFor("user-1")?.isActive).isTrue()

        registry.revokeDevice("user-1")
        assertThat(registry.devicesFor("user-1")?.isActive).isFalse()

        val valid = registry.validateDevice("user-1").getOrThrow()
        assertThat(valid).isFalse()
    }
}
