# ADR-014: Módulo `passkeyauth-ui` — Integración Híbrida, Theming y Estados

**Estado:** Aceptado
**Fecha:** 2026-06-20
**Autores:** Francisco Javier Marmolejo López

---

## Contexto

El SDK necesita una capa de UI, pero la UI **no es el core del producto**. La lectura comercial es clara: el `passkeyauth-core` es lo que se usará en producción; la UI es (a) un *servicio* para el dev que no quiere montarse sus pantallas y (b) el *first-run experience* / escaparate para probar el SDK antes de integrarlo en serio. La UI vende el core sin ser el core.

Eso impone dos restricciones de diseño:

1. **Empaquetado:** la UI no debe vivir en el mismo módulo que el core. `passkeyauth-core` no arrastra Compose; quien integre con su propia pantalla no carga peso muerto. Esto ya está resuelto en la estructura Gradle (ADR-001): `passkeyauth-ui` es un módulo aparte que `api(project(":passkeyauth-core"))`. Hoy está scaffolded (build.gradle con Compose habilitado) pero **vacío de Kotlin** (solo `AndroidManifest.xml` + `strings.xml`).
2. **Modelo de integración:** define qué se puede tematizar y cuánto control se le da al integrador. Es la decisión que condiciona todo lo demás.

El contrato de seguridad y configuración del que la UI es proyección está fijado en el [ADR-013](013-non-negotiable-security-invariants.md): `checkCapability(): PasskeyCapability`, `PasskeyAuthConfig` fusionado, e invariantes no negociables.

**Restricción técnica clave:** el momento biométrico real lo pinta el **sistema** (`BiometricPrompt`). El módulo `passkeyauth-ui` es el *chrome alrededor*: pantalla de entrada con logo + texto explicativo + CTA, y manejo de estados (loading, error con retry, éxito, fallbacks). Son menos pantallas de las que parece.

---

## Decisión

### 1. Modelo de integración: HÍBRIDO

Dos capas, una encima de la otra:

- **Composables primitivos** (capa pública, totalmente personalizable): `PasskeySignInScreen(...)`, `PasskeyEnrollScreen(...)`. La app anfitriona los mete en su propio nav graph. Máxima flexibilidad para apps Compose-native.
- **Launcher fino** (capa de conveniencia, integración de una línea) encima de los composables: para quien quiera "just works" sin cablear nada.

El híbrido da el doble discurso buscado: el launcher entrega la "UI decente por defecto" y los composables son la vía de escape para control total. Es lo que hacen los SDK maduros.

### 2. Theming: `PasskeyAuthTheme` con tokens mínimos vía `CompositionLocal`

No se depende a fuego del `MaterialTheme` del host ni se reinventa un design system. Set mínimo de tokens:

```kotlin
// passkeyauth-ui

@Immutable
data class PasskeyAuthColors(
    val primary: Color,
    val onPrimary: Color,
    val surface: Color,
    val onSurface: Color,
    val error: Color,
)

@Immutable
data class PasskeyAuthBranding(
    val logo: Painter? = null,   // slot opcional — NUNCA un drawable hardcodeado
    val brandName: String? = null,
)

@Composable
fun PasskeyAuthTheme(
    colors: PasskeyAuthColors = PasskeyAuthColors.fromMaterial(), // zero-config
    branding: PasskeyAuthBranding = PasskeyAuthBranding(),
    content: @Composable () -> Unit,
)
```

**Default inteligente (zero-config):** si no se pasa nada, `PasskeyAuthColors.fromMaterial()` deriva de `MaterialTheme.colorScheme` → la UI se mimetiza con la app sin configuración. **Override explícito:** `PasskeyAuthTheme(colors = ..., branding = ...)` para white-label. Tipografía y shapes se heredan del `MaterialTheme` del host por defecto (no se duplican tokens que Material ya da bien).

### 3. Superficie de personalización (y dónde parar)

| Se expone | Cómo |
|---|---|
| Logo opcional | `branding.logo: Painter?` (slot, nunca resource hardcodeado) |
| Tokens de color | `PasskeyAuthColors` vía theme |
| Strings + i18n | `strings.xml` del módulo, sobrescribibles por el host; copy overrides por parámetro |
| Light / dark | Sigue el sistema (deriva de `MaterialTheme`) |
| Overrides de copy | Parámetros `title`/`subtitle`/`ctaLabel` en los composables (white-label) |
| **Escape hatch avanzado** | Slots `header`/`footer` `@Composable` opcionales |

**Donde se para (scope creep, NO se expone):** shapes configurables, variantes de layout, animaciones custom. La UI no es el core; añadir esto es complejidad sin retorno.

### 4. Pantalla de entrada: UN composable dirigido por estado, 6 estados

`PasskeySignInScreen` no son seis pantallas: es un único composable con un `when` sobre el estado. Los seis estados comparten estructura (logo → icono → título → subtítulo → CTA), solo cambian icono/copy/acción:

| Estado | Origen | CTA |
|---|---|---|
| `Idle` | inicial | Primario: "Acceder" → dispara ceremonia |
| `Loading` | ceremonia en curso | Ninguno (spinner; "esperando confirmación" mientras el sistema pinta el prompt) |
| `Error` | `BiometricException` recuperable | Primario: "Reintentar" |
| `Success` | autenticación OK | Ninguno (transición de salida) |
| `NotEnrolled` | `PasskeyCapability.NotEnrolled` | Primario: "Configura tu acceso" → `Settings.ACTION_BIOMETRIC_ENROLL` |
| `NoHardware` | `PasskeyCapability.NoHardware` / `SecurityUpdateRequired` | Secundario "Usar otro método" SOLO si `allowHostFallback=true`; si no, mensaje seco de incompatibilidad sin CTA primario |

Regla de oro de UX: **nunca un CTA primario que no lleva a ningún sitio**. `NotEnrolled` es recuperable (acción real) → primario. `NoHardware` es callejón → secundario o ninguno, y se aclara que la salida la gestiona la app anfitriona.

La diferenciación `NotEnrolled` vs `NoHardware` la da `PasskeyCapability` del core (ADR-013), leída con `checkCapability()` antes de mostrar el CTA. El affordance "Usar otro método" se muestra/oculta según `config.allowHostFallback` — cero decisiones ad-hoc en la capa visual.

### 5. Dirección visual

Minimal + profesional para algo de seguridad: mucho blanco, **un solo acento** (`colors.primary`), jerarquía tipográfica clara, iconografía sobria (candado/huella), logo arriba, un CTA primario. Que transmita solidez, no que sea vistoso.

---

## Justificación

- **Híbrido vs solo-composables o solo-launcher:** los composables solos obligan al integrador a cablear todo; el launcher solo es caja negra difícil de tematizar. El híbrido cubre ambos públicos con una sola base de código (el launcher es una fachada delgada sobre los composables).
- **Tokens vía `CompositionLocal` y no parámetros por componente:** evita prop-drilling de colores por toda la jerarquía y permite el zero-config (un solo punto deriva de `MaterialTheme`).
- **Un composable con `when` y no seis pantallas:** DRY. Los seis estados comparten el 80% de la estructura; un `sealed interface PasskeyUiState` + `when` es menos código y menos superficie de bug que seis Composables paralelos.
- **El logo como `Painter?` y no resource:** un SDK no puede asumir el sistema de recursos del host (ni `R.drawable.*` del módulo). Un slot `Painter?` lo deja en manos del integrador y permite "sin logo" sin ramas especiales.

---

## Alternativas Consideradas

### 1. UI dentro de `passkeyauth-core`

**Rechazado:** arrastra Compose a todos los consumidores del core, incluidos los que traen su propia UI. Viola ADR-001/ADR-008.

### 2. Solo launcher autocontenido (`ActivityResultContract`)

**Rechazado como única opción:** integración de una línea pero caja negra; tematizar se vuelve difícil y el integrador Compose-native no puede componer. Se conserva como capa de conveniencia *encima* de los composables.

### 3. Design system propio completo (tipografía + shapes + spacing tokens)

**Rechazado:** scope creep. La UI es secundaria; heredar de `MaterialTheme` cubre el 95% de los casos con cero mantenimiento.

### 4. Tematizar con parámetros de color por composable

**Rechazado:** prop-drilling y rompe el zero-config. `CompositionLocal` centraliza.

---

## Consecuencias

### Positivas

- ✅ Core sin peso muerto de UI; integrador elige nivel de acoplamiento.
- ✅ Zero-config se mimetiza con la app del dev; white-label con un override.
- ✅ Seis estados, un composable → poco código, fácil de razonar.
- ✅ La UI es el first-run experience que vende el core.
- ✅ Decisiones de UI dirigidas por el contrato del core (ADR-013), no ad-hoc.

### Negativas

- ⚠️ Mantener dos capas (composables + launcher) aunque la segunda sea fina.
- ⚠️ i18n obligatorio en enterprise → `strings.xml` crece y hay que mantener copy legal/compliance.
- ⚠️ El módulo añade dependencia de `activity-compose` (para el `ActivityResultContract` del launcher) además del bundle Compose ya presente.

### Neutral

- ⚪ El momento biométrico sigue siendo UI del sistema; el módulo solo pinta el chrome.
- ⚪ `passkeyauth-ui` puede publicarse o no de forma independiente del core en el futuro.

---

## Referencias

- ADR-001 — Estructura multi-módulo
- ADR-008 — Separación Core/UI
- ADR-013 — Invariantes de seguridad no negociables y contrato de `PasskeyAuthConfig`
- `docs/plans/2026-06-20-passkeyauth-ui-module.md` — Plan de implementación task-by-task
- Diseño visual: sesión "Diseño UI minimalista para PasskeyAuth SDK" (Claude Design) — mockups de sign-in (4 estados base) + fallbacks (`NotEnrolled` / `NoHardware`)

---

## Revisiones

- **2026-06-20:** Creación. Modelo híbrido confirmado por el autor. Fallback al host: opt-in vía `allowHostFallback`, off por defecto (modo blindado). Pendiente de iterar el detalle visual en Claude Design sobre la pantalla de enroll con la misma gramática.
