# Releases

<!--
Release notes, checklists and lessons learned during shipping.
-->

## Versión actual: 0.2.0-alpha

Pre-release. Aún no publicado en Maven Central.

---

## [0.2.0] — 2026-01-23

### 🚨 BREAKING CHANGES

**Enrollment API simplificada — Passwordless real.**

- Eliminado parámetro `newPassword` de `PasskeyAuth.enrollDevice()`
- Firma anterior (v0.1.0):
  ```kotlin
  enrollDevice(activity, email, temporaryPassword, newPassword)
  ```
- Firma nueva:
  ```kotlin
  enrollDevice(activity, email, temporaryPassword)
  ```

### Added
- `FirebaseAuthManager.invalidateTemporaryPassword()` — genera password aleatoria fuerte (32 chars, ~200 bits entropía con `SecureRandom`)
- Mensaje informativo en UI sobre invalidación automática

### Changed
- `EnrollmentManager` paso 2: `changePassword()` → `invalidateTemporaryPassword()`
- `EnrollmentScreen`: reducida de 4 a 2 campos
- `AuthViewModel.enrollDevice()` sin `newPassword`
- ADR-006 actualizado con decisión de passwordless real

### Security
- Passwords aleatorias de 32 chars > passwords elegidas por usuario
- Prevención de reutilización de password en otros servicios
- Password nunca almacenada localmente ni conocida por el usuario

### Migration Guide v0.1 → v0.2
1. Quitar `newPassword` de llamadas a `enrollDevice()`
2. Quitar campos de nueva contraseña de la UI
3. Informar al usuario que solo necesitará biometría

---

## [0.1.0-SNAPSHOT] — 2026-01-22

### Initial Release

**Core (21 archivos):**
- Models (5): `AuthResult`, `AuthUser`, `BiometricConfig`, `EnrollmentState`, `DeviceInfo`
- Exceptions (6): `PasskeyAuthException` base + 5 subtipos (`Biometric` 8 errores, `Firebase` 6, `Crypto` 5, `Device` 4, `Enrollment` 3)
- Crypto (3): `KeyStoreManager`, `CryptoProvider`, `EncryptedData`
- Auth (1): `BiometricAuthenticator`
- Firebase (2): `FirebaseAuthManager`, `DeviceBindingManager`
- Storage (1): `SecureStorage`
- Enrollment (1): `EnrollmentManager`
- Public API (2): `PasskeyAuthConfig`, `PasskeyAuth`

### Security Features
- AES-256-GCM con AEAD
- Hardware-backed keys (StrongBox con TEE fallback)
- Biometric Class 3 (STRONG)
- Device binding 1:1
- Invalidación automática de claves al cambiar biometría
- Enrollment transaccional con rollback automático
- Storage local cifrado (DataStore)

### Architecture
- Multi-módulo (core, ui, sample)
- Type-safe con `Result<T>`
- Reactivo con `Flow` / `StateFlow`
- Coroutines-first, Compose-friendly
- DI manual

### Sample App
- 4 pantallas Compose (Splash, Enrollment, Login, Home)
- Material Design 3

### Compliance
- OWASP MASVS Level 2
- NIST SP 800-63B AAL2
- Android CDD Biometric Class 3

### Stats
- 21 archivos Kotlin
- ~5.500 LOC
- 6 ADRs inicialmente (luego 9)
- API 26+ (Android 8.0+)
- Gradle 9.1.0 + Kotlin 2.1.0

---

## Roadmap pendiente

### v0.3 (próximo)
- Implementar `PasskeyAuthLogger` real (ADR-005) y migrar todos los `println()`
- Tests unitarios + integración completos
- Tests en dispositivos físicos con/sin StrongBox

### v1.0 (release)
- Auditoría completa de logs (verificar que no hay PII)
- ProGuard rules activadas para remover logs en release
- Security hardening (root detection, etc.)
- Maven Central publishing
- Code coverage targets: core >80%, ui >60%, sample >40%

---

## Release Process (definido en DEVELOPMENT.md, aún no ejecutado)

1. Update version en `gradle.properties`
2. Update `CHANGELOG.md`
3. Run full test suite
4. Create release branch: `release/vX.Y.Z`
5. Build release artifacts: `.\gradlew.bat assembleRelease`
6. Tag commit: `git tag vX.Y.Z`
7. Publish to Maven Central
8. Create GitHub Release

---

## Lessons Learned

### v0.2.0
- **Passwordless real > passwordless con contraseña de usuario.** Si el usuario conoce la password puede usarla manualmente, eliminando el beneficio de seguridad de la biometría.
- **Breaking changes en alpha son aceptables.** Mejor romper API ahora que mantener mal diseño hasta v1.0.

### v0.1.0
- **`SystemState` fue un error de diseño.** Acoplaba el core a decisiones de UI. Eliminado en v0.2.0 (ADR-008).
- **El bug del SplashScreen** (acceso a Home sin biometría tras reabrir app — ver `bugfixes.md`) demostró que incluso con buena documentación, el sample app debe ser impecable porque es la referencia que copian los integradores.
