# PasskeyAuth SDK for Android

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://android-arsenal.com/api?level=26)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-blue.svg)](https://kotlinlang.org)
[![Tests](https://img.shields.io/badge/Tests-135%20JVM%2FRobolectric%20%2B%2012%20lint%20%2B%209%20instrumented-brightgreen.svg)](docs/adr/011-testing-stack-and-strategy.md)
[![CI](https://github.com/fjmarlop/PassKeyAuth/actions/workflows/ci.yml/badge.svg)](https://github.com/fjmarlop/PassKeyAuth/actions/workflows/ci.yml)

Librería Android para autenticación sin contraseñas usando biometría hardware-backed, diseñada para entornos enterprise con modelo "1 user = 1 device". Backend-agnóstica: incluye Firebase como implementación de referencia y permite inyectar un backend propio (Keycloak, OIDC, REST).

## 🎯 Estado del Proyecto

**Versión actual:** 0.4.1 (pre-v1.0.0)

### ✅ Completado
- [x] Arquitectura multi-módulo con interfaces internas para testabilidad
- [x] Core SDK completo
- [x] PasskeyAuth API pública (estable, con KDoc)
- [x] Passwordless REAL (sin password de usuario) — *paso de invalidación deshabilitado en dev, ver nota*
- [x] Session timeout configurable
- [x] **Módulo `passkeyauth-ui`:** composables primitivos (`PasskeySignInScreen`, `PasskeyEnrollScreen`) + launcher híbrido (`PasskeyAuthContract`) + theming zero-config
- [x] **Sample app refactorizada:** wiring directo al SDK UI, diseño visual minimalista, edge-to-edge (API 35)
- [x] **Security hardening (integridad + privacidad):** root/emulator/hooking detection, anti-debug, key attestation, tapjacking guard, FLAG_SECURE, privacy overlay, memory zeroing (ADR-015)
- [x] **Backend-agnóstico:** `AuthBackend` / `DeviceRegistry` / `PasswordManagementBackend` públicos e inyectables en `initialize()`; Firebase como default (ADR-016)
- [x] **SDK silencioso por defecto:** sin `println` a stdout (v0.4.1)
- [x] **Suite de tests automatizados completa:** 135 JVM/Robolectric (core + UI Compose Robolectric) + 12 lint rules + 9 instrumented (StrongBox + TEE)
- [x] **GitHub Actions CI:** `.github/workflows/ci.yml` — verde en cada push/PR
- [x] **Manual smoke test checklist** para release (10 escenarios E2E con BiometricPrompt)
- [x] 16 ADRs documentados
- [x] Documentación exhaustiva

### 🚧 Camino a v1.0.0
- [ ] Reactivar paso 2 del enrollment (invalidación de password temporal — passwordless real en producción)
- [ ] Publicación en Maven Central (POM, firma GPG, sources/javadoc JAR, LICENSE)

---

## 🏗️ Arquitectura
```
PasskeyAuth/
├── passkeyauth-core/      # Lógica de autenticación (sin UI)
├── passkeyauth-ui/        # Componentes Compose — PasskeySignInScreen, PasskeyEnrollScreen, PasskeyAuthContract
└── sample/                # App de demostración (wiring directo al SDK UI)
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

| Componente | Tipo | Descripción |
|---|---|---|
| `PasskeyAuth` | facade `object` público | API pública del SDK |
| `AuthBackend` + `PasswordManagementBackend` | interfaces **públicas** | Contrato de autenticación inyectable (impl default `FirebaseAuthBackend`, `internal`) |
| `DeviceRegistry` | interfaz **pública** | Contrato de registry de dispositivos inyectable (impl default `FirestoreDeviceRegistry`, `internal`) |
| `Credentials` + `AuthSession` | tipos **públicos** | Modelos necesarios para implementar `AuthBackend` |
| `EnrollmentManager` | orquestador `internal` | Enrollment transaccional con rollback explícito (6 pasos activos; el paso 2 de invalidación de password está deshabilitado en dev) |
| `BiometricAuthenticator` | interfaz `internal` | Wrapper de BiometricPrompt (impl `AndroidBiometricAuthenticator`) |
| `KeyStoreManager` | interfaz `internal` | Gestión de claves AES (impl `AndroidKeyStoreManager`) |
| `CryptoProvider` | clase `internal` | Operaciones de cifrado |
| `SecureStorage` | clase `internal` | DataStore Preferences |

**Diseño:** las fronteras de plataforma (BiometricPrompt, AndroidKeyStore) están abstraídas tras interfaces `internal` para permitir testing JVM sin hardware. Ver [ADR-010](docs/adr/010-internal-abstractions-for-testability.md). **Backend-agnóstico (ADR-016):** `AuthBackend`, `DeviceRegistry` y `PasswordManagementBackend` son interfaces **públicas** que el integrador puede implementar (Keycloak, Auth0, REST propio) e inyectar en `PasskeyAuth.initialize()`. Firebase es la implementación de referencia incluida y el default si no se inyecta nada — cero configuración adicional.

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
    implementation("es.fjmarlop.passkeyauth:core:1.0.0")
    // Opcional — componentes Compose:
    implementation("es.fjmarlop.passkeyauth:ui:1.0.0")
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
                // authBackend / deviceRegistry opcionales — sin ellos usa Firebase (ver § Backend personalizado)
            )
        }
    }
}
```

> **Backend personalizado (opcional):** para usar Keycloak, OIDC u otro IdP en lugar de Firebase,
> implementa las interfaces `AuthBackend` y `DeviceRegistry` e inyéctalas:
> ```kotlin
> PasskeyAuth.initialize(
>     context = applicationContext,
>     authBackend = MiKeycloakBackend(),       // implementa es.fjmarlop.corpsecauth.AuthBackend
>     deviceRegistry = MiDeviceRegistry()       // implementa es.fjmarlop.corpsecauth.DeviceRegistry
> )
> ```
> Si no se inyectan, Firebase es el default sin configuración adicional. Ver [ADR-016](docs/adr/016-backend-agnostic-sdk.md).

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
- **[ADRs](docs/adr/)** — 16 decisiones arquitectónicas documentadas, incluyendo:
  - [ADR-004](docs/adr/004-keystoremanager-aes-gcm.md) AES-256-GCM y StrongBox optional
  - [ADR-006](docs/adr/006-enrollmentmanager-transactional.md) EnrollmentManager transaccional
  - [ADR-009](docs/adr/009-client-side-security-responsibility.md) Responsabilidad del cliente
  - [ADR-010](docs/adr/010-internal-abstractions-for-testability.md) Interfaces internas para testabilidad y backend-independence
  - [ADR-011](docs/adr/011-testing-stack-and-strategy.md) Stack de testing y estrategia
  - [ADR-013](docs/adr/013-non-negotiable-security-invariants.md) Invariantes de seguridad no negociables + contrato `PasskeyAuthConfig`
  - [ADR-014](docs/adr/014-ui-module-hybrid-integration.md) Módulo UI: integración híbrida, theming y estados
  - [ADR-015](docs/adr/015-runtime-integrity-and-privacy-hardening.md) Runtime integrity hardening + privacidad
  - [ADR-016](docs/adr/016-backend-agnostic-sdk.md) SDK backend-agnóstico — inyección de `AuthBackend`/`DeviceRegistry`
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

El SDK tiene una pirámide de tests con cuatro niveles. Estrategia documentada en [ADR-011](docs/adr/011-testing-stack-and-strategy.md).

```bash
# 1) JVM + Robolectric (131 tests core, <30s) — CI los ejecuta en cada push
.\gradlew.bat :passkeyauth-core:testDebugUnitTest

# 1b) UI Compose Robolectric (4 tests)
.\gradlew.bat :passkeyauth-ui:testDebugUnitTest

# 2) Lint rules (12 tests)
.\gradlew.bat :passkeyauth-lint:test

# 3) Instrumented en device físico — AndroidKeyStore real, StrongBox/TEE
.\gradlew.bat :passkeyauth-core:connectedDebugAndroidTest

# 4) Reporte de cobertura JaCoCo
.\gradlew.bat :passkeyauth-core:jacocoTestReport
# Reportes en passkeyauth-core/build/reports/jacoco/jacocoTestReport/
```

**Los pasos 1 y 2 se ejecutan automáticamente en GitHub Actions** (ver [`.github/workflows/ci.yml`](.github/workflows/ci.yml)) en cada push y cada PR a `main`.

**Pirámide de tests:**

| Nivel | Qué valida | Tests |
|---|---|---|
| JVM puro | `EnrollmentManager` (orquestación transaccional, rollback por paso, helpers), `CryptoProvider`, `KeyAttestationVerifier`, fakes | ~35 |
| JVM seguridad | `RootDetector`, `EmulatorDetector`, `HookDetector`, `IntegrityGuard` (señales inyectadas, decisión pura) | 32 |
| Robolectric | `SecureStorage` + DataStore; adaptadores Firebase (`FirebaseAuthBackend`, `FirestoreDeviceRegistry`); facade `PasskeyAuth` + inyección de backend; `PasskeyAuthConfig` | ~64 |
| UI Compose Robolectric | `PasskeySignInScaffold` (CTA por estado, escape hatch), tapjacking | 4 |
| Lint rules | Contratos del SDK: `FragmentActivity`, anti-pattern SplashScreen, lifecycle hooks obligatorios | 12 (L1/L2/L3) |
| Instrumented (device físico) | `AndroidKeyStoreManager` real con StrongBox vs TEE | 9 por device (matriz **ADR-004 validada al 100%**) |

**Total automatizado en CI:** 147 tests (131 core + 4 UI JVM/Robolectric + 12 lint).

**Smoke test manual antes de release:** ver [`docs/MANUAL-SMOKE-TEST.md`](docs/MANUAL-SMOKE-TEST.md) — 10 escenarios E2E con BiometricPrompt real que los tests automatizados no pueden cubrir (sensor biométrico, cambio de huella, lockout, revocación remota, etc.).

---

## 🗺️ Roadmap

- [x] **v0.1.0** — Core SDK + Arquitectura
- [x] **v0.2.0** — Passwordless real + Session timeout + Fixes
- [x] **v0.2.x** — Testing foundation completa: JVM/Robolectric + 12 lint + instrumented · CI GitHub Actions · Facade `PasskeyAuth` testeado · Firebase adapters MockK · ADR-004 matriz validada en hardware
- [x] **v0.3.0** — Módulo `passkeyauth-ui`: integración híbrida (composables + launcher), theming zero-config, `checkCapability()` + fusión de `PasskeyAuthConfig` (ADR-013/ADR-014). Security hardening: root/emulator/hooking detection, anti-debug, key attestation, tapjacking guard, FLAG_SECURE, privacy overlay (ADR-015)
- [x] **v0.4.0** — Backend-agnóstico: `AuthBackend` / `DeviceRegistry` / `PasswordManagementBackend` públicos e inyectables; Firebase como default (ADR-016)
- [x] **v0.4.1** — Pre-v1.0.0 polish: SDK silencioso (sin `println`), thread safety en `scope`, API reducida (`internal`), KDoc en API pública
- [ ] **v1.0.0** — Reactivar passwordless real + Maven Central (POM, firma GPG, LICENSE) + Producción ready

---

## 📊 Estadísticas

**Versión 0.4.1 (pre-v1.0.0):**
- ADRs documentados: 16
- Tests automatizados verdes en CI: 131 core + 4 UI JVM/Robolectric + 12 lint = **147 automáticos**
- Tests instrumented en hardware: 9 por device (StrongBox + TEE)
- CI GitHub Actions: cancela runs obsoletos (`concurrency: cancel-in-progress`)
- Módulos: `passkeyauth-core`, `passkeyauth-ui`, `passkeyauth-lint`, `sample`
- SDK silencioso por defecto (sin `println` a stdout) y API pública documentada con KDoc

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