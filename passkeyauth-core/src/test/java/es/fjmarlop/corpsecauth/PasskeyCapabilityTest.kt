package es.fjmarlop.corpsecauth

import androidx.biometric.BiometricManager
import com.google.common.truth.Truth.assertThat
import es.fjmarlop.corpsecauth.core.auth.mapCanAuthenticateToCapability
import org.junit.Test

internal class PasskeyCapabilityTest {

    @Test
    fun `BIOMETRIC_SUCCESS mapea a Ready`() {
        assertThat(mapCanAuthenticateToCapability(BiometricManager.BIOMETRIC_SUCCESS))
            .isEqualTo(PasskeyCapability.Ready)
    }

    @Test
    fun `NONE_ENROLLED mapea a NotEnrolled (recuperable)`() {
        assertThat(mapCanAuthenticateToCapability(BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED))
            .isEqualTo(PasskeyCapability.NotEnrolled)
    }

    @Test
    fun `NO_HARDWARE mapea a NoHardware`() {
        assertThat(mapCanAuthenticateToCapability(BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE))
            .isEqualTo(PasskeyCapability.NoHardware)
    }

    @Test
    fun `HW_UNAVAILABLE mapea a TemporarilyUnavailable`() {
        assertThat(mapCanAuthenticateToCapability(BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE))
            .isEqualTo(PasskeyCapability.TemporarilyUnavailable)
    }

    @Test
    fun `SECURITY_UPDATE_REQUIRED mapea a SecurityUpdateRequired`() {
        assertThat(mapCanAuthenticateToCapability(BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED))
            .isEqualTo(PasskeyCapability.SecurityUpdateRequired)
    }
}
