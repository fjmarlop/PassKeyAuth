# PasskeyAuth SDK for Android

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://android-arsenal.com/api?level=26)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-blue.svg)](https://kotlinlang.org)
[![Tests](https://img.shields.io/badge/Tests-147%20automáticos%20%2B%209%20instrumented-brightgreen.svg)](docs/adr/011-testing-stack-and-strategy.md)
[![CI](https://github.com/fjmarlop/PassKeyAuth/actions/workflows/ci.yml/badge.svg)](https://github.com/fjmarlop/PassKeyAuth/actions/workflows/ci.yml)

Librería Android para autenticación sin contraseñas usando biometría hardware-backed, diseñada para entornos enterprise con modelo "1 user = 1 device". Backend-agnóstica: incluye Firebase como implementación de referencia y permite inyectar un backend propio (Keycloak, OIDC, REST).

**Versión actual:** 1.0.0 — disponible en Maven Central. Ver [CHANGELOG](CHANGELOG.md) para el historial completo.

---

## 🚀 Instalación

```gradle
dependencies {
    implementation("io.github.fjmarlop:passkeyauth-core:1.0.0")
    // Opcional — componentes Compose (PasskeySignInScreen, PasskeyEnrollScreen, PasskeyAuthContract):
    implementation("io.github.fjmarlop:passkeyauth-ui:1.0.0")
}
```

**➡️ Para integrar el SDK en una app paso a paso** (manifest, `FragmentActivity`,
elegir modo Launcher vs composables, `FLAG_SECURE` del host, inicialización,
lifecycle hooks) sigue la **[Guía de Integración en DEVELOPMENT.md](DEVELOPMENT.md#guía-de-integración-paso-a-paso-app-nueva)**.

---

## 💻 Quick Start

```kotlin
class MainActivity : FragmentActivity() {   // ⚠️ DEBE ser FragmentActivity — BiometricPrompt lo requiere

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            PasskeyAuth.initialize(context = applicationContext, config = PasskeyAuthConfig.Default)
        }
    }

    override fun onStart() { super.onStart(); PasskeyAuth.onAppForeground() }  // requerido para el timeout
    override fun onStop()  { super.onStop();  PasskeyAuth.onAppBackground() }
}

// Enrollment (primera vez)
PasskeyAuth.enrollDevice(activity, email, temporaryPassword).collect { state -> /* EnrollmentState */ }

// Login (subsecuente)
PasskeyAuth.authenticate(activity).onSuccess { user -> navigateToHome() }

// Logout / unenroll
PasskeyAuth.logout()
PasskeyAuth.unenrollDevice()
```

Guía completa con los 8 pasos, manejo de errores y backend personalizado: **[DEVELOPMENT.md](DEVELOPMENT.md#guía-de-integración-paso-a-paso-app-nueva)**.

---

## 🔒 Seguridad — lectura obligatoria antes de producción

`isDeviceEnrolled()` confirma que existen claves; **no** confirma biometría reciente.
Tu router/splash debe pasar siempre por `authenticate()` o `PasskeySignInScreen`,
nunca saltar a Home solo con `isDeviceEnrolled()`.

El SDK protege sus propias pantallas (`FLAG_SECURE`, anti-tapjacking, root/emulator/hooking
detection) pero **no las tuyas** si usas los composables embebidos en tu propia Activity —
ver el porqué y el fix en [DEVELOPMENT.md § Paso 5](DEVELOPMENT.md#paso-5--seguridad-del-host-flag_secure--anti-tapjacking).

**➡️ [Guía de Seguridad completa](SECURITY.md)** — errores comunes, patrones seguros y checklist antes de desplegar.

---

## 📚 Documentación

| Documento | Para qué |
|---|---|
| **[DEVELOPMENT.md](DEVELOPMENT.md)** | Arquitectura de módulos, guía de integración paso a paso, convenciones, testing, publicación en Maven Central |
| **[SECURITY.md](SECURITY.md)** | Errores comunes, patrones seguros, checklist de seguridad antes de producción |
| **[CHANGELOG.md](CHANGELOG.md)** | Historial de versiones |
| **[docs/adr/](docs/adr/)** | 16 ADRs — decisiones arquitectónicas documentadas |
| **[docs/MANUAL-SMOKE-TEST.md](docs/MANUAL-SMOKE-TEST.md)** | Checklist de release: 10 escenarios E2E con BiometricPrompt real |

---

## 🏗️ Arquitectura

```
PasskeyAuth/
├── passkeyauth-core/      # Lógica de autenticación (sin UI) — AuthBackend/DeviceRegistry inyectables
├── passkeyauth-ui/        # Componentes Compose — PasskeySignInScreen, PasskeyEnrollScreen, PasskeyAuthContract
└── sample/                # App de demostración (wiring directo al SDK UI)
```

Detalle de paquetes, componentes públicos vs `internal`, y diseño de testabilidad: [DEVELOPMENT.md § Arquitectura del Proyecto](DEVELOPMENT.md#arquitectura-del-proyecto).

---

## 🤝 Contribuciones

Contribuciones bienvenidas — abre un issue o PR en [GitHub](https://github.com/fjmarlop/PassKeyAuth).

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
