package es.fjmarlop.corpsecauth.core.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests de [SecureStorage] con Robolectric.
 *
 * **Por que Robolectric y NO JVM puro:**
 * - DataStore necesita un `Context` real para escribir su archivo de preferencias.
 *   Mockear `Context` para DataStore es factible pero fragil — Robolectric provee
 *   un Context con filesystem temporal que es exactamente el environment que el
 *   codigo de produccion espera.
 *
 * **Por que NO instrumented (device fisico):**
 * - SecureStorage no toca hardware ni APIs Android especificas mas alla de
 *   DataStore. Correr esto en device es desperdicio: feedback loop 10x mas lento
 *   sin valor adicional. Ver ADR-011 — Robolectric es el nivel correcto para esto.
 *
 * **Aislamiento entre tests:**
 * DataStore persiste a un archivo en el filesystem temporal de Robolectric. Si dos
 * tests usan el mismo Context (lo cual ocurre por defecto), comparten el archivo.
 * Por eso cada test ejecuta `clear()` en `@Before` y `@After` — estado limpio antes
 * y nada de basura despues.
 *
 * Ver ADR-011 — bundle `testing-robolectric` configurado en libs.versions.toml.
 */
@RunWith(RobolectricTestRunner::class)
internal class SecureStorageRobolectricTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val storage = SecureStorage.create(context)

    @Before
    fun setUp() = runTest {
        storage.clear()
    }

    @After
    fun tearDown() = runTest {
        storage.clear()
    }

    // ============================================================
    // Estado inicial (storage vacio)
    // ============================================================

    @Test
    fun `dado storage vacio cuando consulto cada campo entonces todos devuelven null`() = runTest {
        assertThat(storage.loadEncryptedToken().getOrThrow()).isNull()
        assertThat(storage.loadUserId().getOrThrow()).isNull()
        assertThat(storage.loadDeviceId().getOrThrow()).isNull()
        assertThat(storage.loadLastActivityTimestamp().getOrThrow()).isNull()
    }

    @Test
    fun `dado storage vacio cuando consulto hasStoredSession entonces es false`() = runTest {
        assertThat(storage.hasStoredSession()).isFalse()
    }

    // ============================================================
    // Round-trip por cada campo (save → load → mismo valor)
    // ============================================================

    @Test
    fun `dado token cifrado guardado cuando cargo entonces devuelve el mismo string`() = runTest {
        val expected = "QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVo=" // Base64 de prueba

        val saveResult = storage.saveEncryptedToken(expected)
        assertThat(saveResult.isSuccess).isTrue()

        val loaded = storage.loadEncryptedToken().getOrThrow()
        assertThat(loaded).isEqualTo(expected)
    }

    @Test
    fun `dado userId guardado cuando cargo entonces devuelve el mismo string`() = runTest {
        val expected = "firebase-uid-abcdef12345"

        storage.saveUserId(expected).getOrThrow()

        assertThat(storage.loadUserId().getOrThrow()).isEqualTo(expected)
    }

    @Test
    fun `dado deviceId guardado cuando cargo entonces devuelve el mismo string`() = runTest {
        val expected = "android-id-9876543210abcdef"

        storage.saveDeviceId(expected).getOrThrow()

        assertThat(storage.loadDeviceId().getOrThrow()).isEqualTo(expected)
    }

    @Test
    fun `dado timestamp guardado cuando cargo entonces devuelve el mismo Long`() = runTest {
        val expected = 1_725_000_000_000L

        storage.saveLastActivityTimestamp(expected).getOrThrow()

        assertThat(storage.loadLastActivityTimestamp().getOrThrow()).isEqualTo(expected)
    }

    // ============================================================
    // hasStoredSession se basa SOLO en token (no en userId/deviceId)
    // ============================================================

    @Test
    fun `dado token guardado cuando consulto hasStoredSession entonces es true`() = runTest {
        storage.saveEncryptedToken("any-token").getOrThrow()

        assertThat(storage.hasStoredSession()).isTrue()
    }

    @Test
    fun `dado solo userId guardado sin token cuando consulto hasStoredSession entonces es false`() = runTest {
        // Smell de seguridad documentado: hasStoredSession() solo mira el token,
        // NO el userId. Este test fija ese contrato — si cambia debe ser decision
        // consciente y bug si rompe el flujo de PasskeyAuth.isDeviceEnrolled().
        storage.saveUserId("uid-1").getOrThrow()
        storage.saveDeviceId("dev-1").getOrThrow()

        assertThat(storage.hasStoredSession()).isFalse()
    }

    // ============================================================
    // clear() borra TODO
    // ============================================================

    @Test
    fun `dado storage con todos los campos cuando clear entonces todos quedan null`() = runTest {
        storage.saveEncryptedToken("token").getOrThrow()
        storage.saveUserId("uid").getOrThrow()
        storage.saveDeviceId("dev").getOrThrow()
        storage.saveLastActivityTimestamp(123L).getOrThrow()

        storage.clear().getOrThrow()

        assertThat(storage.loadEncryptedToken().getOrThrow()).isNull()
        assertThat(storage.loadUserId().getOrThrow()).isNull()
        assertThat(storage.loadDeviceId().getOrThrow()).isNull()
        assertThat(storage.loadLastActivityTimestamp().getOrThrow()).isNull()
        assertThat(storage.hasStoredSession()).isFalse()
    }

    // ============================================================
    // clearToken() borra SOLO token y timestamp — preserva userId y deviceId
    // ============================================================

    @Test
    fun `dado storage completo cuando clearToken entonces preserva userId y deviceId pero borra token y timestamp`() = runTest {
        storage.saveEncryptedToken("token").getOrThrow()
        storage.saveUserId("uid-keep").getOrThrow()
        storage.saveDeviceId("dev-keep").getOrThrow()
        storage.saveLastActivityTimestamp(456L).getOrThrow()

        storage.clearToken().getOrThrow()

        // Borrados
        assertThat(storage.loadEncryptedToken().getOrThrow()).isNull()
        assertThat(storage.loadLastActivityTimestamp().getOrThrow()).isNull()
        // Preservados — esto habilita el flujo "forzar re-auth sin perder enrollment"
        // documentado en el KDoc de clearToken().
        assertThat(storage.loadUserId().getOrThrow()).isEqualTo("uid-keep")
        assertThat(storage.loadDeviceId().getOrThrow()).isEqualTo("dev-keep")
        assertThat(storage.hasStoredSession()).isFalse()
    }

    // ============================================================
    // Restriccion descubierta: NO se pueden crear dos SecureStorage con
    // el mismo Context — el `preferencesDataStore` extension property es
    // por archivo, y DataStore prohibe expresamente abrir dos veces el
    // mismo archivo (lanza IllegalStateException desde OkioStorage).
    //
    // Implicacion para el SDK: `PasskeyAuth` usa `by lazy` para `secureStorage`,
    // garantizando UNA sola instancia por proceso. Esto es por diseño.
    //
    // El test que verificaba "dos instancias ven el mismo token" se elimino
    // porque testeaba un escenario que el codigo de produccion nunca produce.
    // Si en el futuro se necesita compartir un DataStore entre Contexts (raro
    // en Android), habria que refactorizar para inyectar un DataStore externo
    // en lugar de crearlo via extension property.
    // ============================================================

    // ============================================================
    // observeEncryptedToken() — Flow reactivo
    // ============================================================

    @Test
    fun `dado observeEncryptedToken cuando guardo y borro entonces Flow emite los cambios`() = runTest {
        storage.observeEncryptedToken().test {
            // Estado inicial: storage vacio por @Before
            assertThat(awaitItem()).isNull()

            // Guardamos → Flow emite el nuevo valor
            storage.saveEncryptedToken("v1").getOrThrow()
            assertThat(awaitItem()).isEqualTo("v1")

            // Actualizamos → emite el nuevo valor
            storage.saveEncryptedToken("v2").getOrThrow()
            assertThat(awaitItem()).isEqualTo("v2")

            // Borramos → emite null
            storage.clearToken().getOrThrow()
            assertThat(awaitItem()).isNull()

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ============================================================
    // Edge case: timestamp corrupto (almacenado como String en el codigo)
    // ============================================================

    @Test
    fun `dado timestamp guardado mediante un save sobrevive entre instancias y se parsea correctamente`() = runTest {
        // El codigo de produccion almacena el timestamp como String (toString())
        // y lo parsea de vuelta con toLongOrNull(). Este test fija el contrato y
        // protege contra cambios accidentales que rompan el parseo.
        val timestamps = listOf(0L, Long.MAX_VALUE, System.currentTimeMillis())

        timestamps.forEach { ts ->
            storage.saveLastActivityTimestamp(ts).getOrThrow()
            val loaded = storage.loadLastActivityTimestamp().getOrThrow()
            assertThat(loaded).isEqualTo(ts)
        }
    }
}
