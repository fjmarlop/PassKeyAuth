package es.fjmarlop.corpsecauth.core.security

/**
 * Detección de frameworks de hooking / instrumentación dinámica (ADR-015).
 *
 * Frida y Xposed son los vectores más comunes para interceptar `BiometricPrompt`
 * a nivel de proceso y devolver éxito sin huella real, o para parchear
 * `setAllowedAuthenticators`. Detectamos:
 *  - artefactos de Frida en el sistema de ficheros (frida-server, gadget)
 *  - paquetes de Xposed/LSPosed instalados
 *  - clases de instrumentación cargadas en el classpath
 *
 * Las dependencias se inyectan para tests JVM deterministas.
 */
internal object HookDetector {

    private val FRIDA_PATHS = listOf(
        "/data/local/tmp/frida-server",
        "/data/local/tmp/re.frida.server",
        "/data/local/tmp/frida-gadget.so",
    )

    private val XPOSED_PACKAGES = listOf(
        "de.robv.android.xposed.installer",
        "org.lsposed.manager",
        "io.va.exposed",
    )

    private val HOOK_CLASSES = listOf(
        "de.robv.android.xposed.XposedBridge",
        "de.robv.android.xposed.XposedHelpers",
        "de.robv.android.xposed.IXposedHookLoadPackage",
    )

    fun isHookingDetected(
        fileExists: (String) -> Boolean = { java.io.File(it).exists() },
        isPackageInstalled: (String) -> Boolean = { false },
        isClassLoadable: (String) -> Boolean = { className ->
            runCatching { Class.forName(className) }.isSuccess
        },
    ): Boolean {
        if (FRIDA_PATHS.any { runCatching { fileExists(it) }.getOrDefault(false) }) return true
        if (XPOSED_PACKAGES.any { runCatching { isPackageInstalled(it) }.getOrDefault(false) }) return true
        if (HOOK_CLASSES.any { runCatching { isClassLoadable(it) }.getOrDefault(false) }) return true
        return false
    }
}
