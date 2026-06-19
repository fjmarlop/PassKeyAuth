package es.fjmarlop.corpsecauth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class CheckCapabilityRobolectricTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `checkCapability no lanza y devuelve un PasskeyCapability`() {
        // Robolectric sin biometría configurada → NoHardware o NotEnrolled,
        // pero NUNCA debe lanzar (contrato no-lanzante).
        val capability = PasskeyAuth.checkCapability(context)
        assertThat(capability).isInstanceOf(PasskeyCapability::class.java)
    }
}
