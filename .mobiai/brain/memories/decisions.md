# Decisions

<!--
Architecture decisions specific to this project.
Append entries with: mobiai brain save decision (coming in Phase 2).
Each entry should record: title, status (active|deprecated), platform,
area, date, decision, reason, files.
-->

## Resumen del SDK

Librería Android Kotlin enterprise para autenticación passwordless con biometría hardware-backed (StrongBox→TEE) y Firebase como backend. Modelo "1 user = 1 device".

**Flujo del SDK:**
1. **Enrollment**: email + password temporal → biometría → device binding en Firestore
2. **Login**: solo biometría
3. **Session**: timeout configurable, invalidación automática

---

## ADR-001: Estructura Multi-Módulo

**Fecha:** 2026-01-17 · **Estado:** Aceptado

**Contexto:** Librería Android con lógica separada de UI.

**Decisión:** 3 módulos — `passkeyauth-core`, `passkeyauth-ui`, `sample`.

**Consecuencias:**
- ✅ Consumidores eligen core solo o core+ui
- ✅ Testing simplificado
- ⚠️ Mayor complejidad inicial

---

## ADR-002: Result<T> para Manejo de Errores

**Fecha:** 2026-01-17 · **Estado:** Aceptado

**Contexto:** Mecanismo consistente para errores en operaciones asíncronas.

**Opciones consideradas:** Exceptions tradicionales, Kotlin `Result<T>`, sealed classes custom, Arrow `Either<L,R>`.

**Decisión:** Usar `Result<T>` de Kotlin stdlib con excepciones custom.

**Justificación:**
- Idiomático en Kotlin moderno
- Integración natural con suspend functions
- Sin dependencias adicionales
- Fuerza manejo explícito de errores

**Ejemplo:**
```kotlin
passkeyAuth.authenticate()
    .onSuccess { token -> /* ... */ }
    .onFailure { error -> /* ... */ }
```

**Consecuencias:**
- ✅ API clara: `suspend fun authenticate(): Result<AuthToken>`
- ✅ Testing simplificado con `Result.success()` / `Result.failure()`
- ⚠️ Clientes deben conocer `Result<T>`

---

## ADR-003: Suspend Functions sobre Callbacks

**Fecha:** 2026-01-17 · **Estado:** Aceptado

**Contexto:** BiometricPrompt y Firebase usan callbacks; debemos decidir API pública.

**Decisión:** Suspend functions en toda la API pública, wrapeando callbacks internamente con `suspendCancellableCoroutine`.

**Justificación:**
- Compose usa coroutines nativamente
- Evita callback hell
- Cancellation automática con Job
- Testing más simple

**Consecuencias:**
- ✅ API moderna, integración directa con ViewModels
- ⚠️ Clientes DEBEN usar coroutines

---

## ADR-004: KeyStoreManager con AES-GCM y StrongBox Optional

**Fecha:** 2026-01-18 · **Estado:** Aceptado

**Contexto:** Almacenar datos sensibles (tokens, credenciales). Decisiones sobre algoritmo, hardware security, key lifecycle, auth timeout.

**Factores:** OWASP MASVS L2, NIST SP 800-63B, soporte API 26+, UX, requisitos enterprise.

**Opciones consideradas:**
1. AES-CBC con HMAC — rechazado (más código, no recomendado por NIST)
2. AES-GCM sin StrongBox — rechazado (no aprovecha hardware moderno)
3. AES-GCM con StrongBox mandatory — rechazado (excluye ~70% dispositivos)
4. **AES-GCM con StrongBox opcional (elegida)** — balance seguridad/compatibilidad

**Decisión:**
```kotlin
internal class KeyStoreManager(
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
    private val authTimeoutSeconds: Int = 0,
    private val requireStrongBox: Boolean = false
)
```
- AES-256-GCM (no CBC)
- `setIsStrongBoxBacked(true)` con try-catch fallback a TEE
- `setInvalidatedByBiometricEnrollment(true)`
- `setUserAuthenticationRequired(true)`

**Factory methods:** `createDefault()`, `createWithTimeout(300)`, `createWithStrongBox()`.

**Justificación clave:**
- **AES-GCM:** NIST SP 800-38D, AEAD (cifra + autentica en una operación), OWASP MASVS L2 6.2.6
- **StrongBox optional:** ~30% dispositivos tienen StrongBox, ~95% tienen TEE → fallback maximiza cobertura
- **Invalidación automática:** previene que atacante con dispositivo robado descifre datos históricos tras registrar nueva huella
- **Auth timeout = 0 por defecto:** cada operación requiere huella

**Descartado:** ChaCha20-Poly1305 (no en KeyStore hasta API 28), RSA+AES híbrido (over-engineering), EncryptedSharedPreferences (no integra con BiometricPrompt).

**Consecuencias:**
- ✅ Cumple OWASP MASVS L2 y NIST SP 800-63B
- ✅ Claves hardware-backed, no exportables
- ✅ Detección de manipulación por GCM
- ❌ Testing complejo (necesita devices con/sin StrongBox)
- ❌ IV debe guardarse con ciphertext (12 bytes para GCM)

**Estructura:**
```kotlin
data class EncryptedData(val ciphertext: ByteArray, val iv: ByteArray)
// Storage: Base64(iv + ciphertext)
```

Ante `KeyPermanentlyInvalidatedException`: invalidar sesión local, forzar re-enrollment, notificar al usuario.

---

## ADR-005: Sistema de Logging Configurable

**Fecha:** 2026-01-18 · **Estado:** Aceptado

**Contexto:** El SDK usa `println()` para debugging. Problemas: expone info sensible en logcat, no se puede deshabilitar sin recompilar, no cumple auditoría enterprise.

**Decisión:** Implementar `PasskeyAuthLogger` interno con niveles configurables.

```kotlin
internal object PasskeyAuthLogger {
    enum class Level { NONE, ERROR, WARNING, INFO, DEBUG, VERBOSE }
    var level: Level = if (BuildConfig.DEBUG) Level.VERBOSE else Level.NONE
    // v(), d(), i(), w(), e()
}
```

**Por qué no Timber:** dependencia externa (+50KB), no esencial. Clientes pueden reenviar vía `setLogListener`.

**Niveles:**
- `NONE`: producción por defecto
- `ERROR`: producción con telemetría
- `WARNING`: staging/QA
- `INFO`: QA con debugging básico
- `DEBUG`: desarrollo local
- `VERBOSE`: debugging profundo (NUNCA en prod)

**Reglas críticas:**
- ❌ PROHIBIDO loguear: email, tokens completos, deviceIds sin ofuscar, claves
- ✅ PERMITIDO con ofuscación: email truncado, token prefix, device hash

**ProGuard en release:**
```proguard
-assumenosideeffects class ...PasskeyAuthLogger {
    public static *** v(...); *** d(...); *** i(...);
}
```

**Plan:** v0.1-0.2 println() temporal → v0.3 migrar a Logger → v1.0 auditoría completa + ProGuard activado.

**Consecuencias:**
- ✅ Cumple OWASP MASVS-STORAGE-2
- ✅ Performance óptimo (logs removidos por ProGuard)
- ❌ Migración de todos los `println()` pendiente

---

## ADR-006: EnrollmentManager Transaccional con Passwordless Real

**Fecha:** 2026-01-23 · **Estado:** Actualizado (v0.2.0)

**Contexto:** El enrollment debe ser atómico, seguro, passwordless real, y con UX progresiva. Cambio arquitectónico: eliminar contraseña elegida por el usuario.

**Decisión:** `EnrollmentManager` como orquestador transaccional que emite estados vía `Flow<EnrollmentState>`, con **invalidación automática** de password temporal a una password aleatoria de 32 chars.

**API:**
```kotlin
fun enrollDevice(email: String, temporaryPassword: String): Flow<EnrollmentState>
// newPassword ELIMINADO
```

**Flujo de 7 pasos:**
1. Validar credenciales temporales con Firebase
2. Invalidar password temporal → password aleatoria 32 chars (usuario NO conoce)
3. Generar clave en KeyStore (StrongBox/TEE)
4. Autenticar biométricamente
5. Cifrar token con AES-GCM
6. Vincular device en Firestore
7. Guardar en storage local cifrado

**Estados:**
```kotlin
sealed class EnrollmentState {
    object Idle
    data class ValidatingCredentials(val email: String)
    data class RequiresPasswordChange(val isTemporaryPassword: Boolean)
    object GeneratingCryptoKey
    data class AwaitingBiometric(val config: BiometricConfig)
    object BindingDevice
    data class Success(val user: AuthUser)
    data class Error(val exception: PasskeyAuthException)
}
```

**Rollback automático por paso:**
| Paso | Falla | Rollback |
|------|-------|----------|
| 1 | Validación | Ninguno |
| 2 | Invalidar password | Sign out Firebase |
| 3 | Generar clave | Sign out Firebase |
| 4 | Biometría | Delete key + Sign out |
| 5 | Cifrado | Delete key + Sign out |
| 6 | Device binding | Delete key + Clear storage + Sign out |
| 7 | Storage | Delete key + Revoke device + Sign out |

**Passwordless real vs anterior:**
- ❌ Antes: usuario elegía nueva password (la CONOCE → puede usar manualmente)
- ✅ Ahora: sistema genera password aleatoria 32 chars con `SecureRandom`, ~200 bits entropía, usuario NUNCA la conoce

**Alternativas descartadas:** APIs individuales (rollback manual), callbacks (callback hell), mantener `newPassword` (no es passwordless real).

**Breaking change v0.1.0 → v0.2.0:** eliminado parámetro `newPassword`. UI pasa de 4 a 2 campos. Recovery vía IT (reset de temporal).

**Consecuencias:**
- ✅ Atomicidad garantizada, UX rica, cancelable, Compose-friendly
- ✅ Passwordless real, password 32 chars aleatorios
- ⚠️ Breaking change en API
- ⚠️ Password solo existe en Firebase Auth, nunca local

---

## ADR-007: Requerimiento de FragmentActivity

**Fecha:** 2026-01-25 · **Estado:** Aceptado

**Contexto:** `BiometricPrompt` usa Fragment transactions internamente → Activity debe ser `FragmentActivity`.

**Decisión:** La Activity que llame `enrollDevice()` o `authenticate()` **debe extender `FragmentActivity`**.

**Alternativas descartadas:**
1. Wrapper interno con Fragment → lifecycle frágil, memory leaks
2. Soportar `ComponentActivity` con reflection → técnicamente imposible sin hacks
3. **Documentar requisito (elegida)** → simple, claro, sin hacks

**Para Compose apps:**
```kotlin
// Cambiar
class MainActivity : ComponentActivity()
// Por
class MainActivity : FragmentActivity()
```
No hay pérdida de funcionalidad. `AppCompatActivity` también funciona (extiende `FragmentActivity`).

**Si no se cumple:** `ClassCastException` inmediato y claro en runtime.

**Mitigaciones:** README con warning destacado, sample app como template, KDoc explícito.

**Consecuencias:**
- ✅ BiometricPrompt funciona sin workarounds
- ✅ Compatible API 14+
- ⚠️ No funciona directo con `ComponentActivity`

---

## ADR-008: Separación Core/UI — Eliminación de SystemState

**Fecha:** 2026-01-26 · **Estado:** Aceptado

**Contexto:** En iteraciones previas existía `SystemState` (sealed class) para que el SDK dictara navegación de UI. Esto violaba separación de responsabilidades.

**Decisión:** **Eliminar `SystemState` del core.**

**El SDK provee:**
- ✅ Estado observable: `authState: StateFlow<AuthResult>`
- ✅ Queries simples: `isDeviceEnrolled()`, `isAuthenticated()`
- ✅ Acciones: `enrollDevice()`, `authenticate()`, `logout()`

**El SDK NO:**
- ❌ Dicta navegación
- ❌ Impone estructura de UI
- ❌ Toma decisiones de presentación

**Migración v0.1.x → v0.2.0+:**
```kotlin
// Antes
when (val state = PasskeyAuth.getSystemState()) {
    is SystemState.RequiresEnrollment -> ...
}

// Después
when {
    !isEnrolled -> navigate(Enrollment)
    !isAuthenticated -> navigate(Login)
    else -> navigate(Home)
}
```

**Alternativas descartadas:** mantener `SystemState` opcional (sigue siendo tentación), módulo `passkeyauth-navigation` (overengineering).

**Consecuencias:**
- ✅ Separación clara: Core = Estado+Acciones, Cliente = Navegación+UI
- ✅ Clientes libres de usar Jetpack Navigation, Compose Navigation, state machines custom
- ⚠️ Breaking change para early adopters

---

## ADR-011: Testing Stack and Infrastructure

**Fecha:** 2026-05-31 · **Estado:** Aceptado

**Contexto:** Con ADR-010 completo, las interfaces existen y el `EnrollmentManager` es 100% testeable en JVM. Falta configurar el stack y la infraestructura antes de escribir tests reales.

**Stack elegido:**

| Pieza | Eleccion | Por que NO la alternativa |
|---|---|---|
| Runner | **JUnit 4.13.2** | JUnit 5 → AGP integration compleja, valor marginal |
| Mocking | **MockK 1.13.13** | Mockito → final classes problematicas, sealed mal soportadas |
| Aserciones | **Truth 1.4.4** (Google) | Kotest assertions → riesgo de pulling Kotest runner |
| Flows | **Turbine 1.2.0** | Manual `.toList()` → no permite verificacion incremental |
| Coroutines | **kotlinx-coroutines-test 1.9.0** con `StandardTestDispatcher` | Unconfined oculta bugs de concurrencia |
| Robolectric | **4.14** SOLO para `SecureStorage`/DataStore | NO para crypto/biometric (ADR-004: tests verdes que mienten) |
| Coverage | **JaCoCo 0.8.12** con exclusiones agresivas | Kover → menos maduro en patterns, peor integracion con SonarQube |

**Patron mocking — cuando usar fake vs MockK:**
- **Fake** si el colaborador aparece en muchos tests con escenarios variados (los 5 ya creados: `FakeBiometricAuthenticator`, `FakeKeyStoreManager`, `FakeAuthBackend`, `FakePasswordManagementBackend`, `InMemoryDeviceRegistry`).
- **MockK** si aparece solo en 1-2 tests, o si no tiene interfaz extraida (caso de `SecureStorage`).

**Estructura creada:**
- `passkeyauth-core/src/test/java/.../fakes/` — los 5 fakes + smoke test canary
- `passkeyauth-core/src/test/java/.../support/` — `MainDispatcherRule` (sustituye `Dispatchers.Main` en tests)
- `passkeyauth-core/build.gradle.kts` — JaCoCo plugin + tarea `jacocoTestReport` con exclusiones (data classes, errors, fakes, generated)

**Restricciones descubiertas (no obvias):**
1. **`android.os.Build.*` es `null` en JVM puro** — al fakear `DeviceInfo` hay que pasar `model`, `manufacturer`, `osVersion` explicitos. Detectado en smoke test inicial.
2. **`Cipher` de Java funciona en JVM puro** con `KeyGenerator.getInstance("AES")` — los fakes de biometric/keystore aprovechan esto para devolver Ciphers reales (no mocks).
3. **JaCoCo path del `.exec` cambio en AGP 9** — ahora en `outputs/unit_test_code_coverage/debugUnitTest/`. El task lo cubre con doble include.
4. **Firebase NO se debe mockear con MockK** — usar `FakeAuthBackend` o Firebase emulator suite (fase siguiente).

**Verificacion:**
- ✅ Smoke test (5 tests del FakesSmokeTest) — verde en 3s
- ✅ JaCoCo report HTML + XML generandose correctamente

**Bundles del version catalog:**
- `testing-unit` — JVM puro (JUnit + MockK + Turbine + Truth + coroutines-test)
- `testing-robolectric` — NUEVO bundle (idem + Robolectric)
- `testing-android` — instrumented (AndroidX test + mockk-android + Truth)

**Comandos clave:**
```bash
./gradlew passkeyauth-core:testDebugUnitTest          # tests JVM rapidos
./gradlew passkeyauth-core:jacocoTestReport           # cobertura
./gradlew passkeyauth-core:connectedAndroidTest       # instrumented en device
./gradlew passkeyauth-core:compileDebugUnitTestKotlin # validacion sin ejecutar
```

**Estado:** Infra lista + **3 plantillas de oro completadas (happy path + rollback × 5 + instrumented StrongBox)**. 26 ejecuciones verdes entre JVM + dos devices físicos. **Matriz ADR-004 100% validada con hardware real** (Device A con StrongBox y Device B sin StrongBox cubren todos los paths). Ver `testing.md` para matriz completa.

**Cleanup posterior (cleanup deuda técnica detectada por el primer test):**

- ✅ **Migración `android.util.Base64` → `java.util.Base64`** en `EnrollmentManager.kt` (paso 5) y `EncryptedData.kt` (`toBase64String` / `fromBase64String`).
  - **Por qué:** `android.util.Base64` retorna `null` en JVM puro (no implementado en stub) → forzaba `mockkStatic` en tests JVM.
  - **`java.util.Base64`** está disponible desde Java 8 / API 26 (= nuestro minSdk). Cero regresión.
  - **Equivalencias aplicadas:** `Base64.encodeToString(bytes, NO_WRAP)` → `Base64.getEncoder().encodeToString(bytes)` (NO_WRAP es el default en `getEncoder()`). `Base64.decode(str, NO_WRAP)` → `Base64.getDecoder().decode(str)`.
  - **Test simplificado:** eliminado `mockkStatic`, `unmockkStatic`, `every`, `@After tearDown` del `EnrollmentManagerHappyPathTest`. Suite verde (6/6) + sample app compila.
  - Documentación: comentario explícito en `EncryptedData.kt` y `EnrollmentManager.kt` (paso 5) referenciando ADR-011 para que futuros devs entiendan por qué `java.util.Base64` y no `android.util.Base64`.

---

## ADR-010: Internal Abstractions for Testability and Backend Independence

**Fecha:** 2026-05-31 · **Estado:** Aceptado

**Contexto:** El SDK depende de tres fronteras no virtualizables — AndroidKeyStore/StrongBox, BiometricPrompt y Firebase. `EnrollmentManager` (componente más crítico del SDK, 7 pasos con rollback) no puede testearse en JVM porque depende directamente de implementaciones concretas. Esto bloquea la estrategia de testing (memoria `testing.md`).

**Decisión:** Extraer **3 interfaces `internal`** que abstraen las fronteras de plataforma:

1. **`BiometricAuthenticator`** → impl `AndroidBiometricAuthenticator` + `FakeBiometricAuthenticator` (test)
2. **`CryptoKeyProvider`** → impl `AndroidKeyStoreCryptoProvider` + `FakeCryptoKeyProvider` (test)
3. **`AuthBackend` + `DeviceRegistry`** → impls Firebase/Firestore + fakes in-memory (test)

**Ubicación:** cada interfaz junto a su implementación en el paquete actual. NO crear paquete `contracts/`.

**Construcción:** `PasskeyAuth` facade sigue construyendo implementaciones reales. Cliente del SDK no nota cambio.

**Justificación clave:**
- Interfaces > mockear clases concretas porque permiten **fakes deterministicos reutilizables** para tests de seguridad (verificar rollback, contar invocaciones de `deleteKey()`).
- Documentan el contrato de plataforma explícitamente.
- Habilitan **backend independence** (Keycloak, Auth0) a coste cero futuro.
- `internal` (no `public`) → no exponer detalle de implementación al cliente del SDK.

**Regla aplicada:** extraer interfaz solo si existirá test double real o segunda implementación. Por eso NO se abstraen `EnrollmentManager`, `CryptoProvider`, `SecureStorage`, `PasskeyAuth`.

**Patrón:** companion factory en la interfaz para preservar `BiometricAuthenticator.create(activity)` sin cambios en consumidores:
```kotlin
internal interface BiometricAuthenticator {
    // ...
    companion object {
        fun create(activity: FragmentActivity): BiometricAuthenticator =
            AndroidBiometricAuthenticator(activity)
    }
}
```

**Plan de implementación:** 3 commits incrementales (interfaz 1 más simple primero, Firebase última por ser más invasiva). Tras los 3, escribir 3 tests "plantilla de oro" que sirvan de referencia para todos los demás.

**Estado actual de ejecución:**
- ✅ Interfaz 1 (`BiometricAuthenticator`) — extraída, compila OK
- ✅ Interfaz 2 (`KeyStoreManager`) — extraída, compila OK. Renombrada desde `CryptoKeyProvider` por consistencia de naming. Warnings de `setUserAuthenticationValidityDurationSeconds` silenciadas con `@Suppress("DEPRECATION")` (deprecado en API 30 pero necesario para minSdk 26).
- ✅ Interfaz 3 (**Path C**: `AuthBackend` + `PasswordManagementBackend` + `DeviceRegistry`) — extraída, compila OK (full project build verde, incluido sample app).
  - **Nuevos modelos:** `AuthSession` (con `refreshToken: String?` y `expiresAt: Long?` nullable para futuro OAuth2), `Credentials` (sealed, hoy solo `EmailPassword`).
  - **Implementaciones:** `FirebaseAuthBackend` implementa AMBAS `AuthBackend` y `PasswordManagementBackend` (un solo objeto sirve ambas capabilities en el caso Firebase). `FirestoreDeviceRegistry` implementa `DeviceRegistry`.
  - **Capability pattern:** `invalidateTemporaryPassword` extraída a `PasswordManagementBackend` aparte porque es muy Firebase-shaped (otros backends OAuth2 lo manejan server-side).
  - **EnrollmentManager paso 1 refactor:** `firebaseAuthManager.loginWithTemporaryCredentials(...)` + `firebaseUser.getIdToken(...)` colapsados en un solo `authBackend.authenticate(Credentials.EmailPassword(...))` que devuelve `AuthSession` atómica.
  - Ficheros eliminados: `FirebaseAuthManager.kt`, `DeviceBindingManager.kt`.
  - **Insight clave que valida Path C:** el token se trata como bytes opacos (se cifra, guarda, descifra, NUNCA se usa para API calls). Esto significa que `idToken: String` es suficiente hoy aunque sea Firebase-shaped — cuando llegue OAuth2 los campos nullable de `AuthSession` se rellenan sin breaking change.

**Alternativas descartadas:** Mockear clases abiertas con MockK (verbose, no resuelve backend independence), Robolectric para todo (KeyStore con BouncyCastle miente — ADR-004), Hilt (dependencia pesada innecesaria), refactor en un solo commit (más difícil de revisar/revertir).

**Consecuencias:**
- ✅ `EnrollmentManager` testeable 100% en JVM, feedback < 1s
- ✅ Contrato de plataforma explícito en código
- ✅ Backend independence preparado
- ⚠️ Una capa más de indirección (mitigada manteniendo interfaz+impl en mismo paquete)
- ⚠️ ~3 fakes nuevos en `src/test/` a mantener

---

## ADR-009: Client-Side Security Responsibility

**Fecha:** 2026-01-26 · **Estado:** Aceptado

**Contexto:** ¿Debe el SDK forzar técnicamente comportamientos seguros en la UI del cliente?

**Decisión:** **El SDK NO fuerza checks de seguridad en la UI del cliente.**

**El SDK provee:** herramientas claras, documentación exhaustiva, sample app como ejemplo, warnings opcionales en logs.

**El SDK NO provee:** bloqueos técnicos, validaciones que fuercen navegación, restricciones de flexibilidad.

**Rationale:**
1. **Imposible de implementar:** cliente siempre puede ignorar checks
2. **Casos de uso legítimos:** apps de notas, apps corporativas en intranet
3. **Responsabilidad del cliente:** cada app tiene distinto modelo de amenazas
4. **Precedente de industria:** Firebase Auth, Biometric Library, Keychain no fuerzan uso

**Caso real (bug descubierto):**
```kotlin
// INSEGURO (versión inicial del SplashScreen)
val isAuthenticated = PasskeyAuth.isAuthenticated()
if (isAuthenticated) navigateToHome() // ❌ authState en memoria → bypass

// SEGURO (corregido)
when {
    !isEnrolled -> navigateToEnrollment()
    else -> navigateToLogin()  // ✅ SIEMPRE pide biometría
}
```
**Lección:** Incluso con documentación, errores son posibles → el sample app debe ser impecable.

**Alternativas descartadas:**
1. Forzar checks con `require(isAuthenticated())` → solo cubre APIs del SDK, no navegación
2. Modo strict/permissive → complejidad innecesaria
3. NavGraph pre-hecho → ata cliente a Jetpack Navigation

**Mitigaciones:** SECURITY.md, README destacado, KDoc detallado con ejemplos seguro/inseguro, warnings opcionales en logs.

**Conclusión:** Seguridad es **responsabilidad compartida**.
- SDK: herramientas seguras, docs exhaustivas, sample app perfecta, warnings.
- Cliente: leer docs, implementar correctamente, auditar, seguir mejores prácticas.
