# Testing Patterns

<!--
Reusable testing patterns discovered for this project.
Append entries with: mobiai brain save testing (coming in Phase 2).
Include the problem, the pattern that solved it and a minimal example.
-->

## Estado actual

**Infraestructura COMPLETA. Lista para escribir tests.**

ADR-010 (interfaces) ✅ y ADR-011 (stack + fakes + JaCoCo) ✅ completos. Lo único pendiente es escribir los tests reales del SDK.

Las dependencias de testing ya están declaradas en `passkeyauth-core/build.gradle.kts`:
- `testImplementation(libs.bundles.testing.unit)`
- `androidTestImplementation(libs.bundles.testing.android)`

### Matriz de devices físicos disponibles

| Device | StrongBox | Uso en tests |
|---|---|---|
| Device A (con `strongbox_keystore=300`) | ✅ | Path StrongBox real, validación ADR-004 |
| Device B (sin feature) | ❌ | Path TEE fallback |

Cobertura suficiente para validar ADR-004 completo en instrumented tests.

### Refactor de testabilidad en curso (ADR-010)

Antes de escribir tests, se extraen 3 interfaces `internal` que abstraen las fronteras de plataforma:

- ✅ **`BiometricAuthenticator`** (commit 1) — extraída. Impl: `AndroidBiometricAuthenticator`. Build verde.
- ✅ **`KeyStoreManager`** (commit 2) — extraída. Impl: `AndroidKeyStoreManager`. Build verde, sin warnings. (Renombrada desde `CryptoKeyProvider` propuesta inicial — ver revisión en ADR-010.)
- ✅ **`AuthBackend` + `PasswordManagementBackend` + `DeviceRegistry`** (commit 3, **Path C**) — extraídas. Impls: `FirebaseAuthBackend` (implementa las DOS interfaces de auth), `FirestoreDeviceRegistry`. Modelos nuevos: `AuthSession` (con campos nullable para evolución OAuth2), `Credentials` sealed class. Full project build verde. Ficheros antiguos `FirebaseAuthManager.kt` y `DeviceBindingManager.kt` eliminados.

**Refactor de testabilidad COMPLETO.** `EnrollmentManager` ahora puede testearse 100% en JVM con fakes para todas sus 7 dependencias.

### Infraestructura de testing creada (ADR-011)

**Stack:**
- JUnit 4.13.2 (NO 5 — AGP integration compleja)
- MockK 1.13.13 (NO Mockito — final classes / sealed)
- Truth 1.4.4 de Google (NO Kotest assertions — evitar pulling runner Kotest)
- Turbine 1.2.0 (para `Flow<EnrollmentState>`)
- kotlinx-coroutines-test con `StandardTestDispatcher`
- Robolectric 4.14 (SOLO para `SecureStorage`/DataStore)
- JaCoCo 0.8.12 con exclusiones agresivas

**Fakes ya creados en `passkeyauth-core/src/test/java/.../fakes/`:**
- `FakeBiometricAuthenticator` — devuelve `Cipher` software-backed reales por defecto
- `FakeKeyStoreManager` — genera/elimina claves AES en memoria, simula StrongBox no disponible
- `FakeAuthBackend` — captura credenciales, mantiene currentUser in-memory
- `FakePasswordManagementBackend` — contador de invalidaciones (para cuando se descomente paso 2)
- `InMemoryDeviceRegistry` — Map<userId, DeviceRecord> con soft delete via `isActive`

**Support:**
- `MainDispatcherRule` en `core/support/` — sustituye `Dispatchers.Main` en tests
- `FakesSmokeTest` (canary) — 5 tests verdes que validan que la infra entera funciona

**Verificacion del setup:**
- ✅ `./gradlew passkeyauth-core:testDebugUnitTest` → smoke test verde en 3s
- ✅ `./gradlew passkeyauth-core:jacocoTestReport` → HTML + XML generandose en `build/reports/jacoco/jacocoTestReport/`

### Restricciones JVM descubiertas (importante al escribir tests)

1. **`android.os.Build.*` es `null`** — usar valores explicitos al construir `DeviceInfo`, etc.
2. **`Cipher` Java funciona** sin Android — los fakes biometric/keystore retornan Ciphers reales software-backed.
3. **Firebase NO se mockea** — usar `FakeAuthBackend` o (futuro) Firebase emulator suite.
4. **JaCoCo `.exec` en AGP 9** vive en `outputs/unit_test_code_coverage/debugUnitTest/`, no en `build/jacoco/`.

### Tests "plantilla de oro"

- ✅ **1. Happy path completo de `EnrollmentManager.enrollDevice()`** (JVM, en `enrollment/EnrollmentManagerHappyPathTest.kt`) — verde. ~150 LOC con comentarios. **Plantilla canónica del patrón "verificar éxito + emisiones del flow + sin rollback".**
- ✅ **2. Rollback transaccional COMPLETO** (JVM, en `enrollment/EnrollmentManagerRollbackTest.kt`) — **5 tests verdes cubriendo toda la matriz de rollback**:

  | Paso que falla | Rollback esperado | Test |
  |---|---|---|
  | 1 (login) | (ninguno — fallo temprano = limpio) | `dado login falla con credenciales invalidas...` |
  | 3 (generateKey) | `signOut` (sin deleteKey: no hay key) | `dado generateKey falla por StrongBox...` |
  | 4 (biometría) | `deleteKey` + `signOut` | `dado biometria cancelada...` |
  | 5 (cifrado) | `deleteKey` + `signOut` | `dado cipher doFinal falla en paso 5...` |
  | 6 (bindDevice) | `deleteKey` + `clear` + `signOut` | `dado bindDevice falla en Firestore...` |
  | 7 (storage) | `deleteKey` + `revokeDevice` + `signOut` | `dado saveEncryptedToken falla...` |

  - **Bug del paso 5 resuelto** (ver `bugfixes.md`): el código añade try/catch alrededor de `cipher.doFinal()` con rollback equivalente al paso 4. Test de regresión inyecta `Cipher` mockeado con MockK que lanza `BadPaddingException`. **Matriz de rollback ahora completa para los 6 pasos no-comentados** (queda fuera paso 2, comentado en producción).
  - **Plantilla canónica del patrón rollback:** copiar para nuevos escenarios cambiando solo `xxxResult = Result.failure(...)` y los `coVerify(exactly = 0)`.
  - Cada test valida también que la excepción original se preserva en `EnrollmentState.Error.exception` (con `isSameInstanceAs` para `PasskeyAuthException` o `cause` para excepciones envueltas).
- ✅ **3. `AndroidKeyStoreManager` instrumented en device físico** (`src/androidTest/.../crypto/AndroidKeyStoreManagerInstrumentedTest.kt`) — **9 tests, 7 pasan + 2 skipped en device con StrongBox**.

  **Cobertura de propiedades del ADR-004 validadas con hardware real:**

  | Test | Universal / Condicional | Estado en device A (StrongBox) |
  |---|---|---|
  | Default config genera clave + persiste entre instancias | Universal | ✅ PASSED |
  | Clave es hardware-backed (KeyInfo.securityLevel != SOFTWARE) | Universal | ✅ PASSED |
  | `getEncryptCipher()` devuelve Cipher con IV de 12 bytes | Universal | ✅ PASSED |
  | `deleteKey()` limpia + persiste eliminación | Universal | ✅ PASSED |
  | `getKey()` sin clave → `KeyNotFound` | Universal | ✅ PASSED |
  | `getOrCreateKey()` sin clave previa → genera | Universal | ✅ PASSED |
  | `requireStrongBox=true` + device CON StrongBox → clave en StrongBox (KeyInfo.SECURITY_LEVEL_STRONGBOX) | Solo con StrongBox | ✅ PASSED |
  | `requireStrongBox=true` + device SIN StrongBox → `StrongBoxNotAvailable` | Solo sin StrongBox | ⏭️ SKIPPED (device tiene StrongBox) |
  | Default + device SIN StrongBox → TEE fallback (KeyInfo.SECURITY_LEVEL_TRUSTED_ENVIRONMENT) | Solo sin StrongBox | ⏭️ SKIPPED |

  **✅ MATRIZ COMPLETA (2026-06-10):** ejecución en device B (Xiaomi Mi 9T, API 29, sin StrongBox) — **8 PASSED + 1 SKIPPED**. Los 2 tests que estaban skipped en device A pasaron correctamente. El test "StrongBox-positive" se salta correctamente en este device. **Cobertura del ADR-004 con hardware real al 100%.**

  | Test | Device A (StrongBox) | Device B (TEE) |
  |---|---|---|
  | 6 tests universales | ✅✅✅✅✅✅ | ✅✅✅✅✅✅ |
  | `requireStrongBox + StrongBox` → clave en StrongBox | ✅ | ⏭️ |
  | `requireStrongBox + sin StrongBox` → `StrongBoxNotAvailable` | ⏭️ | ✅ |
  | Default + sin StrongBox → fallback a TEE (verificado vía `KeyInfo.SECURITY_LEVEL_TRUSTED_ENVIRONMENT`) | ⏭️ | ✅ |

### Smoke test manual (release checklist)

Hay un checklist manual en `docs/MANUAL-SMOKE-TEST.md` que cubre lo que los tests automatizados NO pueden:
- Interacción real con `BiometricPrompt` (huella física, cancelación, lockout)
- Cambio de biometría → `KeyPermanentlyInvalidatedException` (escenario clave de seguridad del ADR-004)
- Revocación remota vía Firestore
- Backgrounding y rotación de Activity durante el prompt

**10 escenarios (S1-S10)** con pasos, expected y pass criteria. Cinco son bloqueantes para release: S1 (enrollment), S2 (login), S3 (cancelación enrollment), S6 (cambio biometría), S8 (revocación remota).

Referenciado desde `DEVELOPMENT.md` en el "Release Process" como paso 5 — bloqueante antes de publicar Maven Central / GitHub Release.

  **Comandos:**
  ```bash
  adb devices                                                # verificar device conectado
  ./gradlew passkeyauth-core:connectedDebugAndroidTest       # build + install + run automatico
  ```

  **Patrones que demuestra el test:**
  - `@RunWith(AndroidJUnit4::class)` para JUnit 4 + AndroidX test runner
  - `ApplicationProvider.getApplicationContext()` para Context real
  - `@Before` / `@After` con `runBlocking { }` para limpiar KeyStore entre tests — **OJO:** usar block body (no `=`) para que retornen Unit (JUnit 4 lo exige)
  - `assumeTrue` / `assumeFalse` con feature flags para tests condicionales por hardware
  - `KeyInfo` via `SecretKeyFactory.getKeySpec(key, KeyInfo::class.java)` para verificar StrongBox vs TEE vs SOFTWARE
  - Diferencia API 31+: usar `getSecurityLevel()` (preciso); API 26-30: `isInsideSecureHardware()` (deprecado pero funciona)

**Estado de la suite:**
- **JVM puro + Robolectric:** **49 verdes + 1 ignored** en <20s:
  - 5 `FakesSmokeTest` (canary)
  - 1 `EnrollmentManagerHappyPathTest`
  - 6 `EnrollmentManagerRollbackTest` (matriz completa: pasos 1, 3, 4, 5, 6, 7)
  - **10 `EnrollmentManagerHelpersTest`** ← NUEVO (`isDeviceEnrolled`, `validateEnrollment`, `unenrollDevice`)
  - 12 `SecureStorageRobolectricTest`
  - **15 `PasskeyAuthFacadeTest`** ← NUEVO (initialize, lifecycle hooks, session timeout, invalidateSession, isAuthenticated, requireInitialized) + 1 `@Ignore` (`logout` — limitación conocida del patrón `mockkObject` + `by lazy`; cubierto por smoke S7)

**Cobertura JaCoCo:** 22.7% instrucciones / 21.9% branches / 20.9% métodos. Dobló desde la sesión anterior (11.2%/7%/3%).
- **Instrumented:**
  - Device A (Pixel-like con StrongBox, API alto): 7 PASSED + 2 SKIPPED
  - Device B (Xiaomi Mi 9T, API 29 sin StrongBox): 8 PASSED + 1 SKIPPED
  - **Matriz ADR-004 completa al 100%** — todos los paths validados en hardware real.
- **Total verde:** **26 ejecuciones verdes** (11 JVM + 15 instrumented entre ambos devices).

**Cobertura JaCoCo global:** 14.5% instrucciones, 9.2% branches. Métricas bajas porque solo cubrimos `EnrollmentManager` aún. Componentes pendientes de tests: `PasskeyAuth` facade, `FirebaseAuthBackend` (Firebase emulator), `FirestoreDeviceRegistry` (emulator), `AndroidKeyStoreManager` (instrumented), `SecureStorage` (Robolectric), `BiometricAuthenticator` (smoke manual).

**Quirk de JaCoCo + coroutines:** `flow { ... }` builder genera clases sintéticas que aparecen como entradas separadas. La cobertura "por clase" del `EnrollmentManager` outer parece baja (13%) pero el grueso del código del orquestador vive en las clases sintéticas del flow body. Para cobertura realista, mirar el total acumulado y/o el HTML.

### Patrón canónico: testar `object` singleton con `by lazy` (PasskeyAuthFacadeTest)

`PasskeyAuth` es un singleton `object` con propiedades `by lazy { CompanionFactory() }`. Inyectar fakes sin tocar producción requiere truco:

```kotlin
@Before
fun setUp() {
    PasskeyAuth.reset()  // limpia estado del singleton

    mockkObject(FirebaseAuthBackend.Companion)
    every { FirebaseAuthBackend.createDefault() } returns mockBackend

    mockkObject(KeyStoreManager.Companion)
    every { KeyStoreManager.createDefault() } returns fakeKeyStoreManager
    // ...
}

@After
fun tearDown() {
    unmockkAll()
    PasskeyAuth.reset()
}
```

**Para manipular estado interno privado** (timestamp para test de timeout, `_authState` para simular Authenticated):
```kotlin
val timestampField = PasskeyAuth::class.java.getDeclaredField("lastActivityTimestamp").apply {
    isAccessible = true
}
timestampField.setLong(PasskeyAuth, System.currentTimeMillis() - (10L * 60_000))
```

**Restricciones importantes con este patrón:**

1. **`initialize()` dispara `refreshAuthState()` async** en `scope.launch(Dispatchers.IO)` que no controlamos con `runTest`. Después de initialize, hacer `Thread.sleep(150)` antes de manipular `_authState` — si no, el refresh async sobreescribe nuestra manipulación.

2. **`by lazy` solo evalúa UNA vez por proceso de test**, no por test. `PasskeyAuth.reset()` NO resetea el delegate. Tests que `coVerify` sobre el mock fresco de `@Before` pueden fallar porque el `by lazy` ya retornó el mock del test anterior. Workaround: `@Ignore` con justificación o cubrir vía smoke test instead.

3. **`Dispatchers.IO`** usado por `scope.launch` interno del SDK NO se intercepta con `MainDispatcherRule` (que solo cubre `Dispatchers.Main`). Para tests que dependen de timing del IO, usar `Thread.sleep` (wall-clock) en lugar de `delay()` (virtual time de `runTest`).

### Plantilla canónica: patrones del happy path test

**Cuando escribas un test nuevo, copia esta estructura:**

1. **`@get:Rule MainDispatcherRule`** — siempre. Sin esto, cualquier uso de `Dispatchers.Main` interno crashea en JVM.
2. **Fakes en propiedades `lateinit var` + setup en `@Before`** — instancias frescas por test, aislamiento total.
3. **MockK para colaboradores sin interfaz** (`SecureStorage`, `Context`, `FragmentActivity`, `CryptoProvider`).
   - `coEvery { ... } returns Result.success(Unit)` para `suspend fun: Result<T>`
   - `mockk(relaxed = true)` para colaboradores que el código bajo test ignora
4. **Datos de prueba en propiedades** (`testEmail`, `testUser`, `testSession`) — reutilizables, legibles.
5. **`runTest { ... }`** — NUNCA `runBlocking`.
6. **Turbine `.test { ... }` para Flow** — `awaitItem()` por cada estado esperado, `awaitComplete()` al final.
7. **Verificación triple:** (a) emisiones del flow, (b) contadores de los fakes, (c) `coVerify` de los mocks.
8. **Aserciones negativas del happy path:** "NO se llamó a `deleteKey`", "NO se llamó a `signOut`", "NO se llamó a `revokeDevice`" — confirma que el rollback NO se disparó.
9. **Nombres BDD en español:** `dado un X cuando Y entonces Z` — reportes legibles.

### Patrón rollback (segunda plantilla canónica)

Para escribir un test de rollback:
1. **Mismo setup que happy path** (todos los fakes configurados al estado de éxito).
2. **Configurar el resultado del paso que debe fallar:**
   - Paso 1 (`authBackend`): `authenticateResult = Result.failure(FirebaseException...)`
   - Paso 3 (`keyStoreManager`): `generateKeyResult = Result.failure(CryptoException...)`
   - Paso 4 (`biometric`): `encryptionResult = Result.failure(BiometricException...)`
   - Paso 6 (`deviceRegistry`): `bindDeviceResult = Result.failure(DeviceException...)`
   - Paso 7 (`secureStorage` mock): `coEvery { saveEncryptedToken(any()) } returns Result.failure(...)`
3. **Verificar emisiones hasta `Error`** con Turbine — y que el `Error.exception` ES la excepción original (no perdida en el wrap). Usar `assertThat(...).isSameInstanceAs(originalException)` cuando la excepción ya es un `PasskeyAuthException` (no se envuelve).
4. **Verificar que las acciones de rollback SÍ ocurrieron** (contadores `>= 1`).
5. **Verificar que los pasos posteriores NO ocurrieron** (contadores `= 0`, `coVerify(exactly = 0)`).

### Restricciones de tests INSTRUMENTED descubiertas escribiendo el test 3

1. **`@Before`/`@After` con `runBlocking { ... }`:** JUnit 4 exige métodos `void`/`Unit`. Si usas expression body `fun setup() = runBlocking { ... }`, el método retorna el resultado del bloque (ej. `Result<Unit>` si la última expresión es un `suspend fun: Result<T>`). **Fix:** usar block body explícito `fun setup() { runBlocking { ... } }`. El runtime error es críptico (`Method setup-d1pmJ48() should be void`).

2. **`INSTALL_FAILED_VERIFICATION_FAILURE` al ejecutar `connectedAndroidTest`:** Play Protect/MIUI/etc. rechazan instalar APKs de test. **Fix en `build.gradle.kts`:**
   ```kotlin
   android {
       installation {
           installOptions += listOf("-t")
       }
   }
   ```
   El flag `-t` indica "este APK es de testing" — los devices lo aceptan sin Play Protect.

3. **`6 files found with path 'META-INF/LICENSE.md'`:** JUnit Jupiter 5 entra como transitivo (vía MockK u otros) y duplica licencias en el APK de test. **Fix en `build.gradle.kts`:**
   ```kotlin
   android {
       packaging {
           resources {
               excludes += setOf("META-INF/LICENSE.md", "META-INF/LICENSE-notice.md", ...)
           }
       }
   }
   ```

4. **Bypass de Gradle para debugging rápido:** si `connectedAndroidTest` falla por install, se puede:
   ```bash
   ./gradlew :passkeyauth-core:assembleDebugAndroidTest
   adb install -r -t passkeyauth-core/build/outputs/apk/androidTest/debug/passkeyauth-core-debug-androidTest.apk
   adb shell am instrument -w -e class <FQN> <package>.test/androidx.test.runner.AndroidJUnitRunner
   ```
   El status code de cada test en el output de `am instrument`: `0`=PASSED, `-1`=FAIL, `-2`/`-4`=SKIPPED por assumption.

### Más restricciones JVM descubiertas escribiendo el primer test

1. **`SecureStorage.saveUserId/saveDeviceId/saveLastActivityTimestamp`** SÍ devuelven `Result<Unit>` (no `Unit` como dice el comentario incorrecto en `EnrollmentManager`). Usar `coEvery { ... } returns Result.success(Unit)`, NO `coJustRun`.
2. **`android.util.Base64.encodeToString` retorna `null` en JVM puro** (con `isReturnDefaultValues=true`) → ✅ **Resuelto migrando a `java.util.Base64` en producción** (`EnrollmentManager` paso 5, `EncryptedData.kt`). Disponible desde API 26 = minSdk. Documentado en ADR-011, ver `bugfixes.md`. **Lección general:** preferir `java.util.*` sobre `android.util.*` cuando exista equivalente al mismo API level — gana portabilidad sin coste.
3. **MockK con `mockk(relaxed = true)` y final classes funciona out-of-the-box** — la versión 1.13.13 incluye el inline mock maker sin configuración extra.

### Patrón Robolectric (tercera plantilla canónica)

Para escribir tests Robolectric (ver `SecureStorageRobolectricTest.kt`):
1. `@RunWith(RobolectricTestRunner::class)` en la clase
2. `private val context: Context = ApplicationProvider.getApplicationContext()` para obtener `Context`
3. `@Before` y `@After` con `storage.clear()` para aislar tests — DataStore persiste a un archivo en filesystem temporal y compartiría estado entre tests sin esta limpieza
4. Sin `MainDispatcherRule` — Robolectric provee un `Dispatchers.Main` válido
5. Bundle del version catalog: `libs.bundles.testing.robolectric` (incluye `androidx-test-core` para `ApplicationProvider`)

### Restricciones descubiertas con Robolectric

1. **`preferencesDataStore` extension prohibe dos DataStores para el mismo archivo.** Crear dos `SecureStorage` con el mismo `Context` lanza `IllegalStateException` desde `OkioStorage.kt:65`. El test que verificaba "dos instancias comparten estado" se eliminó porque testeaba un escenario que `PasskeyAuth` (con `by lazy`) nunca produce.
2. **JaCoCo no ve cobertura de tests Robolectric.** Robolectric usa `SandboxClassLoader` para inyectar clases Android, lo cual esquiva la instrumentación de JaCoCo. Resultado: el reporte muestra `SecureStorage` con 0% covered aunque los 12 tests pasan y la validan end-to-end. **Esto es limitación de tooling, no de los tests.** Documentado como "esperado" — no perseguir métricas de cobertura aquí.

Tras los 3 commits → escribir 3 tests "plantilla de oro":
1. Happy path de `EnrollmentManager.enrollDevice()` (JVM)
2. Rollback paso 5 del enrollment (JVM)
3. `AndroidKeyStoreCryptoProvider` con StrongBox real (instrumented)

---

## Comandos

```bash
.\gradlew.bat test                    # Unit tests
.\gradlew.bat connectedAndroidTest    # Integration tests en device
.\gradlew.bat jacocoTestReport        # Coverage report
.\gradlew.bat detekt                  # Static analysis
.\gradlew.bat lint                    # Android lint
```

---

## Patrones planificados

### Unit tests (`src/test/java/`)

**Patrón con MockK + coroutines test + `Result<T>`:**
```kotlin
@Test
fun `authenticate should return success when biometric succeeds`() = runTest {
    // Given
    val authenticator = BiometricAuthenticator(mockContext)
    coEvery { mockBiometricPrompt.authenticate() } returns AuthResult.Success

    // When
    val result = authenticator.authenticate()

    // Then
    assertTrue(result.isSuccess)
}
```

**Para testear `Flow<EnrollmentState>` del `EnrollmentManager`:** usar **Turbine**.
```kotlin
enrollmentManager.enrollDevice(email, tempPassword).test {
    assertEquals(EnrollmentState.ValidatingCredentials(email), awaitItem())
    assertEquals(EnrollmentState.GeneratingCryptoKey, awaitItem())
    // ...
    awaitComplete()
}
```

### Integration tests (`src/androidTest/java/`)

**Patrón con Robolectric para KeyStore:**
```kotlin
@RunWith(RobolectricTestRunner::class)
class KeyStoreManagerTest {
    @Test
    fun `should generate key in KeyStore`() {
        val manager = KeyStoreManager(ApplicationProvider.getApplicationContext())
        val result = manager.generateKey()
        assertTrue(result.isSuccess)
    }
}
```

### Security tests (`src/androidTest/java/security/`)

```kotlin
@Test
fun `keys should not be extractable from KeyStore`() {
    val keyStore = KeyStore.getInstance("AndroidKeyStore")
    keyStore.load(null)
    val key = keyStore.getKey("test_alias", null)

    // Las claves en KeyStore no son extractables
    assertThrows<KeyStoreException> { key.encoded }
}
```

---

## Constraints específicos del proyecto

### Dispositivo físico es OBLIGATORIO para:
- **StrongBox testing:** emuladores no lo soportan. Verificar fallback a TEE requiere hardware real.
- **BiometricPrompt:** algunos escenarios no son simulables en emulador.
- **Variabilidad hardware:** ADR-004 obliga a probar en devices con StrongBox (Pixel 3+, Samsung S20+) Y sin él (TEE puro).

### Robolectric tiene limitaciones con AndroidKeyStore
- Algunos `KeyGenParameterSpec` flags no se comportan igual.
- Para tests críticos de crypto, preferir `androidTest` en device sobre Robolectric.

### Coroutines testing
- Usar `runTest` (no `runBlocking`).
- Para componentes con `Dispatchers.IO`/`Main`: inyectar dispatcher o usar `Dispatchers.setMain()` en setup.

---

## Checklist pre-commit (security tests)

De `DEVELOPMENT.md`:
- [ ] No hay claves hardcodeadas
- [ ] No hay logs de información sensible
- [ ] Cifrado aplicado a datos en storage
- [ ] Input validation en boundary points
- [ ] Error messages no revelan info de sistema

---

## Casos críticos a cubrir (cuando se implementen tests)

### Crypto / KeyStoreManager (ADR-004)
- Generación de clave con StrongBox disponible
- Fallback a TEE cuando `StrongBoxUnavailableException`
- `KeyPermanentlyInvalidatedException` tras cambio de biometría → invalidación correcta
- IV único en cada cifrado (no reutilización)
- `requireStrongBox = true` falla en device sin StrongBox

### EnrollmentManager (ADR-006)
- Flujo completo de 7 pasos emite todos los estados
- **Rollback por cada paso:** verificar que falla en paso N ejecuta el rollback correcto (tabla en ADR-006)
- Password aleatoria generada tiene 32 chars y entropía suficiente
- Cancelación del Flow no deja estados parciales

### SplashScreen / cliente (ADR-009 — bug histórico)
- Cold start NO debe entrar a Home aunque `authState` haya quedado en memoria
- Verificar que la navegación siempre fuerza biometría tras reapertura

### BiometricAuthenticator (ADR-007)
- `ClassCastException` si la Activity no es `FragmentActivity`
- Cancelación por usuario reportada correctamente

### Logging (ADR-005, cuando se implemente `PasskeyAuthLogger`)
- Nivel `NONE` no produce output
- Test estático: ningún `Log.*` contiene email completo, token completo, o deviceId sin ofuscar
- ProGuard rules eliminan `v()`/`d()`/`i()` en release
