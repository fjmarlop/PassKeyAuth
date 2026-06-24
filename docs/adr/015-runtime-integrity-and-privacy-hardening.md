# ADR-015: Integridad del entorno de ejecución y endurecimiento de privacidad

**Estado:** Aceptado
**Fecha:** 2026-06-25
**Autores:** Francisco Javier Marmolejo López

---

## Contexto

El SDK vende una garantía enterprise (ADR-013): *"lo que entró por mí fue biometría fuerte + clave hardware-backed, sin excepciones"*. Esa garantía asume un **entorno de ejecución no comprometido**. En la práctica, varios vectores la erosionan sin tocar el código del SDK:

- **Root / `su`:** acceso al proceso y al AndroidKeyStore; extracción de material sensible.
- **Frameworks de hooking (Frida, Xposed/LSPosed):** interceptan `BiometricPrompt` a nivel de proceso y devuelven éxito sin huella real, o parchean `setAllowedAuthenticators(BIOMETRIC_STRONG)`.
- **Emuladores:** no tienen sensor biométrico real; la ceremonia puede simularse.
- **Depurador adjunto en release:** permite modificar el retorno de `onAuthenticationSucceeded`.
- **Capturas de pantalla / app switcher / overlays:** exponen credenciales o la pantalla de auth a apps de terceros y servicios de accesibilidad.
- **Datos sensibles en memoria / portapapeles / backup:** persisten más allá de su vida útil.

Ninguno de estos estaba mitigado antes de esta decisión. El plan completo vive en [`docs/plans/2026-06-25-security-hardening.md`](../plans/2026-06-25-security-hardening.md).

---

## Decisión

### 1. Comprobación de integridad en `initialize()`

`PasskeyAuth.initialize()` ejecuta `IntegrityGuard.check()` **antes de operar**. Si la política configurada es `Block` y se detecta un entorno comprometido, `initialize()` devuelve `Result.failure(IntegrityException)` y el SDK no arranca.

Los detectores viven en `passkeyauth-core/core/security/`:

| Detector | Señales |
|---|---|
| `RootDetector` | binarios `su`/`busybox`, apps de root (Magisk, SuperSU…), `Build.TAGS=test-keys` |
| `HookDetector` | artefactos de Frida, paquetes Xposed/LSPosed, clases `XposedBridge` cargables |
| `EmulatorDetector` | heurística sobre `Build.FINGERPRINT/MODEL/HARDWARE/PRODUCT…` |
| Anti-debug | `Debug.isDebuggerConnected()` (solo en release) |

Cada detector recibe sus dependencias de plataforma (sistema de ficheros, `PackageManager`, propiedades de `Build`) **inyectadas**, de modo que la lógica de decisión (`IntegrityGuard.evaluate()`) es pura y testeable en JVM sin device. Defensa en profundidad, no garantía absoluta: un atacante determinado puede ocultar el compromiso.

#### Manejo del fallo por el host

El SDK **reporta** el fallo de forma tipada (`Result.failure(IntegrityException)`) pero **no decide la UX** — es responsabilidad del host. Si el host ignora el fallo y sigue llamando a `enrollDevice()`/`authenticate()`, esas funciones lanzan `IllegalStateException` (SDK no inicializado), lo que se percibe como un crash sin explicación.

El patrón correcto (implementado en el sample como referencia): capturar `IntegrityException` en el resultado de `initialize()` y renderizar una pantalla de bloqueo amigable (`SecurityBlockScreen`) que use `IntegrityException.getUserMessage()` y cierre la app de forma controlada (`finishAndRemoveTask()`), en lugar de montar el nav graph normal.

```kotlin
PasskeyAuth.initialize(context, config).onFailure { error ->
    if (error is IntegrityException) {
        securityBlockMessage.value = error.getUserMessage() // → pantalla de bloqueo
    }
}
```

### 2. Políticas configurables vs invariantes

Se amplía `PasskeyAuthConfig` (manteniendo el contrato de ADR-013):

```kotlin
rootPolicy: RootPolicy          // Block | Warn | Allow  — gobierna root Y hooking
emulatorPolicy: EmulatorPolicy  // Block | Warn | Allow
enablePrivacyOverlay: Boolean   // overlay opaco al pasar a background
```

Defaults por configuración:

| Config | rootPolicy | emulatorPolicy | Razón |
|---|---|---|---|
| `Default` (producción) | Block | Block | blindado |
| `Debug` | Warn | Allow | desarrollo en emuladores |
| `Custom` | Block | **Warn** | estricto sin romper QA en emulador |

**Invariantes NO configurables** (no existe knob que los relaje):

- **Anti-debug en release:** un depurador adjunto en un build de release siempre falla, sin importar la política.
- **`FLAG_SECURE`** en las pantallas del SDK (`PasskeyAuthActivity`): bloquea screenshots, grabación y la preview del app switcher.

### 3. Privacidad de pantalla (capa UI)

- **`FLAG_SECURE`** se aplica incondicionalmente en `PasskeyAuthActivity`. Se recomienda al host replicarlo en pantallas que muestren credenciales (el sample lo hace en `MainActivity`).
- **`PrivacyOverlay`** (composable) cubre las pantallas del SDK con una superficie opaca al recibir `ON_PAUSE`, cerrando el hueco de launchers de terceros entre `onPause` y el efecto de `FLAG_SECURE`. Gobernado por `enablePrivacyOverlay`.

### 4. Datos sensibles

- **Memory zeroing:** el plaintext del token se borra del heap (`ByteArray.fill(0)`) inmediatamente tras cifrarlo (enrollment) o tras descifrarlo para validar (login). El SDK no retiene el token en claro.
- **Clipboard:** el host limpia el portapapeles al pasar a background en la pantalla de credenciales (implementado en el sample).
- **Backup:** `allowBackup=false` + `fullBackupContent=false` en el sample, evitando exfiltración del estado vía adb backup.

### 5. Tapjacking (capa UI — E1)

Una app maliciosa puede superponer una capa transparente sobre las pantallas del SDK para redirigir o interceptar la interacción del usuario. La protección se aplica en `dispatchTouchEvent` de cada Activity:

```kotlin
override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
    if (ev.flags and MotionEvent.FLAG_WINDOW_IS_OBSCURED != 0) return false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
        ev.flags and MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED != 0) return false
    return super.dispatchTouchEvent(ev)
}
```

- `FLAG_WINDOW_IS_OBSCURED` — ventana completamente cubierta (API 26+)
- `FLAG_WINDOW_IS_PARTIALLY_OBSCURED` — cubierta parcialmente por un overlay (API 29+)

`BiometricPrompt` gestiona su propio diálogo fuera de la ventana del SDK: el sistema ya aplica protección anti-tapjacking en ese diálogo. Esta medida cubre las pantallas propias: `PasskeySignInScreen`, `PasskeyEnrollScreen` (vía `PasskeyAuthActivity`) y `CredentialsScreen` (vía `MainActivity` del host — implementado en el sample como referencia).

### 6. Key attestation (D2)

`KeyAttestationVerifier` verifica, tras cada generación de clave, que el material reside en hardware real usando `android.security.keystore.KeyInfo`:

- API 23–30: `KeyInfo.isInsideSecureHardware()` — distingue hardware (TEE/StrongBox) de software
- API 31+: `KeyInfo.securityLevel` — distingue `STRONGBOX` de `TRUSTED_ENVIRONMENT` de `SOFTWARE`

El resultado se expone como `HardwareSecurityLevel` (público) y se logea en el paso 3 del enrollment. Es **informativo, no bloqueante** en modo producción — el enforcement ya está en `generateKeyWithStrongBox()`. Añade valor como:
- Auditoría de nivel de hardware de los dispositivos enrolados
- Defensa en profundidad: si la API del KeyStore mintiera (dispositivo comprometido), el `IntegrityGuard` ya lo bloquea antes de llegar al enrollment.

### 7. Red

- **Network Security Config** (`cleartextTrafficPermitted=false`) prohíbe tráfico en claro.
- **Certificate pinning** — las llamadas HTTP las hace el Firebase SDK, no el SDK de PasskeyAuth. El pinning es responsabilidad del integrador en su `network_security_config.xml`. Ver [guía de cert pinning para integradores](../../DEVELOPMENT.md#cert-pinning-para-integradores).

---

## Consecuencias

**Positivas:**
- La garantía del SDK ahora cubre el entorno de ejecución, no solo la criptografía.
- Toda la lógica de decisión es testeable en JVM (no requiere device rooteado para los tests).
- Las políticas dan al integrador control sobre el trade-off seguridad/ergonomía sin tocar invariantes.

**Negativas / trade-offs:**
- Las heurísticas de root/emulador/hooking tienen falsos negativos (atacante sofisticado) y posibles falsos positivos (ROMs custom legítimas) — por eso son políticas, no hard-blocks universales.
- `Build.TAGS=test-keys` marca como "root" a entornos de test (Robolectric) y ROMs de desarrollo; los tests del facade estabilizan el `PackageManager` para reflejar un entorno limpio.
- Certificate pinning queda pendiente de activación manual: la mitigación de MitM no es efectiva hasta entonces.

---

## Alternativas consideradas

- **Play Integrity API / SafetyNet:** más robusto (attestation server-side) pero acopla a Google Play Services y requiere backend. Complementario, no excluyente — candidato para una iteración futura. Las heurísticas locales son la primera capa offline.
- **Bloqueo duro sin políticas:** rechazado; rompería QA, ROMs legítimas y desarrollo en emulador. El modelo de políticas preserva la ergonomía.

---

## Referencias

- Plan de implementación: [`docs/plans/2026-06-25-security-hardening.md`](../plans/2026-06-25-security-hardening.md)
- Contrato del SDK e invariantes previos: [ADR-013](013-non-negotiable-security-invariants.md)
- Módulo UI híbrido: [ADR-014](014-ui-module-hybrid-integration.md)
