# PasskeyAuth SDK for Android

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://android-arsenal.com/api?level=26)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-blue.svg)](https://kotlinlang.org)
[![Tests](https://img.shields.io/badge/Tests-24%20JVM%2FRobolectric%20%2B%208%20instrumented-brightgreen.svg)](docs/adr/011-testing-stack-and-strategy.md)

Librería Android para autenticación sin contraseñas usando biometría hardware-backed y Firebase, diseñada para entornos enterprise con modelo "1 user = 1 device".

## 🎯 Estado del Proyecto

**Versión actual:** 0.2.0-alpha

### ✅ Completado
- [x] Arquitectura multi-módulo con interfaces internas para testabilidad
- [x] Core SDK completo
- [x] PasskeyAuth API pública (estable)
- [x] Passwordless REAL (sin password de usuario)
- [x] Session timeout configurable
- [x] Sample app funcional
- [x] **Suite de tests automatizados:** 24 unit/Robolectric + 8 instrumented por device (matriz StrongBox + TEE validada en hardware real)
- [x] **Manual smoke test checklist** para release (10 escenarios E2E con BiometricPrompt)
- [x] 11 ADRs documentados
- [x] Documentación exhaustiva

### 🚧 En Desarrollo
- [ ] Tests con Firebase emulator suite (`FirebaseAuthBackend`, `FirestoreDeviceRegistry`)
- [ ] Tests del facade `PasskeyAuth` y métodos auxiliares de `EnrollmentManager`
- [ ] Security hardening (root detection, etc)
- [ ] Maven Central publishing

---

## 🏗️ Arquitectura
```
PasskeyAuth/
├── passkeyauth-core/      # Lógica de autenticación (sin UI)
├── passkeyauth-ui/        # Componentes Compose (futuro)
└── sample/                # App de demostración
```

### Módulo Core (passkeyauth-core)

SDK sin dependencias de UI. Características principales:

**🔐 Seguridad:**
- AES-256-GCM con autenticación
- Claves hardware-backed (StrongBox → TEE fallback)
- BiometricPrompt con STRONG biometrics
- Device binding con Firestore
- Session timeout configurable

**🔄 Flujo de Autenticación:**
1. **Enrollment** (primera vez): Email + password temporal → Biometría → Device binding
2. **Login** (subsecuente): Solo biometría
3. **Session Management**: Timeout configurable, invalidación automática

**📦 Componentes:**

| Componente público | Tipo | Descripción |
|---|---|---|
| `PasskeyAuth` | facade `object` | API pública del SDK |
| `EnrollmentManager` | orquestador | Enrollment transaccional de 7 pasos con rollback explícito |
| `BiometricAuthenticator` | interfaz `internal` | Wrapper de BiometricPrompt (impl `AndroidBiometricAuthenticator`) |
| `KeyStoreManager` | interfaz `internal` | Gestión de claves AES (impl `AndroidKeyStoreManager`) |
| `AuthBackend` + `PasswordManagementBackend` | interfaces `internal` | Backend de autenticación (impl `FirebaseAuthBackend`) |
| `DeviceRegistry` | interfaz `internal` | Registry de dispositivos (impl `FirestoreDeviceRegistry`) |
| `CryptoProvider` | clase `internal` | Operaciones de cifrado |
| `SecureStorage` | clase `internal` | DataStore Preferences |

**Diseño:** las fronteras de plataforma (BiometricPrompt, AndroidKeyStore, Firebase) están abstraídas tras interfaces `internal` para permitir testing JVM sin hardware ni Firebase. Ver [ADR-010](docs/adr/010-internal-abstractions-for-testability.md). Backend-agnóstico: la separación `AuthBackend` ↔ `FirebaseAuthBackend` deja preparado el SDK para soportar otros backends (Keycloak, Auth0, custom) en el futuro sin breaking changes.

---

## 📋 Requisitos

### Software
- **Min SDK:** API 26 (Android 8.0)
- **Target SDK:** API 35 (Android 15)
- **Kotlin:** 2.2.10
- **AGP:** 9.0.0+
- **JDK:** 17

### Hardware
- Sensor biométrico (huella, face, iris)
- Android KeyStore con TEE (StrongBox recomendado)

### ⚠️ Requisito Crítico: FragmentActivity

Tu `MainActivity` **DEBE** extender `FragmentActivity`:
```kotlin
import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity() {  // ✅ CORRECTO
    // Tu código
}

// ❌ INCORRECTO:
class MainActivity : ComponentActivity()  // No funciona
```

**Por qué:** BiometricPrompt requiere FragmentActivity internamente.

---

## 🚀 Instalación

> **Nota:** SDK aún no publicado en Maven Central

### Para Desarrollo Local
```bash
git clone https://github.com/fjmarlop/PasskeyAuth.git
cd PasskeyAuth
./gradlew publishToMavenLocal
```

### Instalación Futura (v1.0+)
```gradle
dependencies {
    implementation("es.fjmarlop.passkeyauth:core:0.2.0")
}
```

---

## 💻 Uso Básico

### 1. Inicialización
```kotlin
import androidx.fragment.app.FragmentActivity
import es.fjmarlop.corpsecauth.PasskeyAuth
import es.fjmarlop.corpsecauth.PasskeyAuthConfig

class MainActivity : FragmentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        lifecycleScope.launch {
            PasskeyAuth.initialize(
                context = applicationContext,
                config = PasskeyAuthConfig.Custom(
                    sessionTimeoutMinutes = 5  // Timeout configurable
                )
            )
        }
    }
}
```

### 2. Implementar Lifecycle Hooks (CRÍTICO)
```kotlin
class MainActivity : FragmentActivity() {
    
    override fun onStart() {
        super.onStart()
        
        // Verificar timeout cuando app vuelve a primer plano
        if (!isChangingConfigurations) {
            PasskeyAuth.onAppForeground()
        }
    }
    
    override fun onStop() {
        super.onStop()
        
        // Guardar timestamp cuando app va a segundo plano
        if (!isChangingConfigurations) {
            PasskeyAuth.onAppBackground()
        }
    }
}
```

### 3. Enrollment (Primera Vez)
```kotlin
import es.fjmarlop.corpsecauth.PasskeyAuth
import es.fjmarlop.corpsecauth.core.models.EnrollmentState

lifecycleScope.launch {
    PasskeyAuth.enrollDevice(
        activity = this@MainActivity,
        email = "empleado@empresa.com",
        temporaryPassword = "TempPass123"  // Será invalidada automáticamente
    ).collect { state ->
        when (state) {
            is EnrollmentState.ValidatingCredentials -> {
                showProgress("Validando credenciales...")
            }
            is EnrollmentState.AwaitingBiometric -> {
                showProgress("Registra tu huella...")
            }
            is EnrollmentState.Success -> {
                navigateToHome()  // Usuario enrollado y autenticado
            }
            is EnrollmentState.Error -> {
                showError(state.exception.message)
            }
            else -> { /* Otros estados */ }
        }
    }
}
```

### 4. Login (Subsecuente)
```kotlin
lifecycleScope.launch {
    PasskeyAuth.authenticate(activity = this@MainActivity)
        .onSuccess { user ->
            navigateToHome()
        }
        .onFailure { error ->
            showError(error.message)
        }
}
```

### 5. Verificar Estado
```kotlin
// Verificar si dispositivo está enrollado
suspend fun checkEnrollment() {
    if (PasskeyAuth.isDeviceEnrolled()) {
        navigateToLogin()  // Pide biometría
    } else {
        navigateToEnrollment()  // Primera vez
    }
}

// Verificar si usuario está autenticado
fun checkAuth() {
    if (PasskeyAuth.isAuthenticated()) {
        // Usuario tiene sesión activa
    }
}
```

### 6. Logout
```kotlin
PasskeyAuth.logout()  // Cierra sesión, mantiene enrollment

// O eliminar enrollment completo:
lifecycleScope.launch {
    PasskeyAuth.unenrollDevice()  // Borra claves y datos
}
```

---

## 🔒 Seguridad - LECTURA OBLIGATORIA

### ⚠️ Conceptos Críticos

**`isDeviceEnrolled()` vs `isAuthenticated()`**
```kotlin
// ❌ INSEGURO - Solo verifica si existen claves
fun isDeviceEnrolled(): Boolean
// No verifica biometría reciente

// ✅ SEGURO - Verifica biometría validada
fun isAuthenticated(): Boolean
// Verifica dentro del timeout de sesión
```

### 🚨 Errores Comunes (NO HACER ESTO)
```kotlin
// ❌ ERROR #1: Saltar directamente a Home
@Composable
fun SplashScreen() {
    if (PasskeyAuth.isDeviceEnrolled()) {
        navigateToHome()  // ❌ CRÍTICO: Sin verificación biométrica
    }
}

// ❌ ERROR #2: No implementar lifecycle hooks
class MainActivity : FragmentActivity() {
    // ❌ Falta onStart() y onStop()
    // Resultado: Timeout no funciona, sesión permanece activa
}
```

### ✅ Implementación Segura
```kotlin
// ✅ CORRECTO: Siempre requerir biometría
@Composable
fun SplashScreen() {
    LaunchedEffect(Unit) {
        when {
            !PasskeyAuth.isDeviceEnrolled() -> navigateToEnrollment()
            else -> navigateToLogin()  // SIEMPRE pide biometría
        }
    }
}

// ✅ CORRECTO: Implementar lifecycle hooks
class MainActivity : FragmentActivity() {
    override fun onStart() {
        super.onStart()
        if (!isChangingConfigurations) {
            PasskeyAuth.onAppForeground()  // Verifica timeout
        }
    }
    
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            PasskeyAuth.onAppBackground()  // Guarda timestamp
        }
    }
}
```

### 📖 Documentación Completa

**Lee la [Guía de Seguridad Completa](SECURITY.md) antes de producción.**

### Tabla de Referencia Rápida

| Método | Propósito | Nivel de Seguridad |
|--------|-----------|-------------------|
| `isDeviceEnrolled()` | Verifica si existen claves | ⚠️ Bajo - Sin verificación activa |
| `isAuthenticated()` | Verifica sesión activa | ✅ Alto - Dentro de timeout |
| `authenticate()` | Dispara prompt biométrico | ✅ Alto - Verificación inmediata |

---

## ⚙️ Configuración

### Session Timeout
```kotlin
// Baja seguridad (app personal)
PasskeyAuthConfig.Custom(
    sessionTimeoutMinutes = 15
)

// Media seguridad (app corporativa)
PasskeyAuthConfig.Default  // 5 minutos

// Alta seguridad (banca/salud)
PasskeyAuthConfig.Custom(
    sessionTimeoutMinutes = 0  // Siempre requiere biometría
)

// Testing (sin timeout)
PasskeyAuthConfig.Custom(
    sessionTimeoutMinutes = -1  // Solo para desarrollo
)
```

---

## 📚 Documentación

- **[Guía de Seguridad](SECURITY.md)** — Mejores prácticas y checklist
- **[ADRs](docs/adr/)** — 11 decisiones arquitectónicas documentadas, incluyendo:
  - [ADR-004](docs/adr/004-keystoremanager-aes-gcm.md) AES-256-GCM y StrongBox optional
  - [ADR-006](docs/adr/006-enrollmentmanager-transactional.md) EnrollmentManager transaccional
  - [ADR-009](docs/adr/009-client-side-security-responsibility.md) Responsabilidad del cliente
  - [ADR-010](docs/adr/010-internal-abstractions-for-testability.md) Interfaces internas para testabilidad y backend-independence
  - [ADR-011](docs/adr/011-testing-stack-and-strategy.md) Stack de testing y estrategia
- **[Manual Smoke Test](docs/MANUAL-SMOKE-TEST.md)** — Checklist de release (10 escenarios E2E con BiometricPrompt real)
- **[CHANGELOG](CHANGELOG.md)** — Historial de versiones
- **[DEVELOPMENT](DEVELOPMENT.md)** — Guía para desarrolladores

---

## 🛠️ Desarrollo

### Build
```bash
# Windows
.\gradlew.bat build

# Linux/macOS
./gradlew build
```

### Sample App
```bash
.\gradlew.bat :sample:installDebug
```

### Tests

El SDK tiene una pirámide de tests con tres niveles diferenciados según lo que cada uno puede validar de forma fiable. Estrategia documentada en [ADR-011](docs/adr/011-testing-stack-and-strategy.md).

```bash
# 1) Tests JVM + Robolectric (rápidos, <12s) — orquestador, fakes, SecureStorage
.\gradlew.bat passkeyauth-core:testDebugUnitTest

# 2) Tests instrumented en device físico — AndroidKeyStore real, StrongBox/TEE
.\gradlew.bat passkeyauth-core:connectedDebugAndroidTest

# 3) Reporte de cobertura JaCoCo
.\gradlew.bat passkeyauth-core:jacocoTestReport
# Reportes en passkeyauth-core/build/reports/jacoco/jacocoTestReport/
```

**Cobertura actual:**

| Nivel | Qué valida | Tests |
|---|---|---|
| JVM puro | Orquestador transaccional, mappers de errores, fakes | 12 (`EnrollmentManager` happy path + matriz completa de rollback para los 6 pasos) |
| Robolectric | `SecureStorage` + DataStore con `Context` real | 12 |
| Instrumented (device físico) | `AndroidKeyStoreManager` real con StrongBox vs TEE | 8-9 por device (matriz **ADR-004 validada al 100%** con device A con StrongBox + device B sin StrongBox) |

**Smoke test manual antes de release:** ver [`docs/MANUAL-SMOKE-TEST.md`](docs/MANUAL-SMOKE-TEST.md) — 10 escenarios E2E con BiometricPrompt real que los tests automatizados no pueden cubrir (interacción física con el sensor biométrico, cambio de huella, lockout, revocación remota, etc.).

---

## 🗺️ Roadmap

- [x] **v0.1.0** — Core SDK + Arquitectura
- [x] **v0.2.0** — Passwordless real + Session timeout + Fixes
- [x] **v0.2.x** — Testing foundation: interfaces internas, fakes, suite de 24 tests JVM/Robolectric + matriz instrumented ADR-004, manual smoke test checklist
- [ ] **v0.3.0** — Tests Firebase emulator + facade `PasskeyAuth` + security hardening (root detection, etc)
- [ ] **v0.4.0** — Backend-agnostic: segundo backend de referencia (Keycloak o custom) usando las interfaces `AuthBackend` / `DeviceRegistry`
- [ ] **v1.0.0** — Maven Central + Producción ready

---

## 📊 Estadísticas

**Versión 0.2.0-alpha:**
- ADRs documentados: 11
- Tests automatizados verdes: 24 JVM/Robolectric + 8–9 instrumented por device físico
- Matriz hardware validada: device con StrongBox + device con TEE only
- Bugs latentes detectados por los tests y resueltos: 2 (rollback paso 5, comentario engañoso en `EnrollmentManager`)
- Cleanup técnico aplicado: migración `android.util.Base64` → `java.util.Base64` (portabilidad JVM)

---

## 🤝 Contribuciones

Contribuciones serán bienvenidas después de v1.0. Por ahora el proyecto está en desarrollo activo.

---

## 📄 Licencia
```
Copyright 2026 Francisco Javier Marmolejo López

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

---

## 👤 Autor

**Francisco Javier Marmolejo López**
- GitHub: [@fjmarlop](https://github.com/fjmarlop)

---

⚠️ **Advertencia:** SDK en desarrollo activo. No usar en producción hasta v1.0.0.