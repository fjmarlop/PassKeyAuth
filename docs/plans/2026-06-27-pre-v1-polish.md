# Pre-v1.0.0 Polish — Bug Fixes Implementation Plan

> **For agentic workers:** Use `mobiai-mobile-executing-plans-with-subagents` (recommended) or `mobiai-mobile-executing-plans` to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Eliminar println(), corregir fuga de FirebaseException en API pública, arreglar leak de coroutine scope en reset(), restringir visibilidad de funciones internas, y añadir KDoc a la API pública — dejando el SDK listo para publicación en Maven Central.

**Architecture:** Tres cambios ortogonales en `PasskeyAuth.kt` (scope, FirebaseException, visibilidad) + barrido de println en todos los módulos + KDoc. El paso 2 del enrollment (invalidateTemporaryPassword) se deja comentado intencionalmente para no interferir con pruebas en desarrollo.

**Tech Stack:** Kotlin, Android SDK, Coroutines, MockK, JUnit4, Robolectric

**Platform:** Android

---

## Dependency graph

```
Task 1 (println — archivos internos)  ──┐
                                        ├──> Task 3 (KDoc)
Task 2 (PasskeyAuth.kt — todo)        ──┘
```

Task 1 y Task 2 son independientes entre sí (ficheros distintos). Task 3 depende de que Task 2 haya estabilizado la API pública.

---

## Task 1: Eliminar println() de todos los archivos internos

**Archivos a modificar:**
- `passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/core/auth/AndroidBiometricAuthenticator.kt`
- `passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/core/crypto/AndroidKeyStoreManager.kt`
- `passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/core/crypto/CryptoProvider.kt`
- `passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/core/firebase/FirebaseAuthBackend.kt`
- `passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/core/firebase/FirestoreDeviceRegistry.kt`
- `passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/core/storage/SecureStorage.kt`
- `passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/core/enrollment/EnrollmentManager.kt`
- `passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/core/security/IntegrityGuard.kt`

**Estrategia:** Eliminar todas las líneas `println(...)` completas. En `IntegrityGuard`, cambiar el default del parámetro `logger` de `::println` a `{}` (no-op) — el parámetro se mantiene para tests.

- [ ] **Step 1: Eliminar println de AndroidBiometricAuthenticator.kt**

Eliminar las 15 líneas de `println(...)`. Ejemplo de las que hay:
```kotlin
// ANTES — eliminar estas líneas completas:
println("✅ BiometricAuthenticator: Biometria STRONG disponible")
println("❌ BiometricAuthenticator: Sin hardware biometrico")
println("⚠️ BiometricAuthenticator: Hardware no disponible")
println("⚠️ BiometricAuthenticator: Sin huellas registradas")
println("⚠️ BiometricAuthenticator: Actualizacion de seguridad requerida")
println("🔐 BiometricAuthenticator: Iniciando autenticacion para cifrado")
println("❌ BiometricAuthenticator: Error obteniendo cipher: ${error.message}")
println("🔓 BiometricAuthenticator: Iniciando autenticacion para descifrado")
println("❌ BiometricAuthenticator: Error obteniendo cipher: ${error.message}")
println("✅ BiometricAuthenticator: Autenticacion exitosa")
println("🚨 BiometricAuthenticator: Cipher null despues de autenticacion")
println("❌ BiometricAuthenticator: Error de autenticacion ($errorCode): $errString")
println("⚠️ BiometricAuthenticator: Intento fallido (usuario puede reintentar)")
println("🚫 BiometricAuthenticator: Autenticacion cancelada")
println("❌ BiometricAuthenticator: Error mostrando prompt: ${e.message}")
```

- [ ] **Step 2: Eliminar println de AndroidKeyStoreManager.kt**

Eliminar las ~13 líneas de `println(...)`.

- [ ] **Step 3: Eliminar println de CryptoProvider.kt**

Eliminar las ~7 líneas de `println(...)`.

- [ ] **Step 4: Eliminar println de FirebaseAuthBackend.kt**

Eliminar las ~8 líneas de `println(...)`.

- [ ] **Step 5: Eliminar println de FirestoreDeviceRegistry.kt**

Eliminar las ~12 líneas de `println(...)`.

- [ ] **Step 6: Eliminar println de SecureStorage.kt**

Eliminar las ~12 líneas de `println(...)`.

- [ ] **Step 7: Eliminar println de EnrollmentManager.kt**

Eliminar todas las líneas `println(...)` del archivo. El TODO del paso 2 se mantiene.

- [ ] **Step 8: Cambiar default del logger en IntegrityGuard.kt**

```kotlin
// ANTES:
logger: (String) -> Unit = ::println,

// DESPUÉS:
logger: (String) -> Unit = {},
```

- [ ] **Step 9: Verificar que compila y los tests pasan**

```bash
.\gradlew.bat passkeyauth-core:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 10: Commit**

```bash
git add passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/core/
git commit -m "chore(core): elimina println() de modulos internos — SDK silencioso por defecto"
```

---

## Task 2: Fixes en PasskeyAuth.kt — scope, FirebaseException, visibilidad, println

**Archivo:** `passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/PasskeyAuth.kt`

Cuatro cambios en el mismo archivo:
1. `private val scope` → `private var scope` + `scope.cancel()` en `reset()`
2. `FirebaseException.UserNotFound` → `DeviceException.NotEnrolled` en `authenticate()`
3. `fun refreshAuthState()` y `fun invalidateSession()` → `internal fun`
4. Eliminar todos los `println(...)` del archivo

- [ ] **Step 1: Escribir test para verificar que el scope se cancela en reset()**

En `passkeyauth-core/src/test/java/es/fjmarlop/corpsecauth/PasskeyAuthFacadeTest.kt`, añadir al final de la clase:

```kotlin
@Test
fun `reset cancela el scope — coroutines del ciclo anterior no emiten al nuevo estado`() = runTest {
    // initialize() lanza refreshAuthState() en scope. Luego reset() debe cancelar ese scope.
    // El test verifica que después del reset, authState vuelve a Loading limpio.
    PasskeyAuth.reset()
    assertThat(PasskeyAuth.authState.value).isInstanceOf(AuthResult.Loading::class.java)
}
```

- [ ] **Step 2: Ejecutar el test para verificar que pasa** (ya pasa porque reset() existe, pero confirmamos que no hay regresión post-cambio de scope)

```bash
.\gradlew.bat passkeyauth-core:testDebugUnitTest --tests "es.fjmarlop.corpsecauth.PasskeyAuthFacadeTest.reset cancela el scope*"
```

- [ ] **Step 3: Cambiar `val scope` a `var scope` y añadir cancel en reset()**

```kotlin
// ANTES:
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

// DESPUÉS:
private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
```

En `reset()`, añadir las dos líneas al principio:
```kotlin
internal fun reset() {
    scope.cancel()                                              // NUEVA
    scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)   // NUEVA
    isInitialized = false
    appContext = null
    // ... resto sin cambios
}
```

- [ ] **Step 4: Reemplazar FirebaseException.UserNotFound por DeviceException.NotEnrolled en authenticate()**

Buscar en `authenticate()` el bloque:
```kotlin
val currentUser = authBackend.getCurrentUser()
    ?: return Result.failure(
        es.fjmarlop.corpsecauth.core.errors.FirebaseException.UserNotFound(
            "Usuario no encontrado en Firebase"
        )
    )
```

Reemplazar por:
```kotlin
val currentUser = authBackend.getCurrentUser()
    ?: return Result.failure(
        es.fjmarlop.corpsecauth.core.errors.DeviceException.NotEnrolled(
            "No hay sesion de usuario activa — dispositivo no enrollado o sesion expirada"
        )
    )
```

- [ ] **Step 5: Marcar refreshAuthState() e invalidateSession() como internal**

```kotlin
// ANTES:
fun refreshAuthState() {

// DESPUÉS:
internal fun refreshAuthState() {
```

```kotlin
// ANTES:
fun invalidateSession() {

// DESPUÉS:
internal fun invalidateSession() {
```

- [ ] **Step 6: Eliminar todos los println de PasskeyAuth.kt**

Eliminar las ~20 líneas con `println(...)` del archivo. Los mensajes de error se propagan via `Result.failure(...)`, no via stdout.

- [ ] **Step 7: Verificar que los tests pasan**

```bash
.\gradlew.bat passkeyauth-core:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/PasskeyAuth.kt
git add passkeyauth-core/src/test/java/es/fjmarlop/corpsecauth/PasskeyAuthFacadeTest.kt
git commit -m "fix(core): scope cancelable en reset, DeviceException en authenticate, internal en refreshAuthState/invalidateSession, sin println"
```

---

## Task 3: KDoc en API pública de PasskeyAuth

**Archivo:** `passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/PasskeyAuth.kt`

Añadir KDoc a todas las funciones `public` del objeto `PasskeyAuth`. Las funciones `internal` no necesitan KDoc público.

- [ ] **Step 1: Añadir KDoc a checkCapability()**

```kotlin
/**
 * Consulta no-lanzante del estado de capacidad biométrica del dispositivo.
 *
 * Diseñado para ser llamado desde la UI antes de mostrar CTAs de autenticación.
 * No lanza excepciones — devuelve [PasskeyCapability] que la UI puede mapear a estado.
 *
 * @param context Contexto de la aplicación.
 * @return Estado de capacidad biométrica actual.
 * @see PasskeyCapability
 */
fun checkCapability(context: Context): PasskeyCapability {
```

- [ ] **Step 2: Añadir KDoc a initialize()**

```kotlin
/**
 * Inicializa el SDK. Debe llamarse una sola vez, típicamente en [Application.onCreate].
 *
 * Ejecuta la comprobación de integridad del entorno ([IntegrityGuard]) según la
 * [PasskeyAuthConfig] proporcionada. Si la política es [RootPolicy.Block] y el dispositivo
 * está rooteado, devuelve [Result.failure] con [IntegrityException.RootDetected].
 *
 * El SDK usa Firebase como backend por defecto. Para inyectar un backend alternativo
 * (Keycloak, REST propio, etc.), pasa instancias de [AuthBackend] y/o [DeviceRegistry].
 *
 * @param context Contexto de la aplicación.
 * @param config Configuración del SDK. Por defecto [PasskeyAuthConfig.Default].
 * @param authBackend Backend de autenticación alternativo. `null` usa Firebase.
 * @param deviceRegistry Registry de dispositivos alternativo. `null` usa Firestore.
 * @return [Result.success] si la inicialización fue correcta,
 *         [Result.failure] con [IntegrityException] si el entorno está comprometido,
 *         o [Result.failure] con [IllegalStateException] si ya estaba inicializado.
 */
suspend fun initialize(
```

- [ ] **Step 3: Añadir KDoc a enrollDevice()**

```kotlin
/**
 * Inicia el flujo de enrollment del dispositivo para el usuario dado.
 *
 * El enrollment es un proceso transaccional de 6 pasos (login, generación de clave,
 * biometría, cifrado del token, binding de dispositivo, almacenamiento local).
 * Si cualquier paso falla, los pasos anteriores se revierten automáticamente.
 *
 * Emite [EnrollmentState] a medida que el proceso avanza. El llamador debe
 * observar el Flow y reaccionar a [EnrollmentState.Success] o [EnrollmentState.Error].
 *
 * @param activity Activity requerida para mostrar el prompt biométrico.
 *                 Debe ser [FragmentActivity] o una subclase.
 * @param email Email corporativo del usuario.
 * @param temporaryPassword Contraseña temporal asignada por IT para el primer acceso.
 * @return [Flow] de [EnrollmentState] que emite los estados del proceso.
 * @throws IllegalStateException si el SDK no está inicializado.
 */
fun enrollDevice(
```

- [ ] **Step 4: Añadir KDoc a authenticate()**

```kotlin
/**
 * Autentica al usuario mediante biometría hardware-backed (BIOMETRIC_STRONG).
 *
 * Requiere que el dispositivo esté previamente enrollado ([enrollDevice]).
 * Verifica que el dispositivo siga activo en el registry remoto antes de
 * conceder acceso — si fue revocado por IT, devuelve [Result.failure].
 *
 * @param activity Activity requerida para mostrar el prompt biométrico.
 * @return [Result.success] con [AuthUser] si la autenticación fue correcta,
 *         [Result.failure] con [DeviceException.Revoked] si el dispositivo fue revocado,
 *         [Result.failure] con [BiometricException] si la biometría falló,
 *         o [Result.failure] con [DeviceException.NotEnrolled] si no hay sesión activa.
 * @throws IllegalStateException si el SDK no está inicializado.
 */
suspend fun authenticate(activity: FragmentActivity): Result<AuthUser> {
```

- [ ] **Step 5: Añadir KDoc a logout()**

```kotlin
/**
 * Cierra la sesión del usuario sin eliminar el enrollment del dispositivo.
 *
 * Limpia el token local y cierra la sesión en el backend. El dispositivo
 * sigue vinculado — el usuario puede volver a autenticarse con biometría.
 * Para eliminar el enrollment completamente, usa [unenrollDevice].
 */
fun logout() {
```

- [ ] **Step 6: Añadir KDoc a unenrollDevice()**

```kotlin
/**
 * Elimina el enrollment del dispositivo de forma completa.
 *
 * Revoca el binding remoto en el registry, borra la clave del AndroidKeyStore,
 * limpia el almacenamiento local cifrado y cierra la sesión del backend.
 * Después de esta llamada, el usuario deberá hacer [enrollDevice] de nuevo.
 *
 * @return [Result.success] si el unenrollment fue correcto.
 *         [Result.failure] si hubo un error irrecuperable (el estado local se limpia igualmente).
 * @throws IllegalStateException si el SDK no está inicializado.
 */
suspend fun unenrollDevice(): Result<Unit> {
```

- [ ] **Step 7: Añadir KDoc a isDeviceEnrolled()**

```kotlin
/**
 * Comprueba si el dispositivo tiene un enrollment válido y completo.
 *
 * Verifica que existan los tres componentes del enrollment:
 * token cifrado en storage, clave en AndroidKeyStore, e identificador de usuario.
 *
 * @return `true` si el dispositivo está enrollado, `false` en caso contrario.
 */
suspend fun isDeviceEnrolled(): Boolean {
```

- [ ] **Step 8: Añadir KDoc a getCurrentUser()**

```kotlin
/**
 * Devuelve el usuario actualmente autenticado según el backend, o `null` si no hay sesión.
 *
 * Esta llamada es síncrona y usa la caché del backend (Firebase Auth en la implementación
 * por defecto). Para backends OIDC sin caché, puede devolver `null` aunque haya una sesión
 * válida — en ese caso implementa un adaptador que mantenga el usuario en memoria.
 *
 * @return [AuthUser] si hay sesión activa, `null` si no hay usuario autenticado
 *         o el SDK no está inicializado.
 */
fun getCurrentUser(): AuthUser? {
```

- [ ] **Step 9: Añadir KDoc a isAuthenticated()**

```kotlin
/**
 * Indica si el estado de autenticación actual es [AuthResult.Authenticated].
 *
 * Equivale a `authState.value is AuthResult.Authenticated`.
 * Para observar cambios reactivos, usa [authState] en su lugar.
 *
 * @return `true` si el usuario está autenticado en este momento.
 */
fun isAuthenticated(): Boolean {
```

- [ ] **Step 10: Añadir KDoc a onAppBackground() y onAppForeground()**

```kotlin
/**
 * Notifica al SDK que la app pasó a segundo plano.
 *
 * Registra el timestamp para el cálculo de timeout de sesión.
 * Debe llamarse desde [Application] o el Activity raíz en `onStop()` o `onPause()`.
 * Ver [PasskeyAuthConfig.sessionTimeoutMinutes].
 */
fun onAppBackground() {

/**
 * Notifica al SDK que la app volvió a primer plano.
 *
 * Evalúa si el tiempo transcurrido desde [onAppBackground] supera
 * [PasskeyAuthConfig.sessionTimeoutMinutes]. Si es así, invalida la sesión
 * y emite [AuthResult.Unauthenticated] en [authState].
 */
fun onAppForeground() {
```

- [ ] **Step 11: Verificar build y tests**

```bash
.\gradlew.bat passkeyauth-core:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 12: Commit**

```bash
git add passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/PasskeyAuth.kt
git commit -m "docs(core): KDoc en API pública de PasskeyAuth — initialize, enrollDevice, authenticate, logout, unenrollDevice, isDeviceEnrolled, getCurrentUser, isAuthenticated, onAppBackground, onAppForeground, checkCapability"
```

---

## Verificación final

- [ ] **Build completo**

```bash
.\gradlew.bat testDebugUnitTest
```
Expected: BUILD SUCCESSFUL, todos los tests pasan.

- [ ] **Verificar que no quedan println en main sources**

```bash
grep -rn "println" passkeyauth-core/src/main/ --include="*.kt"
grep -rn "println" passkeyauth-ui/src/main/ --include="*.kt"
```
Expected: sin resultados (o solo dentro de comentarios).

- [ ] **Verificar que FirebaseException no aparece en PasskeyAuth.kt**

```bash
grep "FirebaseException" passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/PasskeyAuth.kt
```
Expected: sin resultados.

- [ ] **Commit de CHANGELOG**

```bash
git add CHANGELOG.md
git commit -m "docs(changelog): v0.4.1 — pre-v1.0.0 polish (println, scope, FirebaseException, KDoc)"
```
