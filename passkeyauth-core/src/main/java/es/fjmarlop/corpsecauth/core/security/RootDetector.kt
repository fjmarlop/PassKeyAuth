package es.fjmarlop.corpsecauth.core.security

import android.os.Build

/**
 * Heurística de detección de root (ADR-015).
 *
 * Combina varias señales independientes; ninguna es concluyente por sí sola,
 * pero juntas dan una confianza razonable. Un atacante determinado puede ocultar
 * el root — esto es una capa de defensa en profundidad, no una garantía.
 *
 * Las dependencias de plataforma (sistema de ficheros, paquetes instalados, build
 * tags) se inyectan para permitir tests JVM deterministas.
 */
internal object RootDetector {

    /** Binarios típicos de un device rooteado. */
    private val SU_PATHS = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/system/app/Superuser.apk",
        "/system/su",
        "/su/bin/su",
        "/data/local/bin/su",
        "/data/local/xbin/su",
        "/data/local/su",
        "/system/bin/.ext/.su",
        "/system/usr/we-need-root/su",
        "/cache/su",
        "/system/bin/busybox",
        "/system/xbin/busybox",
    )

    /** Paquetes de apps de gestión de root. */
    private val ROOT_PACKAGES = listOf(
        "com.topjohnwu.magisk",
        "com.noshufou.android.su",
        "com.noshufou.android.su.elite",
        "eu.chainfire.supersu",
        "com.koushikdutta.superuser",
        "com.thirdparty.superuser",
        "com.yellowes.su",
        "com.kingroot.kinguser",
        "com.kingo.root",
        "com.zhiqupk.root.global",
        "com.ramdroid.appquarantine",
    )

    /**
     * @param fileExists comprueba si un path existe (default: `java.io.File.exists()`).
     * @param isPackageInstalled comprueba si un paquete está instalado.
     * @param buildTags valor de [Build.TAGS] (un device de producción nunca usa "test-keys").
     */
    fun isProbablyRooted(
        fileExists: (String) -> Boolean = { java.io.File(it).exists() },
        isPackageInstalled: (String) -> Boolean = { false },
        buildTags: String? = Build.TAGS,
    ): Boolean {
        if (buildTags != null && buildTags.contains("test-keys")) return true
        if (SU_PATHS.any { runCatching { fileExists(it) }.getOrDefault(false) }) return true
        if (ROOT_PACKAGES.any { runCatching { isPackageInstalled(it) }.getOrDefault(false) }) return true
        return false
    }
}
