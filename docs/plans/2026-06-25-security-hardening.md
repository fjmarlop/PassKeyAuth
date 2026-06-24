# Security Hardening Plan — PasskeyAuth SDK v0.3.x

> **Para implementar:** ejecutar con `mobiai-mobile-executing-plans` o `mobiai-mobile-executing-plans-with-subagents`.

**Goal:** Elevar el SDK al nivel de seguridad exigible para un autenticador biométrico enterprise — sin debilitar usabilidad.

**Principio rector:** Las medidas de seguridad que no tienen coste de UX son **no-configurables** (siempre activas). Las que impactan a integradores o flujos legítimos son **opt-in/opt-out** vía `PasskeyAuthConfig`.

**Platform:** Android API 26+

---

## Estado de implementación (rama `feat/security-hardening`, 2026-06-25)

Implementado y testeado (ver [ADR-015](../adr/015-runtime-integrity-and-privacy-hardening.md)):

| Bloque | Estado |
|---|---|
| A1 — `FLAG_SECURE` | ✅ `PasskeyAuthActivity` + sample `MainActivity` |
| A2 — Privacy overlay | ✅ `PrivacyOverlay` en pantallas del SDK |
| B1 — Root detection | ✅ `RootDetector` + `IntegrityGuard` (7 tests) |
| B2 — Emulator detection | ✅ `EmulatorDetector` (6 tests) |
| B3 — Anti-debug | ✅ invariante en release dentro de `IntegrityGuard` |
| B4 — Frida/Xposed | ✅ `HookDetector` (6 tests) |
| C2 — Network config | ✅ `network_security_config.xml` (sin cleartext) |
| D1 — Memory zeroing | ✅ token plaintext borrado en enrollment + login |
| E2 — Clipboard | ✅ limpieza en `CredentialsScreen` al background |
| F1 — Backup off | ✅ `allowBackup=false` + `fullBackupContent=false` |
| C1 — Cert pinning | ⏳ plantilla comentada (requiere pines reales) |
| D2 — Key attestation | ⏳ pendiente |
| E1 — Tapjacking | ⏳ pendiente (verificar cobertura de `androidx.biometric`) |
| F2 — exported audit | ✅ ya correcto (`PasskeyAuthActivity exported=false`) |

Leyenda detalle de tareas:
- [ ] = pendiente
- [x] = implementado

---

## Bloque A — Privacidad de pantalla (prioridad alta)

### A1 — Deshabilitar capturas de pantalla (`FLAG_SECURE`)

**Alcance:** `PasskeyAuthActivity` + recomendado en host app.

**Por qué:** `FLAG_SECURE` impide:
- Screenshots y grabación de pantalla durante la pantalla de auth
- Que la preview en el app switcher (recientes) exponga credenciales o biometría
- Que servicios de accesibilidad maliciosos lean el contenido de la pantalla

**Config:** no configurable en `PasskeyAuthActivity` (invariante). Opcional para el host vía helper.

```kotlin
// PasskeyAuthActivity.kt — en onCreate(), antes de setContent
window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
enableEdgeToEdge()
setContent { ... }
```

- [ ] Añadir `FLAG_SECURE` en `PasskeyAuthActivity.onCreate()`
- [ ] Añadir `FLAG_SECURE` en `MainActivity` del sample
- [ ] Documentar en ADR-013 como invariante de privacidad
- [ ] Smoke test S1/S2: verificar que el app switcher muestra pantalla en blanco

### A2 — Privacy overlay al pasar a segundo plano

**Por qué:** `FLAG_SECURE` cubre el app switcher del sistema, pero algunos launchers de terceros o APIs de accesibilidad pueden capturar la última pantalla antes de que `FLAG_SECURE` surta efecto. Un overlay opaco/borroso en `onPause` cierra ese gap.

**Config:** `PasskeyAuthConfig.enablePrivacyOverlay: Boolean = true` (opt-out para integradores que ya gestionan esto).

**Implementación:** Composable `PrivacyOverlay` que se muestra cuando `lifecycle.currentState < RESUMED`:

```kotlin
@Composable
fun PrivacyOverlay(enabled: Boolean = true) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val isResumed by lifecycle.currentStateAsState()
    if (enabled && isResumed < Lifecycle.State.RESUMED) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Icon(
                Icons.Outlined.Security,
                contentDescription = null,
                modifier = Modifier.align(Alignment.Center).size(48.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            )
        }
    }
}
```

- [ ] Implementar `PrivacyOverlay` composable en `passkeyauth-ui`
- [ ] Añadir a `PasskeySignInScaffold` y `PasskeyEnrollScreen`
- [ ] Añadir flag `enablePrivacyOverlay` a `PasskeyAuthConfig`
- [ ] Test: lifecycle transition pausa → reanuda → overlay aparece y desaparece

---

## Bloque B — Integridad del entorno de ejecución (prioridad alta)

### B1 — Detección de root

**Por qué:** En un device rooteado `su` puede extraer claves del KeyStore, modificar la respuesta del BiometricPrompt, o leer memoria del proceso. El SDK no debe enrolar ni autenticar en dispositivos comprometidos.

**Config:** `PasskeyAuthConfig.rootPolicy: RootPolicy = RootPolicy.Block`
- `Block` — lanza `SecurityException` antes de cualquier operación
- `Warn` — emite log + continúa (para entornos de desarrollo)
- `Allow` — sin chequeo (no recomendado)

**Señales a detectar:**
- Binarios `su` / `busybox` en paths conocidos
- Apps de root (`com.topjohnwu.magisk`, `com.koushikdutta.superuser`, etc.)
- Propiedades de sistema (`ro.debuggable=1`, `ro.secure=0`)
- Escritura en `/system` (test de permisos)
- Build tags (`test-keys`)

- [ ] Crear `RootDetector` internal class en `passkeyauth-core/security/`
- [ ] Integrar en `PasskeyAuth.initialize()` y en `EnrollmentManager`
- [ ] Tests: mocks de las señales de root, verificar cada señal de forma aislada
- [ ] ADR-015: documentar política de root y señales

### B2 — Detección de emulador

**Por qué:** Los emuladores no tienen hardware biométrico real — el SDK puede ser bypass-eado con biometría simulada. Detectar emulador permite bloquear en producción y advertir en debug.

**Config:** `PasskeyAuthConfig.emulatorPolicy: EmulatorPolicy = EmulatorPolicy.Block` en release, `Warn` en debug automáticamente.

**Señales:** `Build.FINGERPRINT` contiene "generic"/"unknown", `Build.MODEL` contiene "Emulator"/"Android SDK", `Build.HARDWARE` = "goldfish"/"ranchu".

- [ ] Crear `EmulatorDetector` internal class
- [ ] Auto-`Warn` en builds debug, auto-`Block` en builds release
- [ ] Integrar en `PasskeyAuth.initialize()`

### B3 — Detección de depurador adjunto

**Por qué:** Un debugger adjunto puede modificar el return value de `BiometricPrompt.AuthenticationCallback.onAuthenticationSucceeded()`, elevando privilegios sin huella real.

**Config:** no configurable (invariante en release builds).

```kotlin
if (!BuildConfig.DEBUG && Debug.isDebuggerConnected()) {
    throw SecurityException("Debugger detected — PasskeyAuth cannot run under inspection")
}
```

- [ ] Añadir check en `PasskeyAuth.initialize()` (solo en release)
- [ ] Test: verificar que el check solo actúa en release builds

### B4 — Detección de hooks (Frida / Xposed)

**Por qué:** Frida puede interceptar `BiometricPrompt` a nivel de proceso y devolver éxito sin huella. Xposed puede parchear `setAllowedAuthenticators`.

**Señales:**
- `/data/local/tmp/frida-server` existe
- Paquetes Xposed en el device (`de.robv.android.xposed.installer`)
- Método `XposedBridge` en el classpath

- [ ] Crear `HookDetector` internal class
- [ ] Integrar en `PasskeyAuth.initialize()`
- [ ] Política configurable (por defecto `Block`)

---

## Bloque C — Seguridad de red (prioridad media)

### C1 — Certificate pinning (Firebase)

**Por qué:** Sin pinning, un atacante con acceso a la red (MitM, proxy corporativo malicioso) puede falsificar respuestas de Firebase y validar credenciales incorrectas o bypass-ear la revocación de dispositivos.

**Implementación:** `network_security_config.xml` con pins para los certificados raíz de Google. Backup pins obligatorio para evitar bricking si Google rota.

```xml
<!-- res/xml/network_security_config.xml -->
<network-security-config>
    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="true">firebaseio.com</domain>
        <domain includeSubdomains="true">googleapis.com</domain>
        <pin-set expiration="2027-01-01">
            <pin digest="SHA-256">AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=</pin> <!-- pin real -->
            <pin digest="SHA-256">BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=</pin> <!-- backup -->
        </pin-set>
    </domain-config>
</network-security-config>
```

- [ ] Obtener pins actuales de `firebaseio.com` y `googleapis.com` con `openssl`
- [ ] Crear `network_security_config.xml` en `passkeyauth-core`
- [ ] Establecer proceso de rotación de pins (ADR o CHANGELOG entry)
- [ ] Test: verificar que conexiones con certificado incorrecto son rechazadas

### C2 — Reforzar Network Security Config

- [ ] Prohibir tráfico en claro (`cleartextTrafficPermitted="false"`) en toda la lib
- [ ] Verificar que el sample hereda la config del SDK

---

## Bloque D — Protección de datos en memoria (prioridad media)

### D1 — Zeroing de datos sensibles

**Por qué:** Las contraseñas temporales y tokens permanecen en el heap de Java hasta que el GC los recoge. Un heap dump puede extraerlos.

**Qué zerear:**
- `password: String` en `CredentialsScreen` → usar `CharArray` + fill `' '` tras uso
- `FirebaseAuthBackend` — borrar token de acceso tras binding
- `EncryptedData.ciphertext` — borrar tras descifrado

- [ ] Cambiar `password` en `EnrollmentManager.enroll()` a `CharArray`
- [ ] Añadir `Arrays.fill(passwordChars, ' ')` tras uso
- [ ] Revisar todos los sites donde `String` contiene datos sensibles

### D2 — Key attestation verification

**Por qué:** `AndroidKeyStoreManager` genera la clave y asume que está en hardware. La attestation permite verificarlo criptográficamente — imprescindible en enterprise.

**Implementación:** Llamar a `KeyStore.getCertificateChain()` y verificar la cadena de attestation contra los certificados raíz de Google.

- [ ] Implementar `KeyAttestationVerifier` en `passkeyauth-core/crypto/`
- [ ] Invocar tras `generateKey()` en `AndroidKeyStoreManager`
- [ ] Política configurable: `StrongBoxPolicy.Required` → fallar si attestation dice TEE; `Preferred` → loggear warning

---

## Bloque E — Protección de UI (prioridad media)

### E1 — Detección de screen overlay (tapjacking)

**Por qué:** Una app maliciosa puede superponer una capa transparente sobre el BiometricPrompt para redirigir la huella. Android tiene `FLAG_WINDOW_IS_OBSCURED` para detectarlo.

**Nota:** `BiometricPrompt` de `androidx.biometric` ya aplica cierta protección a nivel de sistema, pero conviene verificarlo.

- [ ] Verificar comportamiento de `androidx.biometric` frente a overlays
- [ ] Si no está cubierto: añadir `filterTouchesWhenObscured = true` en las vistas relevantes
- [ ] Test manual con una overlay app

### E2 — Protección del portapapeles

**Por qué:** Si el usuario copia el email o la contraseña temporal desde `CredentialsScreen`, permanece en el clipboard indefinidamente.

- [ ] Limpiar el clipboard tras 60s si el último contenido copiado viene de la app
- [ ] Usar `ClipboardManager.clearPrimaryClip()` en `onPause` de `CredentialsScreen`

---

## Bloque F — Configuración del manifiesto (prioridad baja, fácil)

### F1 — Prevenir backup de datos sensibles

```xml
<!-- Ya debería estar, verificar -->
android:allowBackup="false"
android:fullBackupContent="false"
```

- [ ] Verificar `allowBackup="false"` en el manifiesto del SDK y del sample
- [ ] Añadir `<exclude domain="sharedpref" path="."/>` si se usa DataStore fallback

### F2 — Exported=false en Activities internas

- [ ] Verificar `android:exported="false"` en `PasskeyAuthActivity`
- [ ] Audit del manifiesto completo

---

## Resumen de impacto vs esfuerzo

| Bloque | Seguridad | Esfuerzo | Prioridad |
|---|---|---|---|
| A1 — FLAG_SECURE | Alta | Muy bajo (1 línea) | **Inmediata** |
| A2 — Privacy overlay | Media | Bajo | **Inmediata** |
| B1 — Root detection | Muy alta | Medio | Alta |
| B2 — Emulator detection | Alta | Bajo | Alta |
| B3 — Anti-debug | Alta | Muy bajo | Alta |
| B4 — Frida/Xposed | Alta | Medio | Alta |
| C1 — Cert pinning | Alta | Medio | Alta |
| C2 — Network config | Media | Bajo | Media |
| D1 — Memory zeroing | Media | Bajo | Media |
| D2 — Key attestation | Alta | Alto | Media |
| E1 — Tapjacking | Media | Bajo | Media |
| E2 — Clipboard | Baja | Bajo | Baja |
| F1/F2 — Manifiesto | Media | Muy bajo | **Inmediata** |

**Orden recomendado:** A1 → F1/F2 → B3 → B2 → A2 → B1 → B4 → C1/C2 → D1 → E1 → D2 → E2
