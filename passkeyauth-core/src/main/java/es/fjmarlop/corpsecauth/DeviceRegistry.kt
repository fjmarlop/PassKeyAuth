package es.fjmarlop.corpsecauth

import android.content.Context
import es.fjmarlop.corpsecauth.core.models.DeviceInfo

/**
 * Contrato del registry de dispositivos vinculados a usuarios.
 *
 * Implementar esta interfaz para persistir el binding usuario-dispositivo
 * en un almacen alternativo a Cloud Firestore. Ver ADR-016.
 *
 * **Garantias del contrato:**
 * - [bindDevice] devuelve el deviceId asignado al dispositivo.
 * - [validateDevice] devuelve `true` solo si el deviceId actual coincide y esta activo.
 * - [revokeDevice] usa soft delete (preserva historial de auditoria).
 */
interface DeviceRegistry {

    /**
     * Vincula el dispositivo actual al usuario en el registry.
     *
     * @return [Result.success] con el deviceId asignado o [Result.failure].
     */
    suspend fun bindDevice(userId: String): Result<String>

    /**
     * Valida que el dispositivo actual sea el registrado y activo para el usuario.
     */
    suspend fun validateDevice(userId: String): Result<Boolean>

    /**
     * Revoca el dispositivo registrado del usuario (soft delete).
     */
    suspend fun revokeDevice(userId: String): Result<Unit>

    /**
     * Devuelve la informacion del dispositivo registrado para el usuario, o `null`.
     */
    suspend fun getDeviceInfo(userId: String): Result<DeviceInfo?>

    companion object {
        /**
         * Crea la implementacion por defecto (Firestore).
         * Llamado internamente por [PasskeyAuth] cuando no se inyecta un registry custom.
         */
        fun createDefault(context: Context): DeviceRegistry =
            es.fjmarlop.corpsecauth.core.firebase.FirestoreDeviceRegistry.create(context)
    }
}
