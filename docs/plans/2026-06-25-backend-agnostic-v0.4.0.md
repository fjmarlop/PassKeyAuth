# Backend-Agnostic SDK v0.4.0 — Implementation Plan

> **For agentic workers:** Use `mobiai-mobile-executing-plans-with-subagents` (recommended) or `mobiai-mobile-executing-plans` to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Desacoplar el SDK de Firebase Auth exponiendo las interfaces de backend como tipos públicos e inyectables, de modo que integradores puedan usar Keycloak, un REST custom, o cualquier backend compatible.

**Architecture:** Las tres interfaces de backend (`AuthBackend`, `DeviceRegistry`, `PasswordManagementBackend`) se mueven del paquete interno `core.firebase` al paquete público `es.fjmarlop.corpsecauth`. Los modelos `Credentials` y `AuthSession` pasan de `internal` a `public`. `PasskeyAuth.initialize()` acepta instancias opcionales de estos contratos; si no se pasan, se usan las implementaciones Firebase como antes — **cero cambios breaking para integradores existentes**. Firebase queda como implementación de referencia incluida.

**Tech Stack:** Kotlin, Android, coroutines, MockK, JUnit4, Robolectric

**Platform:** Android

---

## Contexto y estado actual

### Archivos que se **eliminan** (interfaces internas obsoletas)
| Archivo | Razón |
|---|---|
| `passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/core/firebase/AuthBackend.kt` | Reemplazado por tipo público en paquete raíz |
| `passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/core/firebase/DeviceRegistry.kt` | Ídem |
| `passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/core/firebase/PasswordManagementBackend.kt` | Ídem |
| `passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/core/models/Credentials.kt` | Pasa a `es.fjmarlop.corpsecauth.Credentials` (public) |
| `passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/core/models/AuthSession.kt` | Pasa a `es.fjmarlop.corpsecauth.AuthSession` (public) |

### Archivos que se **modifican** (actualización de imports)
| Archivo | Qué cambia |
|---|---|
| `core/firebase/FirebaseAuthBackend.kt` | Implementa los nuevos tipos públicos; actualiza imports |
| `core/firebase/FirestoreDeviceRegistry.kt` | Ídem para `DeviceRegistry` |
| `core/enrollment/EnrollmentManager.kt` | Imports de `AuthBackend`, `DeviceRegistry`, `PasswordManagementBackend`, `Credentials` |
| `PasskeyAuth.kt` | Añade params de inyección; actualiza imports y getter `deviceRegistry` |
| `core/fakes/FakeAuthBackend.kt` | Actualiza imports |
| `core/fakes/FakePasswordManagementBackend.kt` | Actualiza imports |
| `core/fakes/InMemoryDeviceRegistry.kt` | Actualiza imports |
| `PasskeyAuthFacadeTest.kt` | Actualiza imports |
| `core/enrollment/EnrollmentManagerHappyPathTest.kt` | Actualiza imports |
| `core/enrollment/EnrollmentManagerRollbackTest.kt` | Actualiza imports |
| `core/firebase/FirebaseAuthBackendTest.kt` | Actualiza imports |
| `core/fakes/FakesSmokeTest.kt` | Actualiza imports |

### Archivos que se **crean**
| Archivo | Contenido |
|---|---|
| `es/fjmarlop/corpsecauth/AuthBackend.kt` | Interfaz pública |
| `es/fjmarlop/corpsecauth/DeviceRegistry.kt` | Interfaz pública |
| `es/fjmarlop/corpsecauth/PasswordManagementBackend.kt` | Interfaz pública |
| `es/fjmarlop/corpsecauth/Credentials.kt` | Sealed class pública |
| `es/fjmarlop/corpsecauth/AuthSession.kt` | Data class pública |
| `PasskeyAuthFacadeBackendInjectionTest.kt` | Test de inyección |
| `docs/adr/016-backend-agnostic-sdk.md` | ADR |

---

## Task 1: Crear rama y nuevos tipos públicos

**Branch:** `feat/backend-agnostic-v0.4.0` (crear desde `main` tras merge de PR #16)

**Files:**
- Create: `passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/AuthBackend.kt`
- Create: `passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/DeviceRegistry.kt`
- Create: `passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/PasswordManagementBackend.kt`
- Create: `passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/Credentials.kt`
- Create: `passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/AuthSession.kt`

- [ ] **Step 1.1: Crear rama**

```bash
git checkout main && git pull
git checkout -b feat/backend-agnostic-v0.4.0
```

- [ ] **Step 1.2: Crear `AuthBackend.kt`**

```kotlin
// passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/AuthBackend.kt
package es.fjmarlop.corpsecauth

import es.fjmarlop.corpsecauth.core.models.AuthUser

/**
 * Contrato de autenticacion contra un backend remoto.
 *
 * Implementar esta interfaz para usar un backend alternativo a Firebase
 * (Keycloak, OIDC custom, REST propio). Ver ADR-016.
 *
 * **Implementacion de referencia incluida:**
 * - `FirebaseAuthBackend` (interno): Firebase Auth.
 *
 * **Ejemplo de uso con backend custom:**
 * ```kotlin
 * class MyKeycloakBackend : AuthBackend { ... }
 *
 * PasskeyAuth.initialize(
 *     context = this,
 *     authBackend = MyKeycloakBackend()
 * )
 * ```
 *
 * **Limitaciones conocidas para backends OAuth2/OIDC (ver ADR-016):**
 * - `getCurrentUser()` es sincrono (modelo Firebase con cache local). Backends
 *   sin cache requieren adaptador.
 * - La gestion de password temporal es una capability separada ([PasswordManagementBackend]).
 */
interface AuthBackend {

    /**
     * Autentica al usuario con las credenciales proporcionadas.
     *
     * @return [Result.success] con [AuthSession] o [Result.failure] con un [Throwable].
     */
    suspend fun authenticate(credentials: Credentials): Result<AuthSession>

    /**
     * Devuelve el usuario actualmente autenticado, o `null` si no hay sesion.
     */
    fun getCurrentUser(): AuthUser?

    /**
     * Cierra la sesion actual.
     */
    fun signOut()
}
```

- [ ] **Step 1.3: Crear `DeviceRegistry.kt`**

```kotlin
// passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/DeviceRegistry.kt
package es.fjmarlop.corpsecauth

import android.content.Context
import es.fjmarlop.corpsecauth.core.models.DeviceInfo

/**
 * Contrato del registry de dispositivos vinculados a usuarios.
 *
 * Implementar esta interfaz para persistir el binding usuario-dispositivo
 * en un almacen alternativo a Cloud Firestore. Ver ADR-016.
 *
 * **Garantias del contrato:**
 * - [bindDevice] devuelve el deviceId asignado al dispositivo.
 * - [validateDevice] devuelve `true` solo si el deviceId actual coincide y esta activo.
 * - [revokeDevice] usa soft delete (preserva historial de auditoria).
 */
interface DeviceRegistry {

    /**
     * Vincula el dispositivo actual al usuario en el registry.
     *
     * @return [Result.success] con el deviceId asignado o [Result.failure].
     */
    suspend fun bindDevice(userId: String): Result<String>

    /**
     * Valida que el dispositivo actual sea el registrado y activo para el usuario.
     */
    suspend fun validateDevice(userId: String): Result<Boolean>

    /**
     * Revoca el dispositivo registrado del usuario (soft delete).
     */
    suspend fun revokeDevice(userId: String): Result<Unit>

    /**
     * Devuelve la informacion del dispositivo registrado para el usuario, o `null`.
     */
    suspend fun getDeviceInfo(userId: String): Result<DeviceInfo?>

    companion object {
        /**
         * Crea la implementacion por defecto (Firestore).
         * Llamado internamente por [PasskeyAuth] cuando no se inyecta un registry custom.
         */
        fun createDefault(context: Context): DeviceRegistry =
            es.fjmarlop.corpsecauth.core.firebase.FirestoreDeviceRegistry.create(context)
    }
}
```

- [ ] **Step 1.4: Crear `PasswordManagementBackend.kt`**

```kotlin
// passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/PasswordManagementBackend.kt
package es.fjmarlop.corpsecauth

/**
 * Capability opcional de gestion client-side de password temporal.
 *
 * Implementar esta interfaz junto con [AuthBackend] si el backend soporta
 * invalidacion de password temporal client-side (modelo Firebase Auth).
 *
 * Backends OAuth2/OIDC tipicos (Keycloak, Auth0) NO soportan este modelo
 * y pueden omitir esta interfaz. Ver ADR-016.
 */
interface PasswordManagementBackend {

    /**
     * Invalida la password temporal del usuario actual reemplazandola por una
     * password aleatoria fuerte que el usuario nunca conoce (passwordless real).
     *
     * @return [Result.success] o [Result.failure].
     */
    suspend fun invalidateTemporaryPassword(): Result<Unit>
}
```

- [ ] **Step 1.5: Crear `Credentials.kt`**

```kotlin
// passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/Credentials.kt
package es.fjmarlop.corpsecauth

/**
 * Credenciales del usuario para [AuthBackend.authenticate].
 *
 * Sealed class para soportar multiples mecanismos sin romper la firma del contrato.
 *
 * Subtipos futuros previstos: `AuthorizationCode` (OAuth2), `DeviceCode`, `MagicLink`.
 */
sealed class Credentials {

    /**
     * Credenciales email + password para el flujo de enrollment con credenciales
     * temporales proporcionadas por IT (ADR-006).
     */
    data class EmailPassword(
        val email: String,
        val password: String
    ) : Credentials()
}
```

- [ ] **Step 1.6: Crear `AuthSession.kt`**

```kotlin
// passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/AuthSession.kt
package es.fjmarlop.corpsecauth

import es.fjmarlop.corpsecauth.core.models.AuthUser

/**
 * Sesion autenticada resultante de un [AuthBackend.authenticate] exitoso.
 *
 * @property user Usuario autenticado.
 * @property idToken Token opaco para validacion server-side.
 * @property refreshToken Token de refresco (opcional; null si el backend gestiona refresco internamente).
 * @property expiresAt Timestamp Unix en ms de expiracion (null si el backend gestiona expiracion).
 */
data class AuthSession(
    val user: AuthUser,
    val idToken: String,
    val refreshToken: String? = null,
    val expiresAt: Long? = null
)
```

- [ ] **Step 1.7: Verificar que compila (no rompe nada aun — los tipos nuevos coexisten con los internos)**

```bash
./gradlew :passkeyauth-core:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL (no conflictos — diferentes paquetes)

- [ ] **Step 1.8: Commit**

```bash
git add passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/AuthBackend.kt \
        passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/DeviceRegistry.kt \
        passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/PasswordManagementBackend.kt \
        passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/Credentials.kt \
        passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/AuthSession.kt
git commit -m "feat(api): tipos públicos AuthBackend, DeviceRegistry, Credentials, AuthSession (v0.4.0)"
```

---

## Task 2: Actualizar implementaciones Firebase + eliminar interfaces internas

Los tipos internos en `core.firebase` y `core.models` se reemplazan por los tipos públicos del Task 1.

**Files:**
- Modify: `passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/core/firebase/FirebaseAuthBackend.kt`
- Modify: `passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/core/firebase/FirestoreDeviceRegistry.kt`
- Modify: `passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/core/enrollment/EnrollmentManager.kt`
- Delete: `passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/core/firebase/AuthBackend.kt`
- Delete: `passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/core/firebase/DeviceRegistry.kt`
- Delete: `passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/core/firebase/PasswordManagementBackend.kt`
- Delete: `passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/core/models/Credentials.kt`
- Delete: `passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/core/models/AuthSession.kt`

- [ ] **Step 2.1: Actualizar imports en `FirebaseAuthBackend.kt`**

Cambiar las líneas 8–10 (imports de modelos internos + interfaces del mismo paquete ya implícitas):

```kotlin
// ANTES (líneas 8–10):
import es.fjmarlop.corpsecauth.core.errors.FirebaseException
import es.fjmarlop.corpsecauth.core.models.AuthSession
import es.fjmarlop.corpsecauth.core.models.AuthUser
import es.fjmarlop.corpsecauth.core.models.Credentials

// DESPUÉS — reemplazar las 2 líneas de core.models por los tipos públicos:
import es.fjmarlop.corpsecauth.AuthSession
import es.fjmarlop.corpsecauth.AuthBackend
import es.fjmarlop.corpsecauth.Credentials
import es.fjmarlop.corpsecauth.PasswordManagementBackend
import es.fjmarlop.corpsecauth.core.errors.FirebaseException
import es.fjmarlop.corpsecauth.core.models.AuthUser
```

La línea 31 (`): AuthBackend, PasswordManagementBackend {`) queda igual sintácticamente, pero ahora resuelve los tipos públicos.

- [ ] **Step 2.2: Actualizar imports en `FirestoreDeviceRegistry.kt`**

`FirestoreDeviceRegistry` está en `core.firebase` e implementa `DeviceRegistry` del mismo paquete (sin import explícito). Tras borrar el archivo interno, hay que añadir el import del tipo público:

```kotlin
// Añadir al bloque de imports de FirestoreDeviceRegistry.kt:
import es.fjmarlop.corpsecauth.DeviceRegistry
```

La línea de declaración (`internal class FirestoreDeviceRegistry(...) : DeviceRegistry {`) queda sintácticamente igual.

- [ ] **Step 2.3: Actualizar imports en `EnrollmentManager.kt`**

```kotlin
// ELIMINAR:
import es.fjmarlop.corpsecauth.core.firebase.AuthBackend
import es.fjmarlop.corpsecauth.core.firebase.DeviceRegistry
import es.fjmarlop.corpsecauth.core.firebase.PasswordManagementBackend
import es.fjmarlop.corpsecauth.core.models.Credentials

// AÑADIR:
import es.fjmarlop.corpsecauth.AuthBackend
import es.fjmarlop.corpsecauth.Credentials
import es.fjmarlop.corpsecauth.DeviceRegistry
import es.fjmarlop.corpsecauth.PasswordManagementBackend
```

- [ ] **Step 2.4: Eliminar archivos obsoletos**

```bash
rm passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/core/firebase/AuthBackend.kt
rm passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/core/firebase/DeviceRegistry.kt
rm passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/core/firebase/PasswordManagementBackend.kt
rm passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/core/models/Credentials.kt
rm passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/core/models/AuthSession.kt
```

En Windows: usar `Remove-Item` o el explorador de archivos.

- [ ] **Step 2.5: Compilar — verificar que no hay errores de resolución de tipos**

```bash
./gradlew :passkeyauth-core:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL. Si hay errores de "unresolved reference", significa que falta actualizar algún import.

- [ ] **Step 2.6: Actualizar docstrings obsoletos**

En `FirebaseAuthBackend.kt` hay una referencia en el KDoc a `[es.fjmarlop.corpsecauth.core.models.AuthSession]` — actualizar esa referencia a `[AuthSession]`.

En `Credentials.kt` (el nuevo archivo), los KDoc que referencien `core.firebase.AuthBackend` ya están actualizados en el template del Task 1.

- [ ] **Step 2.7: Commit**

```bash
git add -u   # solo cambios en tracked files (deletes + modifies)
git commit -m "refactor: interfaces backend-agnostic pasan a paquete público; elimina tipos internos"
```

---

## Task 3: Inyección de backend custom en `PasskeyAuth.initialize()`

**Files:**
- Modify: `passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/PasskeyAuth.kt`

**Cambios:**

1. Eliminar los 3 imports de `core.firebase` que ya no existen (las interfaces):
   ```kotlin
   // ELIMINAR estas 3 líneas del bloque de imports:
   import es.fjmarlop.corpsecauth.core.firebase.AuthBackend
   import es.fjmarlop.corpsecauth.core.firebase.DeviceRegistry
   import es.fjmarlop.corpsecauth.core.firebase.PasswordManagementBackend
   ```
   (`AuthBackend`, `DeviceRegistry`, `PasswordManagementBackend` están ahora en el mismo paquete `es.fjmarlop.corpsecauth`, sin import necesario.)

2. Añadir backing fields para inyección custom (tras los backing fields existentes, antes de `_keyStoreManager`):
   ```kotlin
   // Backends inyectados por el integrador vía initialize() (ADR-016).
   // null = usar implementación Firebase por defecto.
   private var _customAuthBackend: AuthBackend? = null
   private var _customDeviceRegistry: DeviceRegistry? = null
   ```

3. Actualizar getters `authBackend`, `passwordManagement` y `deviceRegistry`:
   ```kotlin
   // ANTES:
   private val authBackend: AuthBackend get() = firebaseAuthBackend
   private val passwordManagement: PasswordManagementBackend get() = firebaseAuthBackend
   private var _deviceRegistry: DeviceRegistry? = null
   private val deviceRegistry: DeviceRegistry
       get() = _deviceRegistry ?: DeviceRegistry.create(requireContext()).also { _deviceRegistry = it }

   // DESPUÉS:
   private val authBackend: AuthBackend
       get() = _customAuthBackend ?: firebaseAuthBackend

   private val passwordManagement: PasswordManagementBackend
       get() = (_customAuthBackend as? PasswordManagementBackend) ?: firebaseAuthBackend

   private var _deviceRegistry: DeviceRegistry? = null
   private val deviceRegistry: DeviceRegistry
       get() = _customDeviceRegistry
           ?: _deviceRegistry
           ?: DeviceRegistry.createDefault(requireContext()).also { _deviceRegistry = it }
   ```

4. Actualizar la firma de `initialize()` con params opcionales, y almacenar backends ANTES de `refreshAuthState()`:
   ```kotlin
   // ANTES:
   suspend fun initialize(
       context: Context,
       config: PasskeyAuthConfig = PasskeyAuthConfig.Default
   ): Result<Unit> {
       return try {
           if (isInitialized) { ... }
           // ... integrity check ...
           appContext = context.applicationContext
           this.config = config
           refreshAuthState()
           isInitialized = true
           ...
       }
   }

   // DESPUÉS:
   suspend fun initialize(
       context: Context,
       config: PasskeyAuthConfig = PasskeyAuthConfig.Default,
       authBackend: AuthBackend? = null,
       deviceRegistry: DeviceRegistry? = null
   ): Result<Unit> {
       return try {
           if (isInitialized) { ... }
           // ... integrity check (sin cambios) ...
           appContext = context.applicationContext
           this.config = config
           _customAuthBackend = authBackend       // NUEVO: antes de refreshAuthState
           _customDeviceRegistry = deviceRegistry // NUEVO
           refreshAuthState()
           isInitialized = true
           ...
       }
   }
   ```

5. Actualizar `reset()` para nulificar los nuevos campos:
   ```kotlin
   internal fun reset() {
       isInitialized = false
       appContext = null
       config = null
       _authState.value = AuthResult.Loading
       _firebaseAuthBackend = null
       _keyStoreManager = null
       _cryptoProvider = null
       _secureStorage = null
       _deviceRegistry = null
       _customAuthBackend = null      // NUEVO
       _customDeviceRegistry = null   // NUEVO
   }
   ```

- [ ] **Step 3.1: Aplicar los cambios en `PasskeyAuth.kt`** según el detalle de arriba (5 modificaciones).

- [ ] **Step 3.2: Compilar**

```bash
./gradlew :passkeyauth-core:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3.3: Commit**

```bash
git add passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/PasskeyAuth.kt
git commit -m "feat(core): PasskeyAuth.initialize() acepta authBackend y deviceRegistry opcionales"
```

---

## Task 4: Actualizar fakes de test + escribir test de inyección

**Files:**
- Modify: `passkeyauth-core/src/test/java/es/fjmarlop/corpsecauth/core/fakes/FakeAuthBackend.kt`
- Modify: `passkeyauth-core/src/test/java/es/fjmarlop/corpsecauth/core/fakes/FakePasswordManagementBackend.kt`
- Modify: `passkeyauth-core/src/test/java/es/fjmarlop/corpsecauth/core/fakes/InMemoryDeviceRegistry.kt`
- Modify: `passkeyauth-core/src/test/java/es/fjmarlop/corpsecauth/PasskeyAuthFacadeTest.kt`
- Modify: `passkeyauth-core/src/test/java/es/fjmarlop/corpsecauth/core/enrollment/EnrollmentManagerHappyPathTest.kt`
- Modify: `passkeyauth-core/src/test/java/es/fjmarlop/corpsecauth/core/enrollment/EnrollmentManagerRollbackTest.kt`
- Modify: `passkeyauth-core/src/test/java/es/fjmarlop/corpsecauth/core/firebase/FirebaseAuthBackendTest.kt`
- Modify: `passkeyauth-core/src/test/java/es/fjmarlop/corpsecauth/core/fakes/FakesSmokeTest.kt`
- Create: `passkeyauth-core/src/test/java/es/fjmarlop/corpsecauth/PasskeyAuthFacadeBackendInjectionTest.kt`

- [ ] **Step 4.1: Actualizar imports en `FakeAuthBackend.kt`**

```kotlin
// ELIMINAR:
import es.fjmarlop.corpsecauth.core.firebase.AuthBackend
import es.fjmarlop.corpsecauth.core.models.AuthSession
import es.fjmarlop.corpsecauth.core.models.Credentials

// AÑADIR:
import es.fjmarlop.corpsecauth.AuthBackend
import es.fjmarlop.corpsecauth.AuthSession
import es.fjmarlop.corpsecauth.Credentials
```

- [ ] **Step 4.2: Actualizar imports en `FakePasswordManagementBackend.kt`**

```kotlin
// ELIMINAR:
import es.fjmarlop.corpsecauth.core.firebase.PasswordManagementBackend

// AÑADIR:
import es.fjmarlop.corpsecauth.PasswordManagementBackend
```

- [ ] **Step 4.3: Actualizar imports en `InMemoryDeviceRegistry.kt`**

```kotlin
// ELIMINAR:
import es.fjmarlop.corpsecauth.core.firebase.DeviceRegistry

// AÑADIR:
import es.fjmarlop.corpsecauth.DeviceRegistry
```

- [ ] **Step 4.4: Actualizar imports en `PasskeyAuthFacadeTest.kt`**

```kotlin
// ELIMINAR:
import es.fjmarlop.corpsecauth.core.firebase.AuthBackend
import es.fjmarlop.corpsecauth.core.firebase.DeviceRegistry

// AÑADIR:
import es.fjmarlop.corpsecauth.AuthBackend
import es.fjmarlop.corpsecauth.DeviceRegistry
```

- [ ] **Step 4.5: Actualizar imports en `EnrollmentManagerHappyPathTest.kt`**

```kotlin
// ELIMINAR:
import es.fjmarlop.corpsecauth.core.models.AuthSession
import es.fjmarlop.corpsecauth.core.models.Credentials

// AÑADIR:
import es.fjmarlop.corpsecauth.AuthSession
import es.fjmarlop.corpsecauth.Credentials
```

- [ ] **Step 4.6: Actualizar imports en `EnrollmentManagerRollbackTest.kt`**

```kotlin
// ELIMINAR:
import es.fjmarlop.corpsecauth.core.models.AuthSession

// AÑADIR:
import es.fjmarlop.corpsecauth.AuthSession
```

- [ ] **Step 4.7: Actualizar imports en `FirebaseAuthBackendTest.kt`**

```kotlin
// ELIMINAR:
import es.fjmarlop.corpsecauth.core.models.Credentials

// AÑADIR:
import es.fjmarlop.corpsecauth.Credentials
```

- [ ] **Step 4.8: Actualizar imports en `FakesSmokeTest.kt`**

```kotlin
// ELIMINAR:
import es.fjmarlop.corpsecauth.core.models.AuthSession
import es.fjmarlop.corpsecauth.core.models.Credentials

// AÑADIR:
import es.fjmarlop.corpsecauth.AuthSession
import es.fjmarlop.corpsecauth.Credentials
```

- [ ] **Step 4.9: Ejecutar test suite — verificar que TODO compila y sigue en verde antes del nuevo test**

```bash
./gradlew :passkeyauth-core:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL, todos los tests previos en verde.

- [ ] **Step 4.10: Escribir el test de inyección (FAILING)**

Crear `PasskeyAuthFacadeBackendInjectionTest.kt`:

```kotlin
// passkeyauth-core/src/test/java/es/fjmarlop/corpsecauth/PasskeyAuthFacadeBackendInjectionTest.kt
package es.fjmarlop.corpsecauth

import android.content.Context
import android.content.pm.PackageManager
import com.google.common.truth.Truth.assertThat
import es.fjmarlop.corpsecauth.core.fakes.FakeAuthBackend
import es.fjmarlop.corpsecauth.core.fakes.FakeKeyStoreManager
import es.fjmarlop.corpsecauth.core.fakes.InMemoryDeviceRegistry
import es.fjmarlop.corpsecauth.core.firebase.FirebaseAuthBackend
import es.fjmarlop.corpsecauth.core.firebase.FirestoreDeviceRegistry
import es.fjmarlop.corpsecauth.core.models.AuthResult
import es.fjmarlop.corpsecauth.core.models.AuthUser
import es.fjmarlop.corpsecauth.core.storage.SecureStorage
import es.fjmarlop.corpsecauth.core.support.MainDispatcherRule
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Tests de inyeccion de backend custom en [PasskeyAuth.initialize].
 *
 * Verifica que cuando se pasa un [AuthBackend] o [DeviceRegistry] custom,
 * esos objetos se usan en lugar de las implementaciones Firebase.
 */
internal class PasskeyAuthFacadeBackendInjectionTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fakeKeyStoreManager: FakeKeyStoreManager
    private lateinit var secureStorageMock: SecureStorage
    private lateinit var context: Context

    @Before
    fun setUp() {
        fakeKeyStoreManager = FakeKeyStoreManager()
        secureStorageMock = mockk(relaxed = true)

        context = mockk(relaxed = true)
        val packageManager = mockk<PackageManager>(relaxed = true)
        every { context.applicationContext } returns context
        every { context.packageManager } returns packageManager
        every { context.packageName } returns "es.fjmarlop.corpsecauth.test"

        // Interceptar factories de infraestructura
        mockkObject(FirebaseAuthBackend.Companion)
        mockkObject(FirestoreDeviceRegistry.Companion)
        mockkObject(es.fjmarlop.corpsecauth.core.crypto.KeyStoreManager.Companion)
        mockkObject(SecureStorage.Companion)

        every { es.fjmarlop.corpsecauth.core.crypto.KeyStoreManager.createDefault() } returns fakeKeyStoreManager
        every { es.fjmarlop.corpsecauth.core.crypto.KeyStoreManager.createWithStrongBox() } returns fakeKeyStoreManager
        every { SecureStorage.create(any()) } returns secureStorageMock
        every { secureStorageMock.hasStoredSession() } returns false
        every { secureStorageMock.loadUserId() } returns Result.success(null)
    }

    @After
    fun tearDown() {
        PasskeyAuth.reset()
        unmockkAll()
    }

    @Test
    fun `initialize con authBackend custom usa ese backend, no instancia FirebaseAuthBackend`() = runTest {
        val customBackend = FakeAuthBackend()

        val result = PasskeyAuth.initialize(
            context = context,
            config = PasskeyAuthConfig.Custom(
                rootPolicy = RootPolicy.Allow,
                emulatorPolicy = EmulatorPolicy.Allow,
            ),
            authBackend = customBackend
        )

        assertThat(result.isSuccess).isTrue()
        verify(exactly = 0) { FirebaseAuthBackend.createDefault() }
    }

    @Test
    fun `initialize con deviceRegistry custom usa ese registry, no instancia FirestoreDeviceRegistry`() = runTest {
        val customRegistry = InMemoryDeviceRegistry()

        val result = PasskeyAuth.initialize(
            context = context,
            config = PasskeyAuthConfig.Custom(
                rootPolicy = RootPolicy.Allow,
                emulatorPolicy = EmulatorPolicy.Allow,
            ),
            deviceRegistry = customRegistry
        )

        assertThat(result.isSuccess).isTrue()
        verify(exactly = 0) { FirestoreDeviceRegistry.create(any()) }
    }

    @Test
    fun `getCurrentUser devuelve usuario del authBackend inyectado`() = runTest {
        val expectedUser = AuthUser(
            uid = "uid-custom-backend",
            email = "custom@test.com",
            displayName = null,
            photoUrl = null,
            isEmailVerified = true
        )
        val customBackend = FakeAuthBackend().apply {
            forceCurrentUser(expectedUser)
        }

        PasskeyAuth.initialize(
            context = context,
            config = PasskeyAuthConfig.Custom(
                rootPolicy = RootPolicy.Allow,
                emulatorPolicy = EmulatorPolicy.Allow,
            ),
            authBackend = customBackend
        )

        val user = PasskeyAuth.getCurrentUser()

        assertThat(user).isEqualTo(expectedUser)
        assertThat(customBackend.getCurrentUserCallCount).isEqualTo(1)
        verify(exactly = 0) { FirebaseAuthBackend.createDefault() }
    }

    @Test
    fun `sin inyeccion, initialize sigue usando Firebase por defecto`() = runTest {
        val firebaseBackendMock = mockk<FirebaseAuthBackend>(relaxed = true)
        every { FirebaseAuthBackend.createDefault() } returns firebaseBackendMock
        every { firebaseBackendMock.getCurrentUser() } returns null

        val result = PasskeyAuth.initialize(
            context = context,
            config = PasskeyAuthConfig.Custom(
                rootPolicy = RootPolicy.Allow,
                emulatorPolicy = EmulatorPolicy.Allow,
            )
            // sin authBackend ni deviceRegistry
        )

        assertThat(result.isSuccess).isTrue()
        // Firebase NO se instancia en initialize() — la creacion es lazy
        // Solo se crea cuando se accede al getter por primera vez (e.g. getCurrentUser)
        PasskeyAuth.getCurrentUser()
        verify(exactly = 1) { FirebaseAuthBackend.createDefault() }
    }
}
```

- [ ] **Step 4.11: Ejecutar el nuevo test para verificar que FALLA (antes de que el Step 3 esté hecho)**

Si Task 3 ya está implementado, el test debería pasar en verde directamente.

```bash
./gradlew :passkeyauth-core:testDebugUnitTest \
    --tests "es.fjmarlop.corpsecauth.PasskeyAuthFacadeBackendInjectionTest"
```

Expected tras Task 3: PASS (4 tests verdes)

- [ ] **Step 4.12: Ejecutar suite completa**

```bash
./gradlew :passkeyauth-core:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL, todos los tests en verde.

- [ ] **Step 4.13: Commit**

```bash
git add passkeyauth-core/src/test/java/es/fjmarlop/corpsecauth/core/fakes/FakeAuthBackend.kt \
        passkeyauth-core/src/test/java/es/fjmarlop/corpsecauth/core/fakes/FakePasswordManagementBackend.kt \
        passkeyauth-core/src/test/java/es/fjmarlop/corpsecauth/core/fakes/InMemoryDeviceRegistry.kt \
        passkeyauth-core/src/test/java/es/fjmarlop/corpsecauth/PasskeyAuthFacadeTest.kt \
        passkeyauth-core/src/test/java/es/fjmarlop/corpsecauth/core/enrollment/EnrollmentManagerHappyPathTest.kt \
        passkeyauth-core/src/test/java/es/fjmarlop/corpsecauth/core/enrollment/EnrollmentManagerRollbackTest.kt \
        passkeyauth-core/src/test/java/es/fjmarlop/corpsecauth/core/firebase/FirebaseAuthBackendTest.kt \
        passkeyauth-core/src/test/java/es/fjmarlop/corpsecauth/core/fakes/FakesSmokeTest.kt \
        passkeyauth-core/src/test/java/es/fjmarlop/corpsecauth/PasskeyAuthFacadeBackendInjectionTest.kt
git commit -m "test: actualiza imports post-refactor + test de inyeccion de backend custom"
```

---

## Task 5: ADR-016 + CHANGELOG + build final

**Files:**
- Create: `docs/adr/016-backend-agnostic-sdk.md`
- Modify: `CHANGELOG.md`

- [ ] **Step 5.1: Crear `docs/adr/016-backend-agnostic-sdk.md`**

```markdown
# ADR-016: SDK Backend-Agnostic — Inyección de AuthBackend y DeviceRegistry

**Estado:** Aceptado
**Fecha:** 2026-06-25
**Autores:** Francisco Javier Marmolejo López

---

## Contexto

El SDK v0.3.x tenia `AuthBackend`, `DeviceRegistry` y `PasswordManagementBackend` como
interfaces `internal` en el paquete `core.firebase`. Esto impedia que integradores
implementaran backends alternativos (Keycloak, REST custom, etc.) sin modificar el SDK.

Ademas, `Credentials` y `AuthSession` eran `internal`, lo que impedia al integrador
construir instancias de estos tipos (necesario para implementar la interfaz `AuthBackend`).

El plan completo vive en [`docs/plans/2026-06-25-backend-agnostic-v0.4.0.md`](../plans/2026-06-25-backend-agnostic-v0.4.0.md).

---

## Decisión

### 1. Contratos públicos en el paquete raíz

Las tres interfaces de backend se mueven a `es.fjmarlop.corpsecauth` (mismo paquete
que `PasskeyAuth`, `PasskeyAuthConfig`, etc.) como tipos `public`:

- `AuthBackend` — autenticacion
- `DeviceRegistry` — binding usuario-dispositivo
- `PasswordManagementBackend` — capability opcional de gestion de password

Los modelos `Credentials` y `AuthSession` pasan a `public`.

### 2. Inyección opcional en `initialize()`

```kotlin
PasskeyAuth.initialize(
    context = this,
    config = PasskeyAuthConfig.Default,           // sin cambios breaking
    authBackend = MyKeycloakBackend(),            // NUEVO — opcional
    deviceRegistry = MyPostgresDeviceRegistry()   // NUEVO — opcional
)
```

Si no se pasan, el SDK usa Firebase como antes. Cero cambios breaking.

### 3. Firebase como referencia incluida

`FirebaseAuthBackend` y `FirestoreDeviceRegistry` siguen incluidos en el SDK
como implementaciones `internal`. Son la opcion por defecto y no requieren
configuracion adicional por parte del integrador.

`PasswordManagementBackend` funciona como capability: si el `authBackend` inyectado
la implementa, se usa; si no, se usa `FirebaseAuthBackend` (que siempre la implementa).

### 4. Limitaciones conocidas (candidatas para v0.5.0)

- `getCurrentUser()` es sincrono (modelo Firebase con cache). Backends OIDC sin
  cache necesitan un adaptador de coroutine con timeout.
- `FirebaseException` es el tipo de error mas comun en los `Result.failure` internos.
  Renombrar a `AuthBackendException` generico esta pendiente para v0.5.0.
- La extraccion de `passkeyauth-firebase` como modulo Gradle separado (para hacer
  Firebase verdaderamente opcional) esta pendiente para v0.5.0.

---

## Consecuencias

**Positivas:**
- Integradores pueden usar Keycloak, Auth0, backends REST propios.
- La abstraccion ya existia como `internal` — el riesgo de regresar es minimo.
- Cero cambios breaking para integradores existentes.

**Negativas / trade-offs:**
- `DeviceRegistry.createDefault()` expone una referencia a `FirestoreDeviceRegistry`
  desde un tipo publico. Es opaca (tipo de retorno `DeviceRegistry`) pero indica
  que Firebase sigue acoplado al modulo hasta v0.5.0.
- `PasswordManagementBackend` es `public` pero el paso 2 del enrollment (que la usa)
  sigue comentado en `EnrollmentManager`. La interfaz es API publica sin flujo activo.

---

## Alternativas consideradas

- **Modulo `passkeyauth-firebase` opcional:** mas limpio, pero requiere reestructurar
  el grafo de dependencias Gradle. Aplazado a v0.5.0.
- **Mantener todo `internal` con factory method:** rechazado — el integrador no puede
  implementar la interfaz si es `internal`.

---

## Referencias

- Plan de implementacion: [`docs/plans/2026-06-25-backend-agnostic-v0.4.0.md`](../plans/2026-06-25-backend-agnostic-v0.4.0.md)
- ADR-010: Contrato del backend de autenticacion (Path C)
- ADR-013: Invariantes de seguridad no negociables
```

- [ ] **Step 5.2: Actualizar `CHANGELOG.md` — añadir sección v0.4.0**

Añadir al inicio de la sección de cambios (tras el header del changelog):

```markdown
## [0.4.0] — 2026-06-25

### Added
- `AuthBackend`, `DeviceRegistry`, `PasswordManagementBackend` como interfaces públicas en `es.fjmarlop.corpsecauth` — el integrador puede implementar backends alternativos (Keycloak, OIDC custom, REST propio).
- `Credentials` y `AuthSession` pasan a `public` — necesarios para implementar `AuthBackend`.
- `PasskeyAuth.initialize()` acepta `authBackend: AuthBackend?` y `deviceRegistry: DeviceRegistry?` opcionales. Si no se pasan, Firebase sigue siendo el backend por defecto (sin cambios breaking).

### Changed
- Las interfaces `AuthBackend`, `DeviceRegistry`, `PasswordManagementBackend` se mueven de `core.firebase` (interno) a `es.fjmarlop.corpsecauth` (público). Las implementaciones Firebase quedan como `internal`.
- `Credentials` y `AuthSession` se mueven de `core.models` a `es.fjmarlop.corpsecauth`.

### Internal
- ADR-016: documenta la decision de backend-agnostic y las limitaciones conocidas para v0.5.0.
```

- [ ] **Step 5.3: Build completo final**

```bash
./gradlew assembleDebug testDebugUnitTest
```

Expected: BUILD SUCCESSFUL, todos los tests en verde.

- [ ] **Step 5.4: Commit**

```bash
git add docs/adr/016-backend-agnostic-sdk.md CHANGELOG.md
git commit -m "docs: ADR-016 backend-agnostic + CHANGELOG v0.4.0"
```

---

## Verificacion final — self-review

**Spec coverage:**

| Requisito | Task que lo implementa |
|---|---|
| `AuthBackend` pública e inyectable | Task 1.2 + Task 3 |
| `DeviceRegistry` pública e inyectable | Task 1.3 + Task 3 |
| `PasswordManagementBackend` pública | Task 1.4 |
| `Credentials` y `AuthSession` públicas | Task 1.5 + 1.6 |
| Firebase como default (sin breaking change) | Task 3 (params opcionales) |
| Tests de inyección | Task 4.10 |
| ADR-016 | Task 5.1 |
| CHANGELOG | Task 5.2 |

**Type consistency:** Todos los tipos de retorno y parámetros usan `es.fjmarlop.corpsecauth.{AuthBackend,Credentials,AuthSession}` en los nuevos archivos. Los archivos modificados actualizan imports con el mismo path.

**No placeholders:** Toda la implementación es código completo.

---

## Nota de diseño: `PasswordManagementBackend` no es inyectable en `initialize()`

`PasswordManagementBackend` se implementa como capability del `authBackend`:
- Si `authBackend` (custom) implementa `PasswordManagementBackend`, se usa esa implementación.
- Si no, se usa `FirebaseAuthBackend` como fallback.

Esta decisión mantiene la firma de `initialize()` simple (2 parámetros en vez de 3) para el caso común. Backends Firebase (el default) siempre implementan ambas interfaces.
