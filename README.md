# PasskeyAuth SDK for Android

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://android-arsenal.com/api?level=26)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-blue.svg)](https://kotlinlang.org)

Libreria Android para autenticacion sin contrasenias usando biometria hardware-backed y Firebase, disenada para entornos enterprise.

## Estado del Proyecto

**Version actual:** 0.1.0-SNAPSHOT (en desarrollo activo)

- [x] Arquitectura multi-modulo configurada
- [x] Sistema de build funcionando
- [x] 19 archivos core implementados
- [x] 6 ADRs documentados
- [ ] PasskeyAuth facade (API publica) - En progreso
- [ ] Sample app funcional - Pendiente
- [ ] Tests unitarios - Pendiente

## Arquitectura
```
PasskeyAuth/
Ã¢â€Å“Ã¢â€â‚¬Ã¢â€â‚¬ passkeyauth-core/      # Logica de autenticacion (sin UI)
Ã¢â€Å“Ã¢â€â‚¬Ã¢â€â‚¬ passkeyauth-ui/        # Componentes Compose opcionales
Ã¢â€â€Ã¢â€â‚¬Ã¢â€â‚¬ sample/                # App de demostracion
```

### Modulo Core (passkeyauth-core)

Biblioteca Android sin dependencias de UI. Contiene:

**Crypto Layer:**
- KeyStoreManager (AES-256-GCM con StrongBox support)
- CryptoProvider (encrypt/decrypt helper)
- EncryptedData (modelo con Base64 support)

**Auth Layer:**
- BiometricAuthenticator (BiometricPrompt wrapper con suspend functions)

**Firebase Layer:**
- FirebaseAuthManager (login con credenciales temporales)
- DeviceBindingManager (device registry en Firestore)

**Storage Layer:**
- SecureStorage (DataStore wrapper para tokens cifrados)

**Enrollment Layer:**
- EnrollmentManager (orquestador transaccional de 7 pasos)

**Models:**
- AuthResult, AuthUser, BiometricConfig, EnrollmentState, DeviceInfo

**Exceptions:**
- Jerarquia completa de PasskeyAuthException

**Dependencias clave:**
- AndroidX Biometric 1.2.0-alpha05
- AndroidX Security Crypto 1.1.0-alpha06
- Firebase BOM 33.7.0
- Kotlin Coroutines 1.9.0
- DataStore Preferences 1.1.1

### Modulo UI (passkeyauth-ui)

Componentes Jetpack Compose configurables (en desarrollo):

- Pantallas de enrollment
- Dialogos de autenticacion
- Indicadores de loading
- Temas customizables

**Nota:** Este modulo es opcional. Puedes usar `passkeyauth-core` con tu propia UI.

## Requisitos

- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 35 (Android 15)
- **Kotlin:** 2.1.0+
- **Gradle:** 9.1.0+
- **Java:** 17

**Hardware necesario:**
- Biometric authentication capability (fingerprint, face, iris)
- Android Keystore (TEE minimo, StrongBox recomendado)

## Instalacion

> Nota: El SDK aun no esta publicado en Maven Central.

**Instalacion futura:**
```gradle
dependencies {
    // Solo logica de autenticacion
    implementation("es.fjmarlop.passkeyauth:core:0.1.0")
    
    // + Componentes UI opcionales
    implementation("es.fjmarlop.passkeyauth:ui:0.1.0")
}
```

**Para desarrollo local:**
```bash
git clone https://github.com/fjmarlop/PasskeyAuth.git
cd PasskeyAuth
./gradlew publishToMavenLocal
```

## Uso Basico

> API en desarrollo, sujeta a cambios

### Enrollment (Primera vez)
```kotlin
val enrollmentManager = EnrollmentManager.create(activity)

lifecycleScope.launch {
    enrollmentManager.enrollDevice(
        email = "empleado@empresa.com",
        temporaryPassword = "TempPass123",
        newPassword = "MiNuevaPass_456"
    ).collect { state ->
        when (state) {
            is EnrollmentState.ValidatingCredentials -> {
                showProgress("Validando credenciales...")
            }
            is EnrollmentState.AwaitingBiometric -> {
                showProgress("Registra tu huella digital...")
            }
            is EnrollmentState.Success -> {
                showSuccess("Dispositivo registrado!")
                navigateToHome(state.user)
            }
            is EnrollmentState.Error -> {
                showError(state.exception.getUserMessage())
            }
            else -> { /* Otros estados */ }
        }
    }
}
```

### Verificar Enrollment
```kotlin
if (enrollmentManager.isDeviceEnrolled()) {
    // Ir a login
} else {
    // Mostrar pantalla de enrollment
}
```

## Seguridad

### Caracteristicas

- Cifrado AES-256-GCM (autenticado)
- Claves almacenadas en Android KeyStore
- StrongBox cuando disponible, fallback a TEE
- Biometria STRONG requerida
- Device binding con revocacion remota
- Modelo "1 user = 1 device"
- Invalidacion automatica si cambia biometria
- Screenshot protection (futuro)
- Root/emulator detection (futuro)
- ProGuard rules incluidas

### Cumplimiento

- OWASP MASVS L2 (Mobile Application Security Verification Standard)
- NIST SP 800-63B Digital Identity Guidelines
- Android CDD Biometric Security Requirements

### Flujo de Enrollment (7 Pasos)

1. Validar credenciales temporales (Firebase Auth)
2. Cambiar contrasenia (de temporal a permanente)
3. Generar clave criptografica (KeyStore AES-GCM)
4. Autenticar biometricamente (BiometricPrompt)
5. Cifrar token de sesion
6. Device binding en Firestore
7. Guardar en storage local cifrado

**Rollback automatico:** Si cualquier paso falla, se deshacen los pasos anteriores.

## Documentacion

- **[ADRs](docs/adr/)**: Decisiones arquitectonicas (6 documentados)
- **[API Reference](docs/api/)**: Documentacion de la API publica (pendiente)
- **[Security](docs/security/)**: Modelo de amenazas (pendiente)
- **[Development Guide](DEVELOPMENT.md)**: Guia para desarrolladores

## Desarrollo

### Build del Proyecto
```bash
# Windows
.\gradlew.bat build

# Linux/macOS
./gradlew build
```

### Ejecutar Sample App
```bash
.\gradlew.bat :sample:installDebug
```

### Tests
```bash
# Unit tests
.\gradlew.bat test

# Android instrumented tests
.\gradlew.bat connectedAndroidTest
```

## Roadmap

- [x] v0.1: Arquitectura multi-modulo + Core SDK completo
- [ ] v0.2: PasskeyAuth facade + LoginManager
- [ ] v0.3: Sample app funcional + UI components
- [ ] v0.4: Testing completo (80%+ coverage)
- [ ] v0.5: Documentacion completa + Security audit
- [ ] v1.0: Release estable

## Estadisticas del Proyecto

**Version 0.1.0-SNAPSHOT:**
- Total de archivos Kotlin: 19
- Lineas de codigo: ~3,500
- ADRs documentados: 6
- Componentes core: 8 (Crypto, Auth, Firebase, Storage, Enrollment, Models, Exceptions)

## Contribuciones

Este proyecto esta en desarrollo activo. Las contribuciones seran bienvenidas una vez se publique v1.0.

## Licencia
```
Copyright 2026 Francisco Javier Marmolejo Lopez

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

## Autor

**Francisco Javier Marmolejo Lopez**
- GitHub: [@fjmarlop](https://github.com/fjmarlop)

---

**Advertencia:** Este SDK esta en desarrollo activo. No usar en produccion hasta v1.0.

## 🔒 Mejores Prácticas de Seguridad

**CRÍTICO:** PasskeyAuth provee herramientas de seguridad, pero TÚ debes implementarlas correctamente.

### ⚠️ Errores Comunes
```kotlin
// ❌ INSEGURO - Cualquiera con dispositivo puede acceder
if (PasskeyAuth.isDeviceEnrolled()) {
    navegarAHome()
}

// ❌ INSEGURO - Estado de autenticación obsoleto
if (PasskeyAuth.isAuthenticated()) {
    navegarAHome()
}
```

### ✅ Implementación Segura
```kotlin
// ✅ SEGURO - Siempre requiere biometría al abrir app
@Composable
fun SplashScreen() {
    LaunchedEffect(Unit) {
        when {
            !PasskeyAuth.isDeviceEnrolled() -> navegarAEnrollment()
            else -> navegarALogin()  // Siempre requiere biometría
        }
    }
}

// ✅ SEGURO - Implementar hooks de ciclo de vida
class MainActivity : FragmentActivity() {
    override fun onStart() {
        super.onStart()
        if (!isChangingConfigurations) {
            PasskeyAuth.onAppForeground()
        }
    }
    
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            PasskeyAuth.onAppBackground()
        }
    }
}
```

**📖 Lee la [Guía de Seguridad](SECURITY.md) completa antes de desplegar a producción.**

### Conceptos Clave

| Método | Propósito | Nivel de Seguridad |
|--------|-----------|-------------------|
| `isDeviceEnrolled()` | Verifica si existen claves | ⚠️ Bajo - Sin verificación |
| `isAuthenticated()` | Verifica biometría reciente | ✅ Alto - Verificado |
| `authenticate()` | Dispara prompt biométrico | ✅ Alto - Verificación activa |
