package es.fjmarlop.corpsecauth.core.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test

internal class HookDetectorTest {

    @Test
    fun `device sin hooks no se marca`() {
        val result = HookDetector.isHookingDetected(
            fileExists = { false },
            isPackageInstalled = { false },
            isClassLoadable = { false },
        )
        assertThat(result).isFalse()
    }

    @Test
    fun `frida-server presente se detecta`() {
        val result = HookDetector.isHookingDetected(
            fileExists = { path -> path == "/data/local/tmp/frida-server" },
            isPackageInstalled = { false },
            isClassLoadable = { false },
        )
        assertThat(result).isTrue()
    }

    @Test
    fun `instalador Xposed se detecta`() {
        val result = HookDetector.isHookingDetected(
            fileExists = { false },
            isPackageInstalled = { pkg -> pkg == "de.robv.android.xposed.installer" },
            isClassLoadable = { false },
        )
        assertThat(result).isTrue()
    }

    @Test
    fun `manager LSPosed se detecta`() {
        val result = HookDetector.isHookingDetected(
            fileExists = { false },
            isPackageInstalled = { pkg -> pkg == "org.lsposed.manager" },
            isClassLoadable = { false },
        )
        assertThat(result).isTrue()
    }

    @Test
    fun `clase XposedBridge cargable se detecta`() {
        val result = HookDetector.isHookingDetected(
            fileExists = { false },
            isPackageInstalled = { false },
            isClassLoadable = { cls -> cls == "de.robv.android.xposed.XposedBridge" },
        )
        assertThat(result).isTrue()
    }

    @Test
    fun `excepciones en las sondas no propagan`() {
        val result = HookDetector.isHookingDetected(
            fileExists = { throw SecurityException("denied") },
            isPackageInstalled = { throw RuntimeException("boom") },
            isClassLoadable = { false },
        )
        assertThat(result).isFalse()
    }
}
