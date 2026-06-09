# ADR-011: Testing Stack and Infrastructure

**Estado:** Aceptado
**Fecha:** 2026-05-31
**Autores:** Francisco Javier Marmolejo López

---

## Contexto

Con las 3 interfaces de ADR-010 extraidas, `EnrollmentManager` y los componentes
del SDK son 100% testeables en JVM. Antes de escribir tests, necesitamos elegir
el stack y configurar la infraestructura del modulo `passkeyauth-core`.

Las decisiones tomadas aqui afectan a todos los tests futuros del SDK, por lo
que conviene documentarlas explicitamente.

---

## Decisiones

### 1. Test runner: **JUnit 4**, NO JUnit 5

**Eleccion:** JUnit 4.13.2 (ya en version catalog).

**Por que NO JUnit 5:**
- AGP integration con JUnit 5 requiere plugin externo (`de.mannodermaus.android-junit5`)
  o `useJUnitPlatform()` con configuracion extra
- Compose UI tests y `androidx.test` aun dependen de JUnit 4 internamente
- El valor de las features JUnit 5 (parameterized tests modernos, extensiones) es
  marginal para un SDK Android cuya superficie de test es relativamente simple
- Riesgo de incompatibilidades con futuras versiones de AGP

**Como afecta el codigo:** se usa `@Test` de `org.junit.Test`, `@get:Rule` para reglas,
`@Before` / `@After`. Patrones estandar.

### 2. Mocking: **MockK** con inline mock maker (no Mockito)

**Eleccion:** MockK 1.13.13 (ya en version catalog).

**Por que NO Mockito:**
- Kotlin classes son `final` por defecto → Mockito requiere `mockito-inline` y `mock-maker-inline`
- Sealed classes y suspend functions tienen soporte limitado en Mockito
- MockK es Kotlin-first: `coEvery`, `coVerify`, `every`, `mockkClass` son idiomatico

**Cuando usar MockK vs fakes manuales:**
- **Preferir fakes** (en `src/test/java/.../fakes/`) para colaboradores que usaremos
  en muchos tests con escenarios variados. Coste: una clase nueva. Beneficio: tests
  legibles, deterministas, sin verbosidad de `coEvery`.
- **Usar MockK** para colaboradores que aparecen solo en uno o dos tests, o para
  clases sin interfaz extraida (ej. `SecureStorage`).

**Fakes ya creados** (commit del ADR-011, en `core/fakes/`):
- `FakeBiometricAuthenticator` — devuelve `Cipher` software-backed reales por defecto.
- `FakeKeyStoreManager` — simula generacion/eliminacion de claves AES en memoria.
- `FakeAuthBackend` — mantiene `currentUser` in-memory, captura credenciales.
- `FakePasswordManagementBackend` — para el paso 2 del enrollment (cuando se descomente).
- `InMemoryDeviceRegistry` — Map<userId, DeviceRecord> con soft delete (`isActive`).

### 3. Aserciones: **Truth** (Google), NO Kotest assertions ni JUnit nativo

**Eleccion:** com.google.truth:truth 1.4.4.

**Por que NO JUnit nativo:** `assertEquals(expected, actual)` es ilegible y
no soporta failures descriptivas para colecciones, mapas, nullables.

**Por que NO Kotest assertions:**
- Kotest assertions son excelentes (`shouldBe`, `shouldContain`) pero pulling
  Kotest implica el riesgo de adoptar tambien Kotest runner — confunde el stack.
- Truth tiene la sintaxis suficientemente legible: `assertThat(actual).isEqualTo(expected)`.

**Por que SI Truth:**
- Google-blessed (usada en Android y Compose internamente)
- API estable, sin sorpresas
- Failures descriptivas: `assertThat(list).containsExactly(a, b, c).inOrder()`
- Compatible con JUnit 4

### 4. Flows: **Turbine**

**Eleccion:** app.cash.turbine 1.2.0.

Para testear `EnrollmentManager.enrollDevice()` que emite `Flow<EnrollmentState>`,
la API basica de Kotlin Flow es verbose y propensa a errores. Turbine ofrece:

```kotlin
enrollmentManager.enrollDevice(email, tempPassword).test {
    assertThat(awaitItem()).isEqualTo(EnrollmentState.Idle)
    assertThat(awaitItem()).isInstanceOf(EnrollmentState.ValidatingCredentials::class.java)
    // ...
    awaitComplete()
}
```

Sin Turbine, el equivalente con `.toList()` no permite verificacion incremental ni
deteccion de emisiones no esperadas.

### 5. Coroutines testing: **kotlinx-coroutines-test** con `StandardTestDispatcher`

**Eleccion:** 1.9.0 (ya en version catalog).

**Reglas:**
- Usar `runTest { ... }`, NUNCA `runBlocking`
- Sustituir `Dispatchers.Main` con `MainDispatcherRule` (creada en `core/support/`)
- Preferir `StandardTestDispatcher` sobre `UnconfinedTestDispatcher` — fuerza tests
  a manejar correctamente la concurrencia (`advanceUntilIdle()`, `runCurrent()`)

### 6. Robolectric: **SI**, pero acotado

**Eleccion:** Robolectric 4.14.

**Cuando SI usar:**
- `SecureStorage` (DataStore funciona bien en Robolectric)
- Componentes que necesitan `Context` pero NO hardware especifico

**Cuando NO usar (ya tratado en ADR-004):**
- `KeyStoreManager` (Robolectric usa BouncyCastle → tests verdes que mienten)
- `BiometricAuthenticator` (no simulable)
- `CryptoProvider` real (no aporta sobre JVM puro)

**Bundle separado:** `libs.bundles.testing.robolectric` incluye Robolectric + JUnit +
Truth + coroutines-test + MockK. Los tests Robolectric usan `@RunWith(RobolectricTestRunner::class)`.

### 7. Coverage: **JaCoCo 0.8.12** con exclusiones agresivas

**Eleccion:** JaCoCo 0.8.12 con tarea custom `jacocoTestReport`.

**Por que JaCoCo y no Kover (de JetBrains):**
- JaCoCo tiene mejor integracion con AGP 9 (ver punto sobre `.exec` path mas abajo)
- Kover esta evolucionando pero su soporte de exclusion patterns es menos maduro
- JaCoCo es el estandar de la industria — reportes interpretables por SonarQube,
  Codecov, etc.

**Exclusiones aplicadas en `build.gradle.kts`:**
- `**/models/**` — data classes, no contienen logica
- `**/errors/**` — excepciones, son wrappers de mensajes
- `**/fakes/**` — codigo de test
- `**/BuildConfig.*`, `**/R.class`, `**/R$*.class` — generado
- `**/*\$Companion.*` — factories (probadas via los consumidores)
- `**/*Test*.*` — codigo de test
- `android/**/*.*` — framework

**Filosofia:** medir cobertura del codigo que tiene logica, no del que solo declara estructura.

**Compatibilidad con AGP 9:** el path del `.exec` cambio en AGP 9.0 a
`outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec`. El task
incluye ambos paths por compatibilidad.

**Como ejecutar:**
```bash
./gradlew passkeyauth-core:jacocoTestReport

# Reportes en:
# - passkeyauth-core/build/reports/jacoco/jacocoTestReport/html/index.html
# - passkeyauth-core/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml
```

### 8. Configuracion de `testOptions` en `build.gradle.kts`

```kotlin
testOptions {
    unitTests.isIncludeAndroidResources = true
    unitTests.isReturnDefaultValues = true
}
```

**`isReturnDefaultValues = true`** evita que clases del framework Android lancen
`RuntimeException("Stub!")` en tests JVM puros — devuelven defaults razonables
en su lugar. Necesario para casos donde un import accidental de `android.util.Log`
o similar atraviesa el codigo bajo test.

**`isIncludeAndroidResources = true`** habilita acceso a recursos en tests (necesario
para Robolectric).

### 9. JUnit 5 deferred a futuro

Si AGP simplifica la integracion con JUnit 5 en versiones futuras, evaluar migracion.
No prioritario.

---

## Estructura de directorios

```
passkeyauth-core/
├── src/
│   ├── main/java/es/fjmarlop/corpsecauth/
│   │   └── (codigo de produccion — sin cambios)
│   ├── test/java/es/fjmarlop/corpsecauth/core/
│   │   ├── fakes/
│   │   │   ├── FakeBiometricAuthenticator.kt
│   │   │   ├── FakeKeyStoreManager.kt
│   │   │   ├── FakeAuthBackend.kt
│   │   │   ├── FakePasswordManagementBackend.kt
│   │   │   ├── InMemoryDeviceRegistry.kt
│   │   │   └── FakesSmokeTest.kt  (canary)
│   │   ├── support/
│   │   │   └── MainDispatcherRule.kt
│   │   └── (tests por modulo: enrollment/, crypto/, firebase/, etc.)
│   └── androidTest/java/es/fjmarlop/corpsecauth/core/
│       └── (tests instrumented — solo crypto y biometric en device fisico)
```

---

## Comandos clave (resumen para el equipo)

```bash
# Tests JVM rapidos (lo que se corre el 90% del tiempo)
./gradlew passkeyauth-core:testDebugUnitTest

# Tests instrumented en device fisico
./gradlew passkeyauth-core:connectedAndroidTest

# Reporte de cobertura
./gradlew passkeyauth-core:jacocoTestReport

# Compilar tests sin ejecutar (validacion rapida)
./gradlew passkeyauth-core:compileDebugUnitTestKotlin
```

---

## Restricciones descubiertas (importantes para escribir tests)

### 1. `android.os.Build.*` es `null` en JVM puro

`Build.MODEL`, `Build.MANUFACTURER`, `Build.VERSION.RELEASE` son `null` en tests JVM.
Si un modelo tiene defaults que los referencian (ej. `DeviceInfo`), los tests deben
**pasar valores explicitos** para evitar `NullPointerException`.

**Ejemplo del problema:** descubierto en el smoke test inicial al fakear `DeviceInfo`.
El fake `InMemoryDeviceRegistry` pasa ahora valores explicitos:
```kotlin
DeviceInfo(
    deviceId = deviceId,
    model = "fake-model",
    manufacturer = "fake-manufacturer",
    osVersion = "fake-os",
    appVersion = "test",
    registeredAt = 0L
)
```

### 2. `Cipher` en JVM puro funciona con `SunJCE` provider

`Cipher.getInstance("AES/GCM/NoPadding")` funciona perfectamente en JVM puro con
una `SecretKey` generada por `KeyGenerator.getInstance("AES")`. Los fakes
`FakeBiometricAuthenticator` y `FakeKeyStoreManager` aprovechan esto para devolver
Ciphers reales que el codigo bajo test puede usar con `.doFinal()` y `.iv`.

Esto **no** valida que el SDK funcione con AndroidKeyStore real — eso es lo que
cubren los instrumented tests.

### 3. Firebase NO se debe mockear con MockK

`Task<T>`, listeners y generics anidados hacen los mocks de Firebase frustrantes y
no representativos. Para tests reales de `FirebaseAuthBackend` y `FirestoreDeviceRegistry`
usar **Firebase emulator suite** (siguiente fase, fuera del scope de este ADR).

Para tests del `EnrollmentManager` u otros consumidores, **usar el `FakeAuthBackend`
y el `InMemoryDeviceRegistry`** — son determinsticos y rapidos.

---

## Consecuencias

### Positivas

- ✅ Infraestructura de test funcionando: smoke test verde (5 tests, < 3s).
- ✅ JaCoCo generando reportes HTML + XML con exclusiones sensatas.
- ✅ Fakes reutilizables para todos los tests futuros del `EnrollmentManager` y consumidores.
- ✅ Stack estandar (JUnit 4 + MockK + Truth + Turbine) — onboarding rapido para devs Android.
- ✅ Separacion clara entre JVM/Robolectric/instrumented documentada.

### Negativas

- ⚠️ Truth como dependencia adicional (~150 KB) — aceptable, es dependencia de test.
- ⚠️ Robolectric es pesado (~50 MB en cache) — solo se usa en bundle separado.
- ⚠️ `Build.*` null en JVM es una restriccion implicita — documentada para no tropezar.

### Neutral

- ⚪ JaCoCo task se ejecuta solo cuando se pide explicitamente — no penaliza builds normales.
- ⚪ Bundle `testing-robolectric` opcional — solo se aplica cuando un test lo necesita.

---

## Plan inmediato siguiente

Con la infra lista, escribir los **3 tests "plantilla de oro"** establecidos en ADR-010:

1. **Happy path de `EnrollmentManager.enrollDevice()`** (JVM, ~30 lineas)
   - Inyecta los 5 fakes + 1 mock de `SecureStorage` con MockK
   - Verifica progresion de estados con Turbine
   - Verifica contadores de los fakes (1 generateKey, 1 encryption, 1 bindDevice, etc.)

2. **Rollback del paso 5 del enrollment** (JVM, ~30 lineas)
   - Configura `cipher.doFinal()` para lanzar (via fake del biometric)
   - Verifica que se llama `deleteKey()` y `signOut()`
   - Verifica que NO se llama `bindDevice()` (no se llego a paso 6)
   - Verifica estado final = `EnrollmentState.Error`

3. **Test instrumented de `AndroidKeyStoreManager` con StrongBox real** (instrumented)
   - Tag `@Tag("strongbox")` para correr solo en device con feature
   - Genera clave, verifica que `setIsStrongBoxBacked(true)` no lanza
   - Mata el proceso, recupera la clave, verifica persistencia

Estos 3 tests sirven de plantilla para todos los demas.

---

## Referencias

- ADR-010 — Internal Abstractions for Testability and Backend Independence
- ADR-004 — KeyStoreManager AES-GCM (porque Robolectric no sirve para crypto)
- `kotlinx-coroutines-test` documentation
- Turbine README — https://github.com/cashapp/turbine
- Truth documentation — https://truth.dev/
- JaCoCo manual — https://www.jacoco.org/jacoco/trunk/doc/

---

## Revisiones

- **2026-05-31:** Creacion inicial. Stack configurado, fakes creados, smoke test verde,
  JaCoCo verde. Lista la infra para escribir los 3 tests "plantilla de oro".
