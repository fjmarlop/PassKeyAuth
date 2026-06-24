package es.fjmarlop.corpsecauth.core.security

import android.os.Build

/**
 * Heurística de detección de emuladores (ADR-015).
 *
 * No existe una señal infalible — se combinan varias propiedades de [Build]
 * que los emuladores conocidos (AVD/goldfish/ranchu, Genymotion, BlueStacks, etc.)
 * exponen. La función es pura sobre sus parámetros, lo que permite testearla en
 * JVM sin device pasando valores explícitos.
 */
internal object EmulatorDetector {

    /**
     * @return true si las señales sugieren que el entorno es un emulador.
     */
    fun isProbablyEmulator(
        fingerprint: String? = Build.FINGERPRINT,
        model: String? = Build.MODEL,
        manufacturer: String? = Build.MANUFACTURER,
        brand: String? = Build.BRAND,
        device: String? = Build.DEVICE,
        product: String? = Build.PRODUCT,
        hardware: String? = Build.HARDWARE,
    ): Boolean {
        val fp = fingerprint.orEmpty()
        val md = model.orEmpty()
        val mf = manufacturer.orEmpty()
        val br = brand.orEmpty()
        val dv = device.orEmpty()
        val pr = product.orEmpty()
        val hw = hardware.orEmpty()

        if (fp.startsWith("generic") || fp.startsWith("unknown") || fp.contains("emulator")) return true
        if (md.contains("google_sdk") || md.contains("Emulator") || md.contains("Android SDK built for")) return true
        if (mf.contains("Genymotion")) return true
        if (br.startsWith("generic") && dv.startsWith("generic")) return true
        if (pr == "google_sdk" || pr == "sdk" || pr.contains("sdk_gphone") || pr.contains("vbox") || pr.contains("emulator") || pr.contains("simulator")) return true
        if (hw == "goldfish" || hw == "ranchu" || hw == "vbox86" || hw.contains("ttVM")) return true

        return false
    }
}
