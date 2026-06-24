package es.fjmarlop.corpsecauth.core.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test

internal class EmulatorDetectorTest {

    @Test
    fun `device fisico real no se marca como emulador`() {
        val result = EmulatorDetector.isProbablyEmulator(
            fingerprint = "google/redfin/redfin:14/UQ1A.240105.004/11206848:user/release-keys",
            model = "Pixel 5",
            manufacturer = "Google",
            brand = "google",
            device = "redfin",
            product = "redfin",
            hardware = "redfin",
        )
        assertThat(result).isFalse()
    }

    @Test
    fun `fingerprint generic se marca como emulador`() {
        val result = EmulatorDetector.isProbablyEmulator(
            fingerprint = "generic/sdk_gphone64_x86_64/emu64x:14/UE1A.230829.036/11228894:userdebug/dev-keys",
            model = "sdk_gphone64_x86_64",
            manufacturer = "Google",
            brand = "google",
            device = "emu64x",
            product = "sdk_gphone64_x86_64",
            hardware = "ranchu",
        )
        assertThat(result).isTrue()
    }

    @Test
    fun `hardware goldfish se marca como emulador`() {
        val result = EmulatorDetector.isProbablyEmulator(
            fingerprint = "x", model = "x", manufacturer = "x",
            brand = "x", device = "x", product = "x", hardware = "goldfish",
        )
        assertThat(result).isTrue()
    }

    @Test
    fun `manufacturer Genymotion se marca como emulador`() {
        val result = EmulatorDetector.isProbablyEmulator(
            fingerprint = "x", model = "x", manufacturer = "Genymotion",
            brand = "x", device = "x", product = "x", hardware = "vbox86",
        )
        assertThat(result).isTrue()
    }

    @Test
    fun `model Android SDK built for se marca como emulador`() {
        val result = EmulatorDetector.isProbablyEmulator(
            fingerprint = "x", model = "Android SDK built for x86", manufacturer = "x",
            brand = "x", device = "x", product = "x", hardware = "x",
        )
        assertThat(result).isTrue()
    }

    @Test
    fun `valores null no crashean y devuelven false`() {
        val result = EmulatorDetector.isProbablyEmulator(
            fingerprint = null, model = null, manufacturer = null,
            brand = null, device = null, product = null, hardware = null,
        )
        assertThat(result).isFalse()
    }
}
