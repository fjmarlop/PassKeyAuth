package es.fjmarlop.corpsecauth.core.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test

internal class RootDetectorTest {

    @Test
    fun `device limpio no se marca como rooteado`() {
        val result = RootDetector.isProbablyRooted(
            fileExists = { false },
            isPackageInstalled = { false },
            buildTags = "release-keys",
        )
        assertThat(result).isFalse()
    }

    @Test
    fun `build tags test-keys se marca como rooteado`() {
        val result = RootDetector.isProbablyRooted(
            fileExists = { false },
            isPackageInstalled = { false },
            buildTags = "test-keys",
        )
        assertThat(result).isTrue()
    }

    @Test
    fun `binario su presente se marca como rooteado`() {
        val result = RootDetector.isProbablyRooted(
            fileExists = { path -> path == "/system/xbin/su" },
            isPackageInstalled = { false },
            buildTags = "release-keys",
        )
        assertThat(result).isTrue()
    }

    @Test
    fun `app Magisk instalada se marca como rooteado`() {
        val result = RootDetector.isProbablyRooted(
            fileExists = { false },
            isPackageInstalled = { pkg -> pkg == "com.topjohnwu.magisk" },
            buildTags = "release-keys",
        )
        assertThat(result).isTrue()
    }

    @Test
    fun `app SuperSU instalada se marca como rooteado`() {
        val result = RootDetector.isProbablyRooted(
            fileExists = { false },
            isPackageInstalled = { pkg -> pkg == "eu.chainfire.supersu" },
            buildTags = "release-keys",
        )
        assertThat(result).isTrue()
    }

    @Test
    fun `excepcion en fileExists no propaga y se trata como ausencia`() {
        val result = RootDetector.isProbablyRooted(
            fileExists = { throw SecurityException("denied") },
            isPackageInstalled = { false },
            buildTags = "release-keys",
        )
        assertThat(result).isFalse()
    }

    @Test
    fun `buildTags null no crashea`() {
        val result = RootDetector.isProbablyRooted(
            fileExists = { false },
            isPackageInstalled = { false },
            buildTags = null,
        )
        assertThat(result).isFalse()
    }
}
