# Changelog

Todos los cambios notables de PasskeyAuth SDK seran documentados aqui.

El formato esta basado en [Keep a Changelog](https://keepachangelog.com/es/1.0.0/),
y este proyecto adhiere a [Semantic Versioning](https://semver.org/lang/es/).

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