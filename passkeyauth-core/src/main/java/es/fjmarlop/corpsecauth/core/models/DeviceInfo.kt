package es.fjmarlop.corpsecauth.core.models

import android.os.Build

/**
 * Información del dispositivo para device binding.
 *
 * Esta clase encapsula los datos del dispositivo que se almacenan
 * en Firestore para identificarlo de forma única y permitir su revocación.
 *
 * @property deviceId Identificador único del dispositivo (Android ID)
 * @property model Modelo del dispositivo (ej: "Pixel 6")
 * @property manufacturer Fabricante (ej: "Google")
 * @property osVersion Versión de Android (ej: "13")
 * @property appVersion Versión de la app que registró el dispositivo
 * @property registeredAt Timestamp de registro (millis desde epoch)
 * 
 * SEGURIDAD: deviceId usa Settings.Secure.ANDROID_ID que es único
 * por app y dispositivo, y se resetea en factory reset.
 */
data class DeviceInfo(
    val deviceId: String,
    val model: String = Build.MODEL,
    val manufacturer: String = Build.MANUFACTURER,
    val osVersion: String = Build.VERSION.RELEASE,
    val appVersion: String,
    val registeredAt: Long = System.currentTimeMillis()
) {
    init {
        require(deviceId.isNotBlank()) { "deviceId no puede estar vacío" }
        require(appVersion.isNotBlank()) { "appVersion no puede estar vacío" }
    }

    /**
     * Descripción legible del dispositivo.
     * 
     * Ejemplo: "Google Pixel 6 (Android 13)"
     */
    fun getDisplayName(): String = "$manufacturer $model (Android $osVersion)"

    /**
     * Convierte a Map para guardar en Firestore.
     */
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "deviceId" to deviceId,
        "model" to model,
        "manufacturer" to manufacturer,
        "osVersion" to osVersion,
        "appVersion" to appVersion,
        "registeredAt" to registeredAt
    )

    companion object {
        /**
         * Crea DeviceInfo desde un Map de Firestore.
         */
        fun fromFirestoreMap(map: Map<String, Any>): DeviceInfo? {
            return try {
                DeviceInfo(
                    deviceId = map["deviceId"] as? String ?: return null,
                    model = map["model"] as? String ?: Build.MODEL,
                    manufacturer = map["manufacturer"] as? String ?: Build.MANUFACTURER,
                    osVersion = map["osVersion"] as? String ?: Build.VERSION.RELEASE,
                    appVersion = map["appVersion"] as? String ?: return null,
                    registeredAt = (map["registeredAt"] as? Long) ?: System.currentTimeMillis()
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}