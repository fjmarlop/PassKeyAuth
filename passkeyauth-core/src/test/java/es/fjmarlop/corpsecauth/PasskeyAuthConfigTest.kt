package es.fjmarlop.corpsecauth

import com.google.common.truth.Truth.assertThat
import org.junit.Test

internal class PasskeyAuthConfigTest {

    @Test
    fun `Default conserva sessionTimeout y aplica defaults estrictos`() {
        val cfg = PasskeyAuthConfig.Default
        assertThat(cfg.allowHostFallback).isFalse()
        assertThat(cfg.strongBox).isEqualTo(StrongBoxPolicy.Preferred)
        assertThat(cfg.recovery).isNull()
        assertThat(cfg.sessionTimeoutMinutes).isEqualTo(2)
    }

    @Test
    fun `strongBox Required deriva requireStrongBox true (compat)`() {
        val cfg = PasskeyAuthConfig.Custom(strongBox = StrongBoxPolicy.Required)
        @Suppress("DEPRECATION")
        assertThat(cfg.requireStrongBox).isTrue()
    }

    @Test
    fun `strongBox Preferred deriva requireStrongBox false (compat)`() {
        val cfg = PasskeyAuthConfig.Custom(strongBox = StrongBoxPolicy.Preferred)
        @Suppress("DEPRECATION")
        assertThat(cfg.requireStrongBox).isFalse()
    }

    @Test
    fun `Custom acepta allowHostFallback`() {
        val cfg = PasskeyAuthConfig.Custom(allowHostFallback = true)
        assertThat(cfg.allowHostFallback).isTrue()
    }

    @Test
    fun `Default aplica policies de integridad estrictas`() {
        val cfg = PasskeyAuthConfig.Default
        assertThat(cfg.rootPolicy).isEqualTo(RootPolicy.Block)
        assertThat(cfg.emulatorPolicy).isEqualTo(EmulatorPolicy.Block)
        assertThat(cfg.enablePrivacyOverlay).isTrue()
    }

    @Test
    fun `Debug es tolerante con entornos de desarrollo`() {
        val cfg = PasskeyAuthConfig.Debug
        assertThat(cfg.rootPolicy).isEqualTo(RootPolicy.Warn)
        assertThat(cfg.emulatorPolicy).isEqualTo(EmulatorPolicy.Allow)
    }

    @Test
    fun `Custom usa root Block y emulator Warn por defecto`() {
        val cfg = PasskeyAuthConfig.Custom()
        assertThat(cfg.rootPolicy).isEqualTo(RootPolicy.Block)
        assertThat(cfg.emulatorPolicy).isEqualTo(EmulatorPolicy.Warn)
        assertThat(cfg.enablePrivacyOverlay).isTrue()
    }

    @Test
    fun `Custom permite override de policies`() {
        val cfg = PasskeyAuthConfig.Custom(
            rootPolicy = RootPolicy.Allow,
            emulatorPolicy = EmulatorPolicy.Block,
            enablePrivacyOverlay = false,
        )
        assertThat(cfg.rootPolicy).isEqualTo(RootPolicy.Allow)
        assertThat(cfg.emulatorPolicy).isEqualTo(EmulatorPolicy.Block)
        assertThat(cfg.enablePrivacyOverlay).isFalse()
    }
}
