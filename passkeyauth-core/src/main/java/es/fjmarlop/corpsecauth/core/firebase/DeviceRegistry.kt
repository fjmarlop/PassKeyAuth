package es.fjmarlop.corpsecauth.core.firebase

import android.content.Context
import es.fjmarlop.corpsecauth.core.models.DeviceInfo

/**
 * Contrato del registry de dispositivos vinculados a usuarios.
 *
 * Modelo "1 user = 1 device": cada usuario tiene un dispositivo activo en el
 * registry. La logica de validacion compara el deviceId del registry con el
 * deviceId del Android local.
 *
 * Ver ADR-010 para la justificacion arquitectonica.
 *
 * **Implementaciones:**
 * - [FirestoreDeviceRegistry]: implementacion real con Cloud Firestore (production).
 * - `InMemoryDeviceRegistry` (src/test/): fake in-memory para tests JVM.
 *
 * **Garantias del contrato:**
 * - [bindDevice] devuelve el deviceId asignado (puede ser ANDROID_ID u otro).
 * - [validateDevice] devuelve `true` solo si el deviceId actual coincide con
 *   el registrado Y el registro esta activo.
 * - [revokeDevice] marca el registro como inactivo (soft delete) para preservar
 *   historial.
 *
 * **Nota:** A diferencia de [AuthBackend], esta interfaz NO esta acoplada a
 * Firebase a nivel semantico. Otros backends (DynamoDB, Postgres, etc.) la
 * pueden implementar sin friccion.
 */
internal interface DeviceRegistry {

    /**
     * Vincula el dispositivo actual al usuario en el registry.
     *
     * Implementaciones tipicamente leen un identificador unico del device
     * (ej. ANDROID_ID en Android) y lo persisten asociado al userId.
     *
     * @param userId UID del usuario autenticado.
     * @return [Result.success] con el deviceId asignado o [Result.failure].
     */
    suspend fun bindDevice(userId: String): Result<String>

    /**
     * Valida que el dispositivo actual sea el registrado y activo para el usuario.
     *
     * @return [Result.success] con `true` si coincide y esta activo, `false` en otro caso.
     */
    suspend fun validateDevice(userId: String): Result<Boolean>

    /**
     * Revoca el dispositivo registrado del usuario (soft delete: `isActive = false`).
     *
     * Usado en logout, unenroll y rollback de enrollment.
     */
    suspend fun revokeDevice(userId: String): Result<Unit>

    /**
     * Devuelve la informacion del dispositivo registrado para el usuario, o `null`
     * si no hay registro.
     */
    suspend fun getDeviceInfo(userId: String): Result<DeviceInfo?>

    companion object {
        /**
         * Crea la implementacion por defecto (Firestore).
         */
        fun create(context: Context): DeviceRegistry = FirestoreDeviceRegistry.create(context)
    }
}
