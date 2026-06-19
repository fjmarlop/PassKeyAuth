# ADR-013: Invariantes de Seguridad No Negociables y Contrato de `PasskeyAuthConfig`

**Estado:** Aceptado
**Fecha:** 2026-06-20
**Autores:** Francisco Javier Marmolejo López

---

## Contexto

Antes de construir el módulo `passkeyauth-ui` (ADR-014) hace falta fijar el **contrato del SDK**, porque la UI es una proyección de ese contrato: si el contrato se mueve después, la UI churna detrás. Clavarlo ahora es barato; rehacerlo cuando hay seis estados de pantalla pintados, no.

El SDK vende una garantía enterprise: *"lo que entró por mí fue biometría fuerte + clave hardware-backed, sin excepciones"*. Para que esa frase sea cierta y no marketing vacío, hay que separar de forma explícita:

1. **Lo que el dev integrador SÍ puede configurar** — decisiones de producto de *su* app (mostrar fallback al host, recuperación, branding, copy).
2. **Lo que NO se puede tocar ni queriendo** — los invariantes que, si se relajan, vacían la promesa del SDK.

Esta separación hoy está implícita en el código pero no documentada como decisión, lo que deja la puerta abierta a que un "yo del futuro" añada un knob por "flexibilidad" y rompa la garantía sin darse cuenta.

### Estado real del core (verificado 2026-06-20)

- El mecanismo de autenticación es **`BiometricPrompt` + AndroidKeyStore (AES-256-GCM)**, NO Credential Manager ni passkeys FIDO. El nombre comercial `PasskeyAuth` no implica passkeys en el sentido WebAuthn/FIDO2; describe autenticación passwordless biométrica. Una eventual migración a Credential Manager sería un ADR futuro y un cambio de capa, no el estado actual.
- El invariante de biometría fuerte **ya se cumple en código**: [`AndroidBiometricAuthenticator`](../../passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/core/auth/AndroidBiometricAuthenticator.kt) usa `setAllowedAuthenticators(BIOMETRIC_STRONG)` y nunca `DEVICE_CREDENTIAL`. El `BiometricPrompt` ni siquiera ofrece el botón de "usar PIN".
- La validación de capacidad biométrica **ya existe** en `AndroidBiometricAuthenticator.validateBiometricCapabilities()`, que mapea `BiometricManager.canAuthenticate(BIOMETRIC_STRONG)` a `BiometricException` tipadas (`NoneEnrolled`, `HardwareNotAvailable`, `HardwareUnavailable`, `SecurityUpdateRequired`). Pero esa lógica es **lanzante** y vive en una clase `internal` que requiere `FragmentActivity` — la UI no puede consultarla para dirigir estados sin capturar excepciones, que es un antipatrón para flujo normal.
- El `PasskeyAuthConfig` actual expone `requireStrongBox: Boolean` y `sessionTimeoutMinutes: Int` (con objetos `Default`, `Debug` y clase `Custom`). El timeout granular está implementado, documentado y cubierto por `PasskeyAuthFacadeTest`.

---

## Decisión

### 1. Invariantes NO configurables (la garantía)

Estos tres invariantes son la propuesta de valor del SDK y **no se exponen como knobs jamás**. Quedan escritos como comentario en el propio `PasskeyAuthConfig.kt` para que ningún cambio futuro los erosione "por flexibilidad":

```kotlin
// Invariantes NO configurables — son la garantía del SDK, no un knob:
//  · Verificación = BIOMETRIC_STRONG. Nunca DEVICE_CREDENTIAL en la ceremonia.
//  · Claves hardware-backed (StrongBox → TEE). No existe modo software.
//  · Sin setUserAuthenticationValidityDurationSeconds con ventana (no "recordar" la auth).
```

Concretamente, NO existirán flags como `allowDeviceCredential`, `allowSoftwareKeys` ni `authValidityWindowSeconds`. Bajar de `BIOMETRIC_STRONG` o aceptar device credential dentro de la ceremonia convertiría el "blindado" en una mentira.

### 2. Consulta de capacidad no-lanzante: `checkCapability()`

Se expone una API pública **no-lanzante** para que la UI dirija estados leyendo un valor en vez de capturando excepciones:

```kotlin
// passkeyauth-core — API pública (paquete es.fjmarlop.corpsecauth)

sealed interface PasskeyCapability {
    /** BIOMETRIC_STRONG disponible y con biometría registrada. El SDK puede operar. */
    data object Ready : PasskeyCapability

    /** Dispositivo compatible, pero el usuario no ha registrado huella/rostro.
     *  RECUPERABLE → enviar a Settings.ACTION_BIOMETRIC_ENROLL. */
    data object NotEnrolled : PasskeyCapability

    /** Sin hardware biométrico fuerte. Bloqueo real, no hay nada que hacer en la app. */
    data object NoHardware : PasskeyCapability

    /** Hardware presente pero temporalmente no disponible (p. ej. en uso por otra app). */
    data object TemporarilyUnavailable : PasskeyCapability

    /** Requiere actualización de seguridad del sistema. Bloqueo. */
    data object SecurityUpdateRequired : PasskeyCapability
}

/**
 * Consulta no-lanzante del estado de capacidad biométrica del dispositivo.
 * La UI usa esto para decidir qué estado pintar ANTES de mostrar un CTA,
 * sin capturar excepciones (antipatrón para flujo normal).
 */
fun checkCapability(context: Context): PasskeyCapability
```

La implementación reutiliza la lógica de mapeo `canAuthenticate(BIOMETRIC_STRONG)` que ya vive en `AndroidBiometricAuthenticator`, extrayéndola a una función compartida (la versión lanzante de la ceremonia y la no-lanzante de la consulta producen el mismo veredicto desde la misma fuente). `BiometricManager.from(context)` acepta un `Context`, así que `checkCapability` no requiere `FragmentActivity`.

### 3. Fusión de `PasskeyAuthConfig` (no breaking, sin pérdida de feature)

Se **fusiona** el contrato propuesto con el existente, conservando el timeout granular ya testeado y añadiendo los flags de política de integración:

```kotlin
class PasskeyAuthConfig(
    // Decisión de SU app: ¿devuelvo el control al host cuando no puedo operar?
    // Default estricto: off (modo blindado, sin escape hatch al login del host).
    val allowHostFallback: Boolean = false,

    // Política de hardware: Required bloquea dispositivos sin StrongBox (alta seguridad);
    // Preferred cae a TEE con normalidad. Reemplaza el antiguo requireStrongBox: Boolean.
    val strongBox: StrongBoxPolicy = StrongBoxPolicy.Preferred,

    // Recuperación controlada y auditable. null = sin recuperación in-app.
    // NUNCA es el PIN del dispositivo; es re-aprovisionamiento server-side.
    val recovery: RecoveryHandler? = null,

    // Minutos de inactividad antes de requerir biometría de nuevo.
    // 0 = siempre pide; >0 = ventana; -1 = nunca invalida (solo testing).
    // CONSERVADO del contrato actual — feature ya implementada y testeada.
    val sessionTimeoutMinutes: Int = 5,
)

enum class StrongBoxPolicy { Preferred, Required }

/** Handler de recuperación controlada y auditable (re-aprovisionamiento server-side). */
fun interface RecoveryHandler {
    suspend fun recover(context: Context): Result<Unit>
}
```

**Compatibilidad:** `requireStrongBox: Boolean` se deriva de `strongBox` (`Required` ⇒ `true`) y se mantiene como propiedad de solo lectura `@Deprecated` durante una versión para no romper a `PasskeyAuth.createKeyStoreManager()` ni a los tests existentes de golpe. Los objetos `Default` / `Debug` / `Custom` se preservan reexpresados sobre el nuevo constructor.

### Reparto de responsabilidad (resumen)

| Capa | Qué decide | Ejemplos |
|---|---|---|
| **Núcleo de seguridad** | Fijo, opinado, sin escape | `BIOMETRIC_STRONG`, hardware-backed, sin validity window |
| **Política de integración** (`PasskeyAuthConfig`, core) | Del dev, defaults estrictos | `allowHostFallback=false`, `strongBox`, `recovery`, `sessionTimeoutMinutes` |
| **UI / branding** (`passkeyauth-ui`, ADR-014) | Totalmente del dev | logo, colores, copy, light/dark |

La regla: el branding **nunca** entra en `PasskeyAuthConfig` (arrastraría conceptos de UI —y la tentación de una dependencia de Compose— al core, justo lo que el split de módulos evita). Core decide *qué se permite*; `-ui` decide *cómo se ve*.

---

## Justificación

### Por qué los invariantes son una feature, no una limitación

"No puedes debilitar la auth ni queriendo" es un argumento de venta en enterprise. La rigidez del núcleo ES la propuesta de valor. Un buen SDK expone pocos knobs, con defaults seguros, y se reserva los que comprometerían su garantía.

### Por qué `allowHostFallback` SÍ es un knob legítimo (y `allowDeviceCredential` NO)

- `allowHostFallback` vive **fuera de la frontera de confianza del SDK**: significa "PasskeyAuth no puede ejecutarse aquí → devuelvo el control a la app anfitriona". Lo que el host haga después (su propio login) es responsabilidad y pasivo del host, no del SDK. Es una decisión de producto del dev → legítimamente configurable.
- `allowDeviceCredential` viviría **dentro de la ceremonia**: permitiría desbloquear la clave hardware-backed con un PIN "1234" en vez de biometría fuerte. Eso rompe el invariante → no es flexibilidad, es dispararse en el pie. No existe.

### Por qué la recuperación NO es el lock screen del dispositivo

Si se prohíbe device credential por completo (correcto), se pierde la red de recuperación nativa de Android (la biometría falla por corte en el dedo, sensor roto, o lockout permanente). En enterprise eso es un torrente de tickets a IT. La solución no es "sin escape hatch", sino que el escape hatch sea **controlado, server-side y auditable** (re-aprovisionamiento / magic link con su log y su política) — de ahí `RecoveryHandler`, no el PIN local.

### Por qué `checkCapability()` no-lanzante

Dirigir UI capturando excepciones para flujo normal es un antipatrón: mezcla control de flujo con manejo de errores y obliga a `try/catch` en la capa visual. Una `sealed interface` consultada con `when` mapea directamente a los estados de pantalla (ADR-014) sin decisiones ad-hoc.

---

## Alternativas Consideradas

### 1. Adoptar el `PasskeyAuthConfig` propuesto tal cual (4 flags, sin `sessionTimeoutMinutes`)

**Rechazado porque:** sustituir `sessionTimeoutMinutes: Int` por `reauthOnResume: Boolean` es un **breaking change con regresión de feature**. El timeout granular (0/2/5/15/-1 min) ya está implementado, documentado en README y cubierto por `PasskeyAuthFacadeTest`. La fusión conserva la feature a coste de un flag más.

### 2. Dejar la validación de capacidad solo como excepciones (sin `checkCapability`)

**Rechazado porque:** fuerza a la UI a `try/catch` para flujo normal y no permite pintar `NotEnrolled` vs `NoHardware` (recuperable vs bloqueo) antes de mostrar un CTA.

### 3. Meter branding (logo/colores) en `PasskeyAuthConfig`

**Rechazado porque:** arrastraría conceptos de UI al core y tentaría a añadir una dependencia de Compose a `passkeyauth-core`, violando el split de módulos (ADR-001, ADR-008). El branding vive en el theme de `-ui` (ADR-014).

---

## Consecuencias

### Positivas

- ✅ Contrato del SDK fijado antes de construir UI → la UI no churna.
- ✅ Garantía de seguridad escrita en código (comentario de invariantes) y en ADR.
- ✅ `checkCapability()` deja la UI dirigida por datos, sin `try/catch` de flujo.
- ✅ Sin pérdida de feature (timeout granular conservado) y sin breaking change duro (alias `@Deprecated`).
- ✅ Política de recuperación auditable prevista desde el contrato.

### Negativas

- ⚠️ `PasskeyAuthConfig` pasa de `sealed class` con objetos a `class` con defaults — hay que reexpresar `Default`/`Debug`/`Custom` y migrar usos en `PasskeyAuth.kt` + tests.
- ⚠️ `RecoveryHandler` se introduce como contrato pero su implementación real (server-side) queda fuera de v0.3 — riesgo de API prematura. Mitigado: es `null` por defecto y `fun interface` mínima.

### Neutral

- ⚪ El consumidor que solo usa `PasskeyAuthConfig.Default` no nota cambios.
- ⚪ No se introduce Credential Manager; el nombre del SDK se mantiene.

---

## Referencias

- ADR-001 — Estructura multi-módulo
- ADR-004 — KeyStoreManager AES-GCM y StrongBox
- ADR-008 — Separación Core/UI
- ADR-009 — Client-Side Security Responsibility
- ADR-014 — Módulo UI: integración híbrida y theming (consumidor de este contrato)
- `docs/plans/2026-06-20-passkeyauth-ui-module.md` — Plan de implementación

---

## Revisiones

- **2026-06-20:** Creación. Decisión de fusionar config (conservar `sessionTimeoutMinutes`) tomada por el autor. Naming/auth: documentar la realidad (BiometricPrompt + KeyStore); Credential Manager queda como posible migración futura sin comprometer.
