package es.fjmarlop.corpsecauth.core.crypto

import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import es.fjmarlop.corpsecauth.HardwareSecurityLevel
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.SecretKey

@RunWith(RobolectricTestRunner::class)
internal class KeyAttestationVerifierTest {

    private val key = mockk<SecretKey>(relaxed = true)

    private fun verifierWith(keyInfo: KeyInfo) = KeyAttestationVerifier(
        keyInfoProvider = { Result.success(keyInfo) }
    )

    private fun verifierWithFailure(error: Throwable = RuntimeException("KeyInfo unavailable")) =
        KeyAttestationVerifier(keyInfoProvider = { Result.failure(error) })

    // --- API < 31: usa isInsideSecureHardware (deprecated) ---

    @Test
    @Config(sdk = [28])
    fun `api28 - hardware backed retorna TRUSTED_ENVIRONMENT`() {
        val keyInfo = mockk<KeyInfo>()
        @Suppress("DEPRECATION")
        every { keyInfo.isInsideSecureHardware } returns true

        assertEquals(
            HardwareSecurityLevel.TRUSTED_ENVIRONMENT,
            verifierWith(keyInfo).checkSecurityLevel(key)
        )
    }

    @Test
    @Config(sdk = [28])
    fun `api28 - software backed retorna SOFTWARE`() {
        val keyInfo = mockk<KeyInfo>()
        @Suppress("DEPRECATION")
        every { keyInfo.isInsideSecureHardware } returns false

        assertEquals(
            HardwareSecurityLevel.SOFTWARE,
            verifierWith(keyInfo).checkSecurityLevel(key)
        )
    }

    // --- API 31+: usa securityLevel ---

    @Test
    @Config(sdk = [31])
    fun `api31 - SECURITY_LEVEL_STRONGBOX retorna STRONGBOX`() {
        val keyInfo = mockk<KeyInfo>()
        @Suppress("NewApi")
        every { keyInfo.securityLevel } returns KeyProperties.SECURITY_LEVEL_STRONGBOX

        assertEquals(
            HardwareSecurityLevel.STRONGBOX,
            verifierWith(keyInfo).checkSecurityLevel(key)
        )
    }

    @Test
    @Config(sdk = [31])
    fun `api31 - SECURITY_LEVEL_TRUSTED_ENVIRONMENT retorna TRUSTED_ENVIRONMENT`() {
        val keyInfo = mockk<KeyInfo>()
        @Suppress("NewApi")
        every { keyInfo.securityLevel } returns KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT

        assertEquals(
            HardwareSecurityLevel.TRUSTED_ENVIRONMENT,
            verifierWith(keyInfo).checkSecurityLevel(key)
        )
    }

    @Test
    @Config(sdk = [31])
    fun `api31 - SECURITY_LEVEL_SOFTWARE retorna SOFTWARE`() {
        val keyInfo = mockk<KeyInfo>()
        @Suppress("NewApi")
        every { keyInfo.securityLevel } returns KeyProperties.SECURITY_LEVEL_SOFTWARE

        assertEquals(
            HardwareSecurityLevel.SOFTWARE,
            verifierWith(keyInfo).checkSecurityLevel(key)
        )
    }

    // --- Fallo del proveedor ---

    @Test
    @Config(sdk = [28])
    fun `fallo del KeyInfoProvider retorna UNKNOWN`() {
        assertEquals(
            HardwareSecurityLevel.UNKNOWN,
            verifierWithFailure().checkSecurityLevel(key)
        )
    }
}
