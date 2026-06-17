package es.fjmarlop.corpsecauth.core.firebase

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.tasks.Task
import com.google.common.truth.Truth.assertThat
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import es.fjmarlop.corpsecauth.core.errors.DeviceException
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests de [FirestoreDeviceRegistry] con Robolectric + MockK.
 *
 * **Estrategia:** Se inyecta un [FirebaseFirestore] mockeado via
 * [FirestoreDeviceRegistry.createWithFirestore]. La cadena fluida de
 * Firestore (collection → document → collection → document → get/set/update)
 * se mockea con MockK. Las Tasks se marcan como `isComplete = true` para que
 * `kotlinx.coroutines.tasks.await()` tome el fast path síncrono.
 *
 * **Por qué Robolectric:** [FirestoreDeviceRegistry] necesita un Context real
 * para leer [Settings.Secure.ANDROID_ID] via ContentResolver. Robolectric
 * proporciona el Context mínimo para esto.
 *
 * **ANDROID_ID en tests:** Se fija a "test-device-id" en @Before para tener
 * un valor predecible en las comparaciones de dispositivo.
 *
 * Ver ADR-010 (DeviceRegistry interface), ADR-011 (testing stack).
 */
@RunWith(RobolectricTestRunner::class)
internal class FirestoreDeviceRegistryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val mockFirestore = mockk<FirebaseFirestore>()
    private lateinit var registry: FirestoreDeviceRegistry

    companion object {
        private const val TEST_USER_ID = "uid-test-001"
        private const val TEST_DEVICE_ID = "test-device-id"
    }

    @Before
    fun setup() {
        registry = FirestoreDeviceRegistry.createWithFirestore(context, mockFirestore)
        // Fijar ANDROID_ID para que getDeviceId() devuelva un valor predecible
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID,
            TEST_DEVICE_ID
        )
    }

    // ===========================================================
    // bindDevice()
    // ===========================================================

    @Test
    fun `bindDevice success escribe en la ruta correcta de Firestore y devuelve deviceId`() = runTest {
        val currentDoc = mockFirestoreChain(TEST_USER_ID)
        val capturedData = slot<Map<String, Any>>()
        every { currentDoc.set(capture(capturedData)) } returns completedVoidTask()

        val result = registry.bindDevice(TEST_USER_ID)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow()).isEqualTo(TEST_DEVICE_ID)
        // El documento debe incluir el deviceId y el flag isActive
        assertThat(capturedData.captured["deviceId"]).isEqualTo(TEST_DEVICE_ID)
        assertThat(capturedData.captured["isActive"]).isEqualTo(true)
        assertThat(capturedData.captured["appVersion"]).isNotNull()
    }

    @Test
    fun `bindDevice fallo en Firestore devuelve BindingFailed`() = runTest {
        val currentDoc = mockFirestoreChain(TEST_USER_ID)
        every { currentDoc.set(any()) } returns failedVoidTask(RuntimeException("Firestore write error"))

        val result = registry.bindDevice(TEST_USER_ID)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(DeviceException.BindingFailed::class.java)
    }

    // ===========================================================
    // validateDevice()
    // ===========================================================

    @Test
    fun `validateDevice deviceId coincide e isActive true devuelve true`() = runTest {
        val currentDoc = mockFirestoreChain(TEST_USER_ID)
        val snapshot = fakeSnapshot(
            exists = true,
            deviceId = TEST_DEVICE_ID,
            isActive = true
        )
        every { currentDoc.get() } returns completedTask(snapshot)

        val result = registry.validateDevice(TEST_USER_ID)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow()).isTrue()
    }

    @Test
    fun `validateDevice deviceId coincide pero isActive false devuelve false (dispositivo revocado)`() = runTest {
        val currentDoc = mockFirestoreChain(TEST_USER_ID)
        val snapshot = fakeSnapshot(
            exists = true,
            deviceId = TEST_DEVICE_ID,
            isActive = false
        )
        every { currentDoc.get() } returns completedTask(snapshot)

        val result = registry.validateDevice(TEST_USER_ID)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow()).isFalse()
    }

    @Test
    fun `validateDevice deviceId no coincide devuelve false (otro dispositivo registrado)`() = runTest {
        val currentDoc = mockFirestoreChain(TEST_USER_ID)
        val snapshot = fakeSnapshot(
            exists = true,
            deviceId = "otro-device-id",
            isActive = true
        )
        every { currentDoc.get() } returns completedTask(snapshot)

        val result = registry.validateDevice(TEST_USER_ID)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow()).isFalse()
    }

    @Test
    fun `validateDevice documento no encontrado devuelve false`() = runTest {
        val currentDoc = mockFirestoreChain(TEST_USER_ID)
        val snapshot = mockk<DocumentSnapshot>()
        every { snapshot.exists() } returns false
        every { currentDoc.get() } returns completedTask(snapshot)

        val result = registry.validateDevice(TEST_USER_ID)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow()).isFalse()
    }

    @Test
    fun `validateDevice fallo en Firestore devuelve ValidationFailed`() = runTest {
        val currentDoc = mockFirestoreChain(TEST_USER_ID)
        every { currentDoc.get() } returns failedTask(RuntimeException("Firestore read error"))

        val result = registry.validateDevice(TEST_USER_ID)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(DeviceException.ValidationFailed::class.java)
    }

    // ===========================================================
    // revokeDevice()
    // ===========================================================

    @Test
    fun `revokeDevice success actualiza isActive a false en Firestore`() = runTest {
        val currentDoc = mockFirestoreChain(TEST_USER_ID)
        val capturedField = slot<String>()
        val capturedValue = slot<Any>()
        every { currentDoc.update(capture(capturedField), capture(capturedValue)) } returns completedVoidTask()

        val result = registry.revokeDevice(TEST_USER_ID)

        assertThat(result.isSuccess).isTrue()
        assertThat(capturedField.captured).isEqualTo("isActive")
        assertThat(capturedValue.captured).isEqualTo(false)
    }

    @Test
    fun `revokeDevice fallo en Firestore devuelve BindingFailed`() = runTest {
        val currentDoc = mockFirestoreChain(TEST_USER_ID)
        every { currentDoc.update(any<String>(), any()) } returns failedVoidTask(RuntimeException("Update failed"))

        val result = registry.revokeDevice(TEST_USER_ID)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(DeviceException.BindingFailed::class.java)
    }

    // ===========================================================
    // getDeviceInfo()
    // ===========================================================

    @Test
    fun `getDeviceInfo documento existe devuelve DeviceInfo mapeado`() = runTest {
        val currentDoc = mockFirestoreChain(TEST_USER_ID)
        val snapshotData = mapOf(
            "deviceId" to TEST_DEVICE_ID,
            "appVersion" to "1.0.0",
            "model" to "Pixel 6",
            "manufacturer" to "Google",
            "osVersion" to "13",
            "registeredAt" to 1_000_000L
        )
        val snapshot = mockk<DocumentSnapshot>()
        every { snapshot.exists() } returns true
        every { snapshot.data } returns snapshotData
        every { currentDoc.get() } returns completedTask(snapshot)

        val result = registry.getDeviceInfo(TEST_USER_ID)

        assertThat(result.isSuccess).isTrue()
        val info = result.getOrThrow()
        assertThat(info).isNotNull()
        assertThat(info!!.deviceId).isEqualTo(TEST_DEVICE_ID)
        assertThat(info.appVersion).isEqualTo("1.0.0")
        assertThat(info.model).isEqualTo("Pixel 6")
    }

    @Test
    fun `getDeviceInfo documento no encontrado devuelve null`() = runTest {
        val currentDoc = mockFirestoreChain(TEST_USER_ID)
        val snapshot = mockk<DocumentSnapshot>()
        every { snapshot.exists() } returns false
        every { currentDoc.get() } returns completedTask(snapshot)

        val result = registry.getDeviceInfo(TEST_USER_ID)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow()).isNull()
    }

    // ===========================================================
    // Helpers privados
    // ===========================================================

    /**
     * Mockea la cadena fluida de Firestore para la ruta
     * `devices/{userId}/history/current` y devuelve el DocumentReference final.
     */
    private fun mockFirestoreChain(userId: String): DocumentReference {
        val devicesCollection = mockk<CollectionReference>()
        val userDoc = mockk<DocumentReference>()
        val historyCollection = mockk<CollectionReference>()
        val currentDoc = mockk<DocumentReference>()

        every { mockFirestore.collection("devices") } returns devicesCollection
        every { devicesCollection.document(userId) } returns userDoc
        every { userDoc.collection("history") } returns historyCollection
        every { historyCollection.document("current") } returns currentDoc

        return currentDoc
    }

    /**
     * Task<T> ya completado con éxito.
     *
     * kotlinx.coroutines.tasks.await() toma el fast path cuando isComplete=true,
     * evitando necesidad de addOnCompleteListener asíncrono.
     * isCanceled debe mockearse porque el runtime de GMS Tasks lo consulta
     * en la infraestructura de cancelación de coroutines.
     */
    private fun <T> completedTask(result: T): Task<T> = mockk<Task<T>>().also {
        every { it.isComplete } returns true
        every { it.isSuccessful } returns true
        every { it.isCanceled } returns false
        every { it.result } returns result
        every { it.exception } returns null
    }

    /** Task<Void> completado con éxito (para set/update/delete de Firestore). */
    private fun completedVoidTask(): Task<Void> = mockk<Task<Void>>().also {
        every { it.isComplete } returns true
        every { it.isSuccessful } returns true
        every { it.isCanceled } returns false
        every { it.exception } returns null
        every { it.result } answers { null }
    }

    /** Task<Void> fallido. */
    private fun failedVoidTask(exception: Exception): Task<Void> = mockk<Task<Void>>().also {
        every { it.isComplete } returns true
        every { it.isSuccessful } returns false
        every { it.isCanceled } returns false
        every { it.exception } returns exception
    }

    /** Task<T> fallido. */
    private fun <T> failedTask(exception: Exception): Task<T> = mockk<Task<T>>().also {
        every { it.isComplete } returns true
        every { it.isSuccessful } returns false
        every { it.isCanceled } returns false
        every { it.exception } returns exception
    }

    /** DocumentSnapshot mockeado con deviceId e isActive configurables. */
    private fun fakeSnapshot(exists: Boolean, deviceId: String, isActive: Boolean): DocumentSnapshot =
        mockk<DocumentSnapshot>().also {
            every { it.exists() } returns exists
            every { it.getString("deviceId") } returns deviceId
            every { it.getBoolean("isActive") } returns isActive
        }
}
