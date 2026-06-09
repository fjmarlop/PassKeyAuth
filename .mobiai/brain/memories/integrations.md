# Integrations

<!--
Notes on third-party integrations (Firebase, analytics, push, payments,
etc.) and their project-specific configuration quirks.
-->

## Firebase

**SDK consumido vía:** `platform(libs.firebase.bom)` + `libs.bundles.firebase` en `passkeyauth-core/build.gradle.kts`.

### Firebase Authentication
**Componente del SDK:** [FirebaseAuthBackend.kt](passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/core/firebase/FirebaseAuthBackend.kt) (implementa interfaces `AuthBackend` + `PasswordManagementBackend` desde commit 3 del ADR-010)

**Uso:**
- Login con email/password temporal en enrollment
- Método clave: `invalidateTemporaryPassword()` — genera password aleatoria de 32 chars con `SecureRandom` (~200 bits entropía) y la asigna en Firebase
- Sign out en rollback de enrollment fallido

**Setup requerido (sample app):**
1. Crear proyecto en Firebase Console
2. Agregar app Android con package `es.fjmarlop.corpsecauth.sample`
3. Descargar `google-services.json` → copiar a `sample/google-services.json`
4. Habilitar **Authentication > Email/Password** en consola

**Errores manejados:** `FirebaseException` con 6 tipos.

**Quirk:** La password aleatoria nunca se almacena localmente — solo existe en Firebase Auth. Recovery requiere reset de password temporal vía IT.

### Cloud Firestore
**Componente del SDK:** [FirestoreDeviceRegistry.kt](passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/core/firebase/FirestoreDeviceRegistry.kt) (implementa interfaz `DeviceRegistry` desde commit 3 del ADR-010)

**Modelo:** "1 user = 1 device" — registry de dispositivos enrolados por usuario.

**Operaciones:**
- `bindDevice()` — paso 6 del enrollment transaccional
- `revokeDevice()` — rollback si falla el paso 7 (storage)

**Quirk:** Si falla el binding (paso 6), el rollback incluye: delete key + clear storage + sign out. Si falla el storage (paso 7), incluye revoke device.

---

## Android Platform APIs

### Android KeyStore (AndroidKeyStore)
**Componente del SDK:** [KeyStoreManager.kt](passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/core/crypto/KeyStoreManager.kt)

**Configuración:**
- Algoritmo: **AES-256-GCM** (ADR-004)
- `setIsStrongBoxBacked(true)` con try-catch — fallback automático a TEE si no disponible
- `setInvalidatedByBiometricEnrollment(true)` — invalida clave si cambian las huellas
- `setUserAuthenticationRequired(true)` — operaciones requieren biometría

**Cobertura hardware:**
- StrongBox: ~30% dispositivos (Pixel 3+, Samsung S20+, OnePlus 7T+)
- TEE: ~95% dispositivos

**Quirk:** Las claves no son extraíbles del KeyStore. Si la biometría cambia, lanza `KeyPermanentlyInvalidatedException` → invalidar sesión + forzar re-enrollment.

### AndroidX Biometric
**Componente del SDK:** [BiometricAuthenticator.kt](passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/core/auth/BiometricAuthenticator.kt)

**Dependencia:** `libs.androidx.biometric`

**Configuración:** BiometricPrompt con **STRONG biometrics** (Class 3, Android CDD).

**Quirk crítico (ADR-007):** La Activity host **DEBE extender `FragmentActivity`**. `ComponentActivity` o `AppCompatActivity` directo causa `ClassCastException` en runtime. Razón: BiometricPrompt usa Fragment transactions internamente.

```kotlin
// Correcto
class MainActivity : FragmentActivity()
```

### Jetpack DataStore (Preferences)
**Componente del SDK:** [SecureStorage.kt](passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/core/storage/SecureStorage.kt)

**Dependencia:** `libs.androidx.datastore.preferences`

**Uso:** Wrapper que cifra contenido con AES-GCM antes de persistir. Storage del token de sesión cifrado.

**Formato de storage:**
```kotlin
// EncryptedData → Base64(iv + ciphertext)
```

### AndroidX Security Crypto
**Dependencia:** `libs.androidx.security.crypto` — incluida pero el SDK usa su propio `CryptoProvider` en lugar de `EncryptedSharedPreferences`.

**Razón del descarte (ADR-004):** `EncryptedSharedPreferences` no permite integrar con `BiometricPrompt` para requerir auth biométrica por operación.

---

## Coroutines & Flow

**Dependencia:** `libs.bundles.coroutines`

**Patrón global (ADR-003):** Toda API pública es `suspend fun` o devuelve `Flow`. Wrappers internos convierten callbacks (Firebase, BiometricPrompt) con `suspendCancellableCoroutine`.

**Dispatchers convencionados:**
- `Dispatchers.IO`: red/disco
- `Dispatchers.Main`: UI
- `SupervisorJob`: trabajos independientes

---

## Build & Tooling

- **Gradle:** 9.1.0
- **Kotlin:** 2.1.0
- **JVM target:** 17
- **compileSdk:** 35 (vía version catalog)
- **minSdk:** 26 (Android 8.0)
- **Version catalog:** `gradle/libs.versions.toml`
- **BuildConfig:** habilitado en `passkeyauth-core` (necesario para detectar DEBUG en `PasskeyAuthLogger`)
