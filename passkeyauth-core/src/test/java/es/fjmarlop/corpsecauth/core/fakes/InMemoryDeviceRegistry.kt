package es.fjmarlop.corpsecauth.core.fakes

import es.fjmarlop.corpsecauth.DeviceRegistry
import es.fjmarlop.corpsecauth.core.errors.DeviceException
import es.fjmarlop.corpsecauth.core.models.DeviceInfo

/**
 * Implementacion in-memory de [DeviceRegistry] para tests JVM.
 *
 * Simula el registry de Firestore con un Map en memoria. Cada usuario tiene
 * como mucho un device, con un flag `isActive` para soportar revocacion
 * (soft delete) igual que la implementacion real.
 *
 * **Patron de uso:**
 * ```kotlin
 * val registry = InMemoryDeviceRegistry(
 *     deviceIdProvider = { "fake-device-id-001" }
 * )
 *
 * // Inyectar y verificar despues del enrollment
 * assertThat(registry.bindDeviceCallCount).isEqualTo(1)
 * assertThat(registry.devicesFor("user-123").deviceId).isEqualTo("fake-device-id-001")
 * assertThat(registry.devicesFor("user-123").isActive).isTrue()
 *
 * // Simular fallo
 * registry.bindDeviceResult = Result.failure(DeviceException.BindingFailed("test"))
 * ```
 */
internal class InMemoryDeviceRegistry(
    /**
     * Provee el deviceId actual del dispositivo. En produccion la implementacion
     * real lee ANDROID_ID; aqui el test lo controla explicitamente.
     */
    private val deviceIdProvider: () -> String = { DEFAULT_DEVICE_ID }
) : DeviceRegistry {

    // === Configuracion de fallos ===

    /** Si != null, [bindDevice] devuelve este resultado en vez de hacer la logica. */
    var bindDeviceResult: Result<String>? = null

    /** Si != null, [validateDevice] devuelve este resultado. */
    var validateDeviceResult: Result<Boolean>? = null

    /** Si != null, [revokeDevice] devuelve este resultado. */
    var revokeDeviceResult: Result<Unit>? = null

    /** Si != null, [getDeviceInfo] devuelve este resultado. */
    var getDeviceInfoResult: Result<DeviceInfo?>? = null

    // === Estado interno ===

    /**
     * Estructura: userId → registro de device.
     * `isActive = false` significa revocado (soft delete).
     */
    private data class DeviceRecord(
        val deviceId: String,
        val info: DeviceInfo,
        var isActive: Boolean = true
    )

    private val devices: MutableMap<String, DeviceRecord> = mutableMapOf()

    // === Contadores ===

    var bindDeviceCallCount = 0
        private set
    var validateDeviceCallCount = 0
        private set
    var revokeDeviceCallCount = 0
        private set
    var getDeviceInfoCallCount = 0
        private set

    // === Implementacion ===

    override suspend fun bindDevice(userId: String): Result<String> {
        bindDeviceCallCount++
        bindDeviceResult?.let { return it }

        val deviceId = deviceIdProvider()
        // NOTA: Pasamos valores explicitos para todos los campos porque DeviceInfo
        // usa Build.MODEL, Build.MANUFACTURER, etc. como defaults — y esos son null
        // en JVM puro. Esta es una restriccion de los tests JVM: evitar dependencias
        // de android.os.Build. Documentado en testing.md.
        val info = DeviceInfo(
            deviceId = deviceId,
            model = "fake-model",
            manufacturer = "fake-manufacturer",
            osVersion = "fake-os",
            appVersion = "test",
            registeredAt = 0L
        )
        devices[userId] = DeviceRecord(deviceId, info, isActive = true)
        return Result.success(deviceId)
    }

    override suspend fun validateDevice(userId: String): Result<Boolean> {
        validateDeviceCallCount++
        validateDeviceResult?.let { return it }

        val record = devices[userId] ?: return Result.success(false)
        val currentDeviceId = deviceIdProvider()
        val isValid = record.deviceId == currentDeviceId && record.isActive
        return Result.success(isValid)
    }

    override suspend fun revokeDevice(userId: String): Result<Unit> {
        revokeDeviceCallCount++
        revokeDeviceResult?.let { return it }

        val record = devices[userId]
            ?: return Result.failure(DeviceException.BindingFailed("No device for user $userId"))
        record.isActive = false
        return Result.success(Unit)
    }

    override suspend fun getDeviceInfo(userId: String): Result<DeviceInfo?> {
        getDeviceInfoCallCount++
        getDeviceInfoResult?.let { return it }

        return Result.success(devices[userId]?.info)
    }

    // === Helpers de inspeccion para tests ===

    /**
     * Devuelve el registro actual para un usuario, o null si no existe.
     * NOTA: devuelve una vista del estado interno; no exponer mas alla de tests.
     */
    fun devicesFor(userId: String): DeviceSnapshot? {
        val record = devices[userId] ?: return null
        return DeviceSnapshot(record.deviceId, record.info, record.isActive)
    }

    internal data class DeviceSnapshot(
        val deviceId: String,
        val info: DeviceInfo,
        val isActive: Boolean
    )

    companion object {
        const val DEFAULT_DEVICE_ID = "fake-device-android-id-0001"
    }
}
