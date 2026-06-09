# ADR-010: Internal Abstractions for Testability and Backend Independence

**Estado:** Aceptado
**Fecha:** 2026-05-31
**Autores:** Francisco Javier Marmolejo López

---

## Contexto

El SDK depende de tres fronteras con sistemas no determinísticos o no virtualizables:

1. **Android KeyStore + StrongBox** (en `KeyStoreManager`) — solo se comporta correctamente en device físico. Robolectric usa una implementación BouncyCastle que pasa tests pero NO reproduce `StrongBoxUnavailableException`, `KeyPermanentlyInvalidatedException`, ni los flags `setUserAuthenticationRequired` / `setInvalidatedByBiometricEnrollment`.
2. **BiometricPrompt** (en `BiometricAuthenticator`) — requiere `FragmentActivity` real y interacción del usuario. No simulable en JVM ni Robolectric.
3. **Firebase Auth + Firestore** (en `FirebaseAuthManager` y `DeviceBindingManager`) — requiere `google-services.json` válido y conexión. Mockear el SDK de Firebase con MockK es frágil (`Task<T>`, listeners, generics anidados).

**Consecuencia hoy:** el componente más crítico del SDK — `EnrollmentManager` y sus 7 pasos con rollback transaccional (ADR-006) — **no puede testearse en JVM** porque depende directamente de implementaciones concretas que requieren hardware, Firebase o emulador.

Esto bloquea la estrategia de testing acordada (ver `.mobiai/brain/memories/testing.md`): JVM unit tests son la base de la pirámide y deben cubrir el orquestador transaccional con feedback loop < 30 segundos.

---

## Decisión

Extraer **tres interfaces `internal`** que sirvan como contratos sobre las fronteras de plataforma:

### 1. `BiometricAuthenticator` (interfaz)
- Implementación: `AndroidBiometricAuthenticator` (renombre de la clase actual).
- Fake JVM: `FakeBiometricAuthenticator` para tests, configurable para devolver `Result.success(Cipher)` o `Result.failure(BiometricException)` según el escenario.

### 2. `KeyStoreManager` (interfaz) — *renombre original propuesto `CryptoKeyProvider`, ver revisión 2026-05-31*
- Implementación: `AndroidKeyStoreManager` (renombre de la clase actual `KeyStoreManager`).
- Fake JVM: `FakeKeyStoreManager` configurable para simular StrongBox disponible/no disponible, clave invalidada, etc.

### 3. `AuthBackend` + `PasswordManagementBackend` + `DeviceRegistry` (tres interfaces — Path C, revision 2026-05-31)
- Implementaciones:
  - `FirebaseAuthBackend` (implementa AMBAS `AuthBackend` y `PasswordManagementBackend`).
  - `FirestoreDeviceRegistry`.
- Modelos asociados nuevos: `AuthSession`, `Credentials` (sealed).
- Fakes JVM: `FakeAuthBackend`, `FakePasswordManagementBackend`, `InMemoryDeviceRegistry`.

**Decision de diseño "Path C" (revision 2026-05-31):**
Tras analisis honesto del codigo se detecto que el SDK trata el token como bytes opacos
(lo cifra, lo guarda, lo descifra, NUNCA lo usa para llamar APIs). Esto permite que el
contrato actual sea adecuado HOY incluso para backends no-Firebase, pero hay asunciones
Firebase-shaped que sera necesario revisar si se anade un backend OAuth2/OIDC real:
- Credenciales asumidas email+password (mitigado: [Credentials] es sealed, extensible)
- Token unico opaco (mitigado: [AuthSession.refreshToken] y [expiresAt] ya previstos como nullable)
- `invalidateTemporaryPassword` es operacion client-side muy especifica de Firebase Auth
  (mitigado: extraida a [PasswordManagementBackend] como capability separada)

Path C = diseño honesto y documentado con limitaciones explicitas, sin sobre-abstraccion
prematura. Cuando llegue un backend real OAuth2/OIDC, los cambios necesarios son acotados
y estan documentados en este ADR.

**Visibilidad:** todas `internal`. No forman parte de la API pública del SDK.

**Ubicación:** cada interfaz junto a su implementación en su paquete actual (`core/auth/`, `core/crypto/`, `core/firebase/`). No crear paquete `contracts/` ni `interfaces/`.

**Construcción:** `PasskeyAuth` (facade) sigue construyendo las implementaciones reales (`Android*`, `Firebase*`). El cliente del SDK no percibe el cambio.

---

## Justificación

### Por qué interfaces y no solo "extender clases abiertas con mocks"

1. **MockK puede mockear clases abiertas, pero el coste cognitivo es real.** Cada test requeriría `mockk<BiometricAuthenticator> { coEvery { ... } returns ... }`. Las interfaces permiten **fakes in-memory** que son código de test deterministico, autodocumentado y reutilizable entre tests. Los fakes encajan mejor con tests de seguridad (verificar estado tras rollback, contar invocaciones de `deleteKey()`, etc.).

2. **Las interfaces documentan el contrato de plataforma.** El equipo lee `BiometricAuthenticator` como interfaz y entiende qué garantías promete el SDK, sin tener que leer 300 líneas de implementación Android.

3. **Habilitan backend independence (caso 3).** Clientes enterprise frecuentemente no quieren Firebase (Keycloak, Auth0, backend propio). Tener `AuthBackend` como interfaz no requiere segundo backend hoy, pero deja el camino abierto a coste cero.

### Por qué SOLO tres interfaces, no una por componente

Regla aplicada: **extrae una interfaz solo si existirá un test double real o segunda implementación.**

Componentes que NO se abstraen porque no aportarían valor:
- `EnrollmentManager` — es el orquestador que estamos intentando testear. Abstraerlo sería ceremonia vacía.
- `CryptoProvider` — wrapper delgado sobre `Cipher` de Java. La lógica que tiene es JVM-portable.
- `SecureStorage` — DataStore funciona en Robolectric.
- `PasskeyAuth` (facade) — singleton de composición; se valida E2E en el sample app.

### Por qué `internal` y no `public`

Las interfaces son **detalle de implementación interna del SDK**. Exponerlas:
- Permitiría a clientes inyectar sus propios `BiometricAuthenticator` (no deseado: rompe garantías de seguridad).
- Crearía deuda de API pública: cualquier cambio sería breaking.

Visibilidad `internal` + tests en el mismo módulo (`src/test/`) acceden a los símbolos sin truco.

---

## Alternativas Consideradas

### 1. Mockear las clases concretas con MockK (sin interfaces)

**Pros:** sin refactor, MockK soporta open classes y final con `mockk-android`.

**Rechazado porque:**
- Requiere abrir todas las clases (`open class`) o configurar MockK con `MockMaker` global — fricción.
- Los mocks son verbose y dispersos por los tests; no se centralizan los escenarios reutilizables.
- No resuelve el problema arquitectónico de cara a backend independence.

### 2. Usar Robolectric para todo

**Pros:** menos refactor.

**Rechazado porque (ya documentado en ADR-004 y memoria de testing):**
- Robolectric con AndroidKeyStore usa BouncyCastle → tests verdes que mienten.
- No simula BiometricPrompt.
- Tiempos de ejecución 5-10x más lentos que JVM puro.

### 3. Inyectar dependencias con Hilt

**Pros:** patrón estándar Android.

**Rechazado porque:**
- DI manual ya funciona (constructor injection).
- Hilt añadiría una dependencia pesada al SDK que los clientes consumirían.
- No es necesario para resolver el problema de testing.

### 4. Hacer las tres extracciones en un solo commit

**Rechazado porque:**
- Refactor de Firebase es invasivo (toca `FirebaseAuthManager`, `DeviceBindingManager`, ambos usados en `EnrollmentManager` y `PasskeyAuth`).
- Commits separados por interfaz son más fáciles de revisar y reverter.
- Cada interfaz desbloquea progresivamente más tests.

---

## Plan de Implementación

Ejecución en **3 commits independientes**:

| # | Interfaz | Coste | Desbloquea |
|---|----------|-------|------------|
| 1 | `BiometricAuthenticator` | Bajo | Mayoría de tests JVM del `EnrollmentManager` |
| 2 | `KeyStoreManager` (renombre, ver revisión) | Medio | Resto de tests JVM del enrollment + login |
| 3 | `AuthBackend` + `PasswordManagementBackend` + `DeviceRegistry` (Path C) | Alto | Tests JVM completos sin Firebase emulator |

Tras los 3, escribir 3 tests ejemplares (plantilla de oro):
- Happy path de `EnrollmentManager.enrollDevice()`
- Rollback del paso 5 (cifrado falla → key se borra, Firebase sign out)
- Instrumented test de `AndroidKeyStoreCryptoProvider` con StrongBox real

---

## Consecuencias

### Positivas

- ✅ `EnrollmentManager` testeable 100% en JVM con feedback < 1s.
- ✅ Cada interfaz es un punto de inserción para fakes específicos de escenarios de seguridad.
- ✅ Backend independence sin coste futuro (cuando llegue Keycloak/Auth0).
- ✅ El contrato de plataforma queda explícito en código.
- ✅ Compatible con la estrategia de testing acordada (memoria `testing.md`).

### Negativas

- ⚠️ Refactor inicial: 3 commits, ~6-8 archivos tocados en total.
- ⚠️ Una capa más de indirección en el código fuente (interfaz + impl). Mitigado por mantenerlas en el mismo paquete.
- ⚠️ Los fakes son código nuevo a mantener (~3 ficheros en `src/test/`).

### Neutral

- ⚪ El consumidor del SDK no nota ningún cambio.
- ⚪ El binario no crece de forma significativa (interfaces son `internal`, ProGuard puede inlinearlas si llega el caso).

---

## Patrón Aplicado

**Companion factory en la interfaz** para preservar las llamadas existentes:

```kotlin
internal interface BiometricAuthenticator {
    suspend fun authenticateForEncryption(config: BiometricConfig): Result<Cipher>
    // ...

    companion object {
        fun create(activity: FragmentActivity): BiometricAuthenticator =
            AndroidBiometricAuthenticator(activity)
    }
}
```

Esto mantiene `BiometricAuthenticator.create(activity)` funcionando en `PasskeyAuth` sin cambios.

---

## Referencias

- ADR-001 — Estructura multi-módulo (las interfaces viven en `passkeyauth-core`)
- ADR-004 — KeyStoreManager AES-GCM (la implementación Android se mantiene intacta)
- ADR-006 — EnrollmentManager transaccional (consumidor principal de las interfaces)
- `.mobiai/brain/memories/testing.md` — Estrategia de testing que requiere estas interfaces
- "Working Effectively with Legacy Code" - Michael Feathers (seam concept)

---

## Revisiones

- **2026-05-31:** Creación inicial. Aprobada implementación incremental en 3 commits.
- **2026-05-31 (durante commit 1):** Interfaz 1 (`BiometricAuthenticator`) extraída e integrada. Build verde. Sin cambios en consumidores gracias al companion factory.
- **2026-05-31 (antes de commit 2):** Renombre de la interfaz 2: `CryptoKeyProvider` → `KeyStoreManager`. Razón: consistencia con `BiometricAuthenticator` (interfaz preserva nombre original, impl recibe prefijo `Android*`). Beneficio adicional: los 4 consumidores existentes mantienen el tipo `KeyStoreManager` en sus firmas → cero cambios fuera del módulo `core/crypto/`.
- **2026-05-31 (commit 3, Path C):** Tras pregunta del usuario sobre evolución a backends no-Firebase, analisis del codigo revela que el token se trata como bytes opacos (nunca se usa para API calls), validando que el modelo `AuthSession` simple es adecuado HOY. Sin embargo, para preservar evolutivilidad sin sobre-abstraer se aplican estas mejoras:
  - `Credentials` como sealed class (solo subtipo `EmailPassword` hoy; futuros: `AuthorizationCode`, `DeviceCode`, `MagicLink`).
  - `AuthSession` con campos `refreshToken: String?` y `expiresAt: Long?` nullable (Firebase: null; OAuth2 futuro: poblados).
  - `invalidateTemporaryPassword` extraida a interfaz separada `PasswordManagementBackend` (capability pattern). Backends que no soporten cambio de password client-side simplemente no inyectan esta dependencia.
  - KDoc explicito en `AuthBackend` documentando limitaciones conocidas y plan de evolucion.
  - Coste extra del Path C vs Path A simple: ~15 LOC. Beneficio: el dia que llegue Keycloak/Auth0/custom backend, los cambios son acotados (anadir subtipos a `Credentials`, poblar campos opcionales de `AuthSession`, anadir operacion `refreshSession()`) sin tocar firmas core ni romper consumidores.
- **Próxima revisión:** Al cerrar v0.3 con los tests escritos, evaluar si alguna interfaz necesita ajustes.
