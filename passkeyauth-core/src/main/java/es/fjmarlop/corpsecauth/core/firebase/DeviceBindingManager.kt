package es.fjmarlop.corpsecauth.core.firebase

import android.content.Context
import android.provider.Settings
import com.google.firebase.firestore.FirebaseFirestore
import es.fjmarlop.corpsecauth.core.errors.DeviceException
import es.fjmarlop.corpsecauth.core.models.DeviceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

internal class DeviceBindingManager(
    private val context: Context,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val devicesCollection = "devices"
    private val currentDeviceDoc = "current"

    private fun getDeviceId(): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }

    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    suspend fun bindDevice(userId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            println("🔗 DeviceBindingManager: Vinculando dispositivo para user: $userId")

            val deviceId = getDeviceId()
            val appVersion = getAppVersion()
            
            val deviceInfo = DeviceInfo(
                deviceId = deviceId,
                appVersion = appVersion
            )

            val deviceData = deviceInfo.toFirestoreMap().toMutableMap()
            deviceData["isActive"] = true

            firestore.collection(devicesCollection)
                .document(userId)
                .collection("history")
                .document(currentDeviceDoc)
                .set(deviceData)
                .await()

            println("✅ DeviceBindingManager: Dispositivo vinculado (deviceId: $deviceId)")
            Result.success(deviceId)

        } catch (e: Exception) {
            println("❌ DeviceBindingManager: Error vinculando dispositivo: ${e.message}")
            Result.failure(
                DeviceException.BindingFailed("Error vinculando dispositivo", e)
            )
        }
    }

    suspend fun validateDevice(userId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            println("🔍 DeviceBindingManager: Validando dispositivo para user: $userId")

            val currentDeviceId = getDeviceId()

            val snapshot = firestore.collection(devicesCollection)
                .document(userId)
                .collection("history")
                .document(currentDeviceDoc)
                .get()
                .await()

            if (!snapshot.exists()) {
                println("⚠️ DeviceBindingManager: Dispositivo no registrado")
                return@withContext Result.success(false)
            }

            val registeredDeviceId = snapshot.getString("deviceId")
            val isActive = snapshot.getBoolean("isActive") ?: false

            val isValid = (registeredDeviceId == currentDeviceId) && isActive

            if (isValid) {
                println("✅ DeviceBindingManager: Dispositivo valido")
            } else {
                println("🚨 DeviceBindingManager: Dispositivo NO valido (deviceId: $currentDeviceId, registrado: $registeredDeviceId, active: $isActive)")
            }

            Result.success(isValid)

        } catch (e: Exception) {
            println("❌ DeviceBindingManager: Error validando dispositivo: ${e.message}")
            Result.failure(
                DeviceException.ValidationFailed("Error validando dispositivo", e)
            )
        }
    }

    suspend fun revokeDevice(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            println("🗑️ DeviceBindingManager: Revocando dispositivo para user: $userId")

            firestore.collection(devicesCollection)
                .document(userId)
                .collection("history")
                .document(currentDeviceDoc)
                .update("isActive", false)
                .await()

            println("✅ DeviceBindingManager: Dispositivo revocado")
            Result.success(Unit)  // ← FIX: Unit, no deviceId

        } catch (e: Exception) {
            println("❌ DeviceBindingManager: Error revocando dispositivo: ${e.message}")
            Result.failure(
                DeviceException.BindingFailed("Error revocando dispositivo", e)
            )
        }
    }

    suspend fun getDeviceInfo(userId: String): Result<DeviceInfo?> = withContext(Dispatchers.IO) {
        try {
            val snapshot = firestore.collection(devicesCollection)
                .document(userId)
                .collection("history")
                .document(currentDeviceDoc)
                .get()
                .await()

            if (!snapshot.exists()) {
                return@withContext Result.success(null)
            }

            val deviceInfo = DeviceInfo.fromFirestoreMap(snapshot.data ?: emptyMap())
            Result.success(deviceInfo)

        } catch (e: Exception) {
            println("❌ DeviceBindingManager: Error obteniendo info: ${e.message}")
            Result.failure(
                DeviceException.ValidationFailed("Error obteniendo informacion del dispositivo", e)
            )
        }
    }

    companion object {
        fun create(context: Context) = DeviceBindingManager(context)

        fun createWithFirestore(
            context: Context,
            firestore: FirebaseFirestore
        ) = DeviceBindingManager(context, firestore)
    }
}