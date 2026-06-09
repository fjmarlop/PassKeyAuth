package es.fjmarlop.corpsecauth.core.crypto

import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import es.fjmarlop.corpsecauth.core.errors.CryptoException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory

/**
 * TEST PLANTILLA DE ORO 3 (ADR-011): tests instrumented en device fisico para
 * [AndroidKeyStoreManager].
 *
 * **Por que NO se puede testear en JVM (ni Robolectric):**
 * - AndroidKeyStore real solo existe en device. Robolectric usa BouncyCastle que
 *   pasa tests pero NO reproduce el comportamiento de StrongBox, TEE, ni los flags
 *   `setUserAuthenticationRequired`, `setInvalidatedByBiometricEnrollment` (ADR-004).
 * - `KeyInfo` para verificar si la clave esta en hardware solo retorna datos
 *   realistas en device fisico.
 *
 * **Que cubre este test:**
 * 1. Generacion exitosa de clave con configuracion default (StrongBox o TEE segun device)
 * 2. Persistencia entre instancias de [AndroidKeyStoreManager] (AndroidKeyStore es singleton del sistema)
 * 3. La clave generada esta hardware-backed (no software)
 * 4. `requireStrongBox = true` en device CON StrongBox → clave aterriza en StrongBox
 * 5. `requireStrongBox = true` en device SIN StrongBox → [CryptoException.StrongBoxNotAvailable]
 * 6. `getEncryptCipher()` inicializa el Cipher correctamente
 * 7. `deleteKey()` limpia el KeyStore
 *
 * **Que NO cubre (y por que):**
 * - Round-trip cifrado/descifrado: la clave requiere autenticacion biometrica
 *   (`setUserAuthenticationRequired(true)` + `validity=0`). `cipher.doFinal()` lanzaria
 *   `UserNotAuthenticatedException` sin BiometricPrompt. Eso es smoke test manual con
 *   el sample app, no test instrumented automatizado.
 * - `KeyPermanentlyInvalidatedException` tras cambio de biometria: requiere intervencion
 *   manual del usuario en Settings. Documentado en bugfixes.md.
 *
 * **Como ejecutar:**
 * ```bash
 * # Asegurarse que hay device conectado:
 * adb devices
 *
 * # Ejecutar:
 * ./gradlew passkeyauth-core:connectedDebugAndroidTest
 * ```
 *
 * El runner usa `assumeTrue`/`assumeFalse` con la feature `strongbox_keystore` para
 * skippear tests no aplicables al device. Asi este mismo fichero corre en ambos
 * devices de la matriz (con StrongBox y sin StrongBox) sin modificacion.
 */
@RunWith(AndroidJUnit4::class)
internal class AndroidKeyStoreManagerInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    /**
     * Cleanup ANTES de cada test: garantiza estado limpio del KeyStore. Necesario
     * porque AndroidKeyStore es global del sistema y los tests anteriores podrian
     * haber dejado claves.
     */
    @Before
    fun cleanKeyStoreBefore() {
        // NOTA: NO usar `= runBlocking { ... }` con expression body porque devolveria
        // Result<Unit> y JUnit 4 exige metodos @Before/@After con retorno void/Unit.
        runBlocking { AndroidKeyStoreManager().deleteKey() }
    }

    /**
     * Cleanup DESPUES de cada test: politicas de buen ciudadano. No dejamos basura
     * en el KeyStore del device.
     */
    @After
    fun cleanKeyStoreAfter() {
        runBlocking { AndroidKeyStoreManager().deleteKey() }
    }

    // ============================================================
    // Tests que corren en CUALQUIER device (con o sin StrongBox)
    // ============================================================

    @Test
    fun dado_configuracion_default_cuando_genero_clave_entonces_exito_y_persiste() = runBlocking {
        val manager = AndroidKeyStoreManager(
            userAuthenticationValiditySeconds = 0,
            requireStrongBox = false
        )

        // ARRANGE: KeyStore vacio (garantizado por @Before)
        assertThat(manager.hasKey()).isFalse()

        // ACT: generar
        val result = manager.generateKey()

        // ASSERT: exito
        assertThat(result.isSuccess).isTrue()
        assertThat(manager.hasKey()).isTrue()

        // ASSERT: persistencia entre instancias (AndroidKeyStore es singleton del sistema)
        val freshInstance = AndroidKeyStoreManager()
        assertThat(freshInstance.hasKey()).isTrue()
    }

    @Test
    fun dado_clave_generada_cuando_consulto_KeyInfo_entonces_esta_hardware_backed() = runBlocking {
        val manager = AndroidKeyStoreManager()
        manager.generateKey().getOrThrow()

        val key = manager.getKey().getOrThrow()
        val keyInfo = key.toKeyInfo()

        // El corazon del ADR-004: la clave NUNCA debe estar en software, siempre
        // en hardware (StrongBox o TEE).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // API 31+: usar getSecurityLevel (mas preciso)
            assertThat(keyInfo.securityLevel).isNotEqualTo(KeyProperties.SECURITY_LEVEL_SOFTWARE)
        } else {
            // API < 31: isInsideSecureHardware (deprecated en 31, valido en 26-30)
            @Suppress("DEPRECATION")
            assertThat(keyInfo.isInsideSecureHardware).isTrue()
        }
    }

    @Test
    fun dado_clave_generada_cuando_getEncryptCipher_entonces_devuelve_Cipher_inicializado() = runBlocking {
        val manager = AndroidKeyStoreManager()
        manager.generateKey().getOrThrow()

        val cipherResult = manager.getEncryptCipher()

        // El cipher se inicializa OK aunque setUserAuthenticationRequired=true,
        // porque init no requiere auth — solo doFinal la requiere. La auth se
        // valida cuando se usa el cipher dentro de BiometricPrompt.CryptoObject.
        assertThat(cipherResult.isSuccess).isTrue()
        val cipher = cipherResult.getOrThrow()

        // Verificamos que esta en modo ENCRYPT y tiene IV asignado (el IV se genera
        // automaticamente al iniciar el cipher en ENCRYPT mode con GCM).
        assertThat(cipher.iv).isNotNull()
        assertThat(cipher.iv).hasLength(12) // GCM IV es 12 bytes (96 bits)
    }

    @Test
    fun dado_clave_existente_cuando_deleteKey_entonces_se_elimina_del_KeyStore() = runBlocking {
        val manager = AndroidKeyStoreManager()
        manager.generateKey().getOrThrow()
        assertThat(manager.hasKey()).isTrue()

        val deleteResult = manager.deleteKey()

        assertThat(deleteResult.isSuccess).isTrue()
        assertThat(manager.hasKey()).isFalse()

        // Persistencia de la eliminacion: nueva instancia tambien ve KeyStore vacio.
        assertThat(AndroidKeyStoreManager().hasKey()).isFalse()
    }

    @Test
    fun dado_keyStore_vacio_cuando_getKey_entonces_KeyNotFound() = runBlocking {
        val manager = AndroidKeyStoreManager()
        // Sin generar — @Before garantiza KeyStore vacio.

        val result = manager.getKey()

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(CryptoException.KeyNotFound::class.java)
    }

    @Test
    fun dado_keyStore_vacio_cuando_getOrCreateKey_entonces_genera_nueva() = runBlocking {
        val manager = AndroidKeyStoreManager()

        val result = manager.getOrCreateKey()

        assertThat(result.isSuccess).isTrue()
        assertThat(manager.hasKey()).isTrue()
    }

    // ============================================================
    // Tests CONDICIONALES segun StrongBox disponibilidad
    // ============================================================

    @Test
    fun dado_device_con_StrongBox_y_requireStrongBox_cuando_genero_entonces_clave_en_StrongBox() = runBlocking {
        assumeTrue("Test aplicable solo a device CON StrongBox", deviceHasStrongBox())

        val manager = AndroidKeyStoreManager(
            userAuthenticationValiditySeconds = 0,
            requireStrongBox = true
        )

        val result = manager.generateKey()
        assertThat(result.isSuccess).isTrue()

        // Verificar que la clave aterrizo en StrongBox especificamente (no TEE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val key = manager.getKey().getOrThrow()
            val keyInfo = key.toKeyInfo()
            assertThat(keyInfo.securityLevel).isEqualTo(KeyProperties.SECURITY_LEVEL_STRONGBOX)
        }
        // API 26-30: isInsideSecureHardware no distingue StrongBox de TEE,
        // confiamos en que el flag setIsStrongBoxBacked(true) no lanzo excepcion.
    }

    @Test
    fun dado_device_sin_StrongBox_y_requireStrongBox_cuando_genero_entonces_StrongBoxNotAvailable() = runBlocking {
        assumeTrue("Test aplicable solo a device SIN StrongBox", !deviceHasStrongBox())

        val manager = AndroidKeyStoreManager(
            userAuthenticationValiditySeconds = 0,
            requireStrongBox = true
        )

        val result = manager.generateKey()

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull())
            .isInstanceOf(CryptoException.StrongBoxNotAvailable::class.java)
        assertThat(manager.hasKey()).isFalse() // no debe haber clave parcialmente generada
    }

    @Test
    fun dado_device_sin_StrongBox_y_default_cuando_genero_entonces_fallback_a_TEE() = runBlocking {
        assumeTrue("Test aplicable solo a device SIN StrongBox", !deviceHasStrongBox())

        // Default: requireStrongBox=false → intenta StrongBox y cae a TEE
        val manager = AndroidKeyStoreManager(requireStrongBox = false)

        val result = manager.generateKey()
        assertThat(result.isSuccess).isTrue()

        // En API 31+ verificamos explicitamente que es TEE (no software)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val key = manager.getKey().getOrThrow()
            val keyInfo = key.toKeyInfo()
            assertThat(keyInfo.securityLevel)
                .isEqualTo(KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT)
        }
    }

    // ============================================================
    // Helpers
    // ============================================================

    private fun deviceHasStrongBox(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
    }

    /**
     * Extrae [KeyInfo] de una SecretKey del AndroidKeyStore.
     *
     * KeyInfo expone metadata de seguridad: si la clave es hardware-backed, en que
     * nivel de seguridad, etc. Es la unica forma fiable de verificar StrongBox vs TEE.
     */
    private fun SecretKey.toKeyInfo(): KeyInfo {
        val factory = SecretKeyFactory.getInstance(algorithm, "AndroidKeyStore")
        return factory.getKeySpec(this, KeyInfo::class.java) as KeyInfo
    }
}
