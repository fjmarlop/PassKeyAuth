package es.fjmarlop.corpsecauth.core.firebase

import android.content.Context
import android.provider.Settings
import com.google.firebase.firestore.FirebaseFirestore
import es.fjmarlop.corpsecauth.DeviceRegistry
import es.fjmarlop.corpsecauth.core.errors.DeviceException
import es.fjmarlop.corpsecauth.core.models.DeviceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Implementacion Firestore de [DeviceRegistry].
 *
 * Persiste el binding usuario→dispositivo en:
 * `devices/{userId}/history/current`
 *
 * El campo `isActive` permite soft delete (revocacion) preservando historial
 * para auditoria.
 *
 * Para tests JVM, usar `InMemoryDeviceRegistry` (src/test/).
 */
internal class FirestoreDeviceRegistry(
    private val context: Context,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : DeviceRegistry {

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

    override suspend fun bindDevice(userId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
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

            Result.success(deviceId)

        } catch (e: Exception) {
            Result.failure(
                DeviceException.BindingFailed("Error vinculando dispositivo", e)
            )
        }
    }

    override suspend fun validateDevice(userId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val currentDeviceId = getDeviceId()

            val snapshot = firestore.collection(devicesCollection)
                .document(userId)
                .collection("history")
                .document(currentDeviceDoc)
                .get()
                .await()

            if (!snapshot.exists()) {
                return@withContext Result.success(false)
            }

            val registeredDeviceId = snapshot.getString("deviceId")
            val isActive = snapshot.getBoolean("isActive") ?: false

            val isValid = (registeredDeviceId == currentDeviceId) && isActive

            Result.success(isValid)

        } catch (e: Exception) {
            Result.failure(
                DeviceException.ValidationFailed("Error validando dispositivo", e)
            )
        }
    }

    override suspend fun revokeDevice(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            firestore.collection(devicesCollection)
                .document(userId)
                .collection("history")
                .document(currentDeviceDoc)
                .update("isActive", false)
                .await()

            Result.success(Unit)

        } catch (e: Exception) {
            Result.failure(
                DeviceException.BindingFailed("Error revocando dispositivo", e)
            )
        }
    }

    override suspend fun getDeviceInfo(userId: String): Result<DeviceInfo?> = withContext(Dispatchers.IO) {
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
            Result.failure(
                DeviceException.ValidationFailed("Error obteniendo informacion del dispositivo", e)
            )
        }
    }

    companion object {
        fun create(context: Context): FirestoreDeviceRegistry = FirestoreDeviceRegistry(context)

        /**
         * Crea instancia con Firestore custom (testing con Firebase emulator).
         */
        fun createWithFirestore(
            context: Context,
            firestore: FirebaseFirestore
        ): FirestoreDeviceRegistry = FirestoreDeviceRegistry(context, firestore)
    }
}
