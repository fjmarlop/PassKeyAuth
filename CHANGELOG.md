# Changelog

Todos los cambios notables de PasskeyAuth SDK seran documentados aqui.

El formato esta basado en [Keep a Changelog](https://keepachangelog.com/es/1.0.0/),
y este proyecto adhiere a [Semantic Versioning](https://semver.org/lang/es/).

---

## [Unreleased] — v0.3.0

### 🛡️ Security Hardening — integridad del entorno y privacidad (ADR-015)

#### Core (`passkeyauth-core`)

- **`IntegrityGuard`** — comprobación de integridad en `PasskeyAuth.initialize()`. Falla el arranque (según política) si el entorno está comprometido
- **`RootDetector`** — binarios `su`/`busybox`, apps de root (Magisk, SuperSU…), `Build.TAGS=test-keys`
- **`HookDetector`** — artefactos de Frida, paquetes Xposed/LSPosed, clases `XposedBridge`
- **`EmulatorDetector`** — heurística sobre propiedades de `Build`
- **Anti-debug** — invariante en builds de release (depurador adjunto → fallo)
- **`IntegrityException`** sealed (`RootDetected` / `EmulatorDetected` / `HookingDetected` / `DebuggerAttached`)
- **`PasskeyAuthConfig` ampliado** — `rootPolicy: RootPolicy`, `emulatorPolicy: EmulatorPolicy`, `enablePrivacyOverlay: Boolean`
- **Memory zeroing** — el plaintext del token se borra del heap (`ByteArray.fill(0)`) tras cifrar (enrollment) y tras descifrar (login)
- Detectores con dependencias inyectables → lógica de decisión pura testeable en JVM (36 tests nuevos)

#### UI (`passkeyauth-ui`)

- **`FLAG_SECURE`** en `PasskeyAuthActivity` — bloquea screenshots, grabación y preview del app switcher (invariante)
- **`PrivacyOverlay`** — superficie opaca al pasar a segundo plano (`ON_PAUSE`), gobernada por `enablePrivacyOverlay`

#### Sample

- `FLAG_SECURE` en `MainActivity`; `allowBackup=false` + `fullBackupContent=false`
- `network_security_config.xml` — prohíbe tráfico en claro; plantilla de certificate pinning comentada
- Limpieza del portapapeles en `CredentialsScreen` al pasar a background

### ✨ Módulo `passkeyauth-ui` + Core foundations (ADR-013/014)

#### Core (`passkeyauth-core`)

- **`StrongBoxPolicy`** enum (`Preferred` / `Required`) — reemplaza `requireStrongBox: Boolean`
- **`RecoveryHandler`** fun interface — recuperación auditable server-side (null = sin recovery in-app)
- **`PasskeyCapability`** sealed interface (`Ready` / `NotEnrolled` / `NoHardware` / `TemporarilyUnavailable` / `SecurityUpdateRequired`)
- **`PasskeyAuth.checkCapability(context)`** — consulta no-lanzante del estado biométrico (la UI lee esto antes de mostrar CTAs)
- **`PasskeyAuthConfig` fusionado** — añade `allowHostFallback`, `strongBox`, `recovery`; conserva `sessionTimeoutMinutes`; `requireStrongBox` deprecado con compat property

#### UI (`passkeyauth-ui`) — nuevo módulo

- **`PasskeyAuthTheme`** — zero-config deriva de `MaterialTheme.colorScheme`; `CompositionLocal` para tokens
- **`PasskeyAuthColors`** / **`PasskeyAuthBranding`** — 5 tokens de color + slot `logo: Painter?` (nunca resource hardcodeado)
- **`PasskeyUiState`** — 6 estados (`Idle` / `Loading` / `Error` / `Success` / `NotEnrolled` / `NoHardware`) + factory `from(PasskeyCapability)`
- **`PasskeySignInScaffold`** — UI pura dirigida por estado con slots `header`/`footer` (escape hatch)
- **`PasskeySignInScreen`** — composable primitivo: cablea capability + ceremonia biométrica
- **`PasskeyEnrollScreen`** — composable primitivo: mapea `EnrollmentState` al scaffold
- **`PasskeyAuthContract`** + **`PasskeyAuthResult`** — launcher híbrido de una línea vía `ActivityResultContract`
- i18n: 11 strings sobrescribibles por el host en `strings.xml`

#### UI (`passkeyauth-ui`) — rediseño visual

- **`PasskeySignInScaffold`** rediseñado: layout con peso (icono centrado / CTA anclado al fondo), iconos Material Outlined por estado (`Fingerprint` / `Warning` / `TouchApp` / `Block`), botón full-width 56 dp con `RoundedCornerShape(16)`, tipografía `headlineSmall SemiBold`
- Escape hatch anti-bucle: en estado `Error` con `allowHostFallback = true`, muestra botón de fallback bajo "Reintentar"
- `enableEdgeToEdge()` en `PasskeyAuthActivity` + `safeDrawingPadding()` en el scaffold

#### Sample — refactor completo a SDK UI

- **`CredentialsScreen`** — pantalla de recogida de email + contraseña temporal antes del enrollment; prefilled para testing, nota de soporte visible
- Wiring directo: `Screen.Enrollment` → `PasskeyEnrollScreen`, `Screen.Login` → `PasskeySignInScreen` sin wrappers intermedios
- `AuthViewModel` reducido a `isDeviceEnrolled()` + `logout()`
- Eliminados: `LoginScreen`, `EnrollmentScreen`, `SdkSignInDemoScreen` (−324 líneas)
- `SplashScreen` y `HomeScreen` rediseñados sin emojis, iconos Material Outlined, layout con `weight`
- Logout navega a `CredentialsScreen` (no a `LoginScreen`) — evita el bucle de error cuando el device queda desenrolado
- Edge-to-edge: `enableEdgeToEdge()` en `MainActivity`, tema `AppCompat.DayNight.NoActionBar`, `adjustResize`, `safeDrawingPadding()` en todas las pantallas

---

## [0.2.1] - 2026-06-18

### ✅ Testing Suite Completa

Suite de tests automatizados completa para todo el SDK. CI integrado.

#### Tests añadidos

**`passkeyauth-core` — 73 tests JVM/Robolectric:**

| Clase de test | Nivel | Tests | Qué valida |
|---|---|---|---|
| `EnrollmentManagerHappyPathTest` | JVM | 1 | Flujo completo de enrollment (plantilla de oro) |
| `EnrollmentManagerRollbackTest` | JVM | 6 | Rollback automático en cada uno de los 6 pasos con fallos |
| `EnrollmentManagerHelpersTest` | JVM | 10 | `isDeviceEnrolled`, `unenrollDevice` y helpers auxiliares |
| `FakesSmokeTest` | JVM | 5 | Contratos de los fakes del SDK |
| `SecureStorageRobolectricTest` | Robolectric | 12 | DataStore cifrado con `Context` real |
| `FirebaseAuthBackendTest` | Robolectric | 12 | Adaptador Firebase Auth (callbacks sincrónicos con MockK) |
| `FirestoreDeviceRegistryTest` | Robolectric | 11 | Adaptador Firestore device registry (cadena fluida mockeada) |
| `PasskeyAuthFacadeTest` | Robolectric | 16 | Facade público: initialize, logout, session timeout, lifecycle |

**`passkeyauth-lint` — 12 tests (ya en v0.2.0, documentado aquí para completitud):**

| Regla | Tests | Qué valida |
|---|---|---|
| L1 `MissingFragmentActivity` | 4 | `FragmentActivity` obligatoria en `MainActivity` |
| L2 `SkipBiometricNavigation` | 4 | Anti-pattern de saltar biometría en Splash |
| L3 `MissingLifecycleHooks` | 4 | `onAppForeground`/`onAppBackground` obligatorios |

#### CI GitHub Actions

- Nuevo workflow `.github/workflows/ci.yml`
- Ejecuta 85 tests en cada push y PR a `main`
- Tiempo: ~1m 30s en `ubuntu-latest`
- `concurrency: cancel-in-progress: true` — cancela runs obsoletos

#### Fix técnico: `by lazy` no resettable en singleton

`PasskeyAuth` usaba `by lazy` para las 5 propiedades de infraestructura (`firebaseAuthBackend`, `keyStoreManager`, `cryptoProvider`, `secureStorage`, `deviceRegistry`). `SynchronizedLazyImpl` evalúa permanentemente y `reset()` no podía nulificar los delegates — los tests del facade reutilizaban instancias entre tests. Fix: patrón _backing field nullable + getter_ en todas las propiedades diferidas del singleton.

---

## [0.2.0] - 2026-01-23

### 🚨 BREAKING CHANGES

#### Enrollment API Simplificada - Passwordless Real
**Motivacion:** Transición a autenticación passwordless verdadera donde el usuario nunca conoce la contraseña final.

**Antes (v0.1.0):**
```kotlin
PasskeyAuth.enrollDevice(
    activity = activity,
    email = "user@empresa.com",
    temporaryPassword = "TempPass123",
    newPassword = "MiPassword456"  // Usuario elige contraseña
)
```

**Ahora (v0.2.0):**
```kotlin
PasskeyAuth.enrollDevice(
    activity = activity,
    email = "user@empresa.com",
    temporaryPassword = "TempPass123"
    // newPassword eliminado - se genera automáticamente
)
```

### ✨ Added

- **Passwordless Real:** Contraseña temporal se invalida automáticamente con password aleatoria de 32 caracteres
- **`FirebaseAuthManager.invalidateTemporaryPassword()`:** Genera y asigna password aleatoria fuerte
- **Generación Segura:** Usa `SecureRandom` con entropía ~200 bits
- **UI Simplificada:** EnrollmentScreen reducida de 4 campos a 2 campos
- **Mejor UX:** Mensaje informativo sobre invalidación automática

### 🔧 Changed

- **EnrollmentManager:** Paso 2 cambiado de `changePassword()` a `invalidateTemporaryPassword()`
- **EnrollmentScreen:** Eliminados campos `newPassword` y `confirmPassword`
- **AuthViewModel:** Método `enrollDevice()` sin parámetro `newPassword`
- **ADR-006:** Actualizado con decisión de passwordless real

### 🔐 Security

- **Mayor Seguridad:** Passwords aleatorias de 32 chars > passwords elegidas por usuarios
- **Prevención de Reutilización:** Usuario no puede reutilizar password en otros servicios
- **Eliminación de Vector de Ataque:** Password nunca almacenada localmente ni conocida por usuario

### 📝 Migration Guide v0.1 → v0.2

1. **Actualizar llamadas a `enrollDevice()`:**
   - Eliminar parámetro `newPassword`
   - Solo pasar `email` y `temporaryPassword`

2. **Actualizar UI de enrollment:**
   - Quitar campos de nueva contraseña
   - Agregar mensaje informativo

3. **Informar a usuarios:**
   - Password temporal será invalidada automáticamente
   - Solo necesitarán biometría para acceder

---

## [0.1.0-SNAPSHOT] - 2026-01-22

### ✨ Initial Release

**PasskeyAuth SDK v0.1.0** - Autenticación passwordless con biometría hardware-backed.

#### Core Components (21 archivos)

**Models (5):**
- `AuthResult` - Estados de autenticación
- `AuthUser` - Modelo de usuario
- `BiometricConfig` - Configuración biométrica
- `EnrollmentState` - Estados de enrollment
- `DeviceInfo` - Información del dispositivo

**Exceptions (6):**
- `PasskeyAuthException` - Base sealed class
- `BiometricException` - 8 tipos de errores biométricos
- `FirebaseException` - 6 tipos de errores Firebase
- `CryptoException` - 5 tipos de errores criptográficos
- `DeviceException` - 4 tipos de errores de dispositivo
- `EnrollmentException` - 3 tipos de errores de enrollment

**Crypto (3):**
- `KeyStoreManager` - Gestión de claves (StrongBox/TEE)
- `CryptoProvider` - Helper de cifrado AES-256-GCM
- `EncryptedData` - Modelo de datos cifrados

**Auth (1):**
- `BiometricAuthenticator` - Wrapper de BiometricPrompt

**Firebase (2):**
- `FirebaseAuthManager` - Autenticación Firebase
- `DeviceBindingManager` - Device registry (1 user = 1 device)

**Storage (1):**
- `SecureStorage` - Wrapper de DataStore cifrado

**Enrollment (1):**
- `EnrollmentManager` - Orquestador transaccional (7 pasos)

**Public API (2):**
- `PasskeyAuthConfig` - Configuración del SDK
- `PasskeyAuth` - Facade principal

#### Security Features

- ✅ AES-256-GCM encryption
- ✅ Hardware-backed keys (StrongBox with TEE fallback)
- ✅ Biometric authentication (Class 3 - STRONG)
- ✅ Device binding (1 user = 1 device)
- ✅ Automatic key invalidation on biometric changes
- ✅ Transactional enrollment with automatic rollback
- ✅ Encrypted local storage (DataStore)

#### Architecture

- ✅ Multi-module (core, ui, sample)
- ✅ Type-safe API with `Result<T>`
- ✅ Reactive with Flow/StateFlow
- ✅ Coroutines-first
- ✅ Compose-friendly
- ✅ Manual dependency injection

#### Documentation

- ✅ 6 ADRs (Architecture Decision Records)
- ✅ README completo
- ✅ DEVELOPMENT guide
- ✅ CHANGELOG
- ✅ Inline documentation (Spanish security comments, English KDoc)

#### Sample App

- ✅ 4 Compose screens (Splash, Enrollment, Login, Home)
- ✅ Complete enrollment flow demo
- ✅ Biometric login demo
- ✅ Material Design 3
- ✅ Reactive state management

#### Compliance

- ✅ OWASP MASVS Level 2
- ✅ NIST SP 800-63B AAL2
- ✅ Android CDD Biometric Class 3

#### Statistics

- 📊 21 Kotlin files
- 📊 ~5,500 lines of code
- 📊 6 ADRs documented
- 📊 API 26+ (Android 8.0+)
- 📊 Gradle 9.1.0 + Kotlin 2.1.0

#### Author

Francisco Javier Marmolejo López

---

[0.2.0]: https://github.com/user/corpsecauth/compare/v0.1.0...v0.2.0
[0.1.0-SNAPSHOT]: https://github.com/user/corpsecauth/releases/tag/v0.1.0