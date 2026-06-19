# PasskeyAuth UI Module Implementation Plan

> **For agentic workers:** Use `mobiai-mobile-executing-plans-with-subagents` (recommended) or `mobiai-mobile-executing-plans` to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Construir el módulo `passkeyauth-ui` con integración híbrida (composables primitivos + launcher fino), theming zero-config vía `CompositionLocal`, y una pantalla de entrada dirigida por estado con 6 estados — sobre el contrato de core fijado en ADR-013.

**Architecture:** Tres capas. (1) **Core foundations** — exponer `checkCapability()`/`PasskeyCapability` y fusionar `PasskeyAuthConfig` (ADR-013). (2) **UI theme + primitivos** en `passkeyauth-ui` — `PasskeyAuthTheme` deriva de `MaterialTheme` por defecto; `PasskeySignInScreen`/`PasskeyEnrollScreen` son composables únicos dirigidos por `sealed interface`. (3) **Launcher híbrido** — `ActivityResultContract` fino encima. El momento biométrico lo pinta el sistema (`BiometricPrompt`); la UI es el chrome.

**Tech Stack:** Kotlin 2.2.10, AGP 9.0.0, Jetpack Compose (BOM 2024.12.01), Material3, `activity-compose`, minSdk 26, JDK 17. Tests: Robolectric 4.14 + MockK 1.13.13 + Truth 1.4.4 + Compose UI Test (`ui-test-junit4`).

**Platform:** Android

---

## Mapa de ficheros

**Core (`passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/`):**
- Crear: `PasskeyCapability.kt`, `StrongBoxPolicy.kt`, `RecoveryHandler.kt`
- Modificar: `PasskeyAuthConfig.kt` (fusión), `PasskeyAuth.kt` (`checkCapability()` + lectura de `strongBox`), `core/auth/AndroidBiometricAuthenticator.kt` (extraer mapeo de capacidad)

**UI (`passkeyauth-ui/src/main/java/es/fjmarlop/corpsecauth/ui/`):**
- Crear: `theme/PasskeyAuthColors.kt`, `theme/PasskeyAuthBranding.kt`, `theme/PasskeyAuthTheme.kt`
- Crear: `signin/PasskeyUiState.kt`, `signin/PasskeySignInScreen.kt`, `signin/PasskeySignInScaffold.kt`
- Crear: `enroll/PasskeyEnrollScreen.kt`
- Crear: `launcher/PasskeyAuthContract.kt`
- Modificar: `passkeyauth-ui/build.gradle.kts` (añadir `activity-compose`), `src/main/res/values/strings.xml`

**Tests:**
- Core: `passkeyauth-core/src/test/java/es/fjmarlop/corpsecauth/PasskeyCapabilityTest.kt`, `PasskeyAuthConfigTest.kt`
- UI: `passkeyauth-ui/src/test/java/es/fjmarlop/corpsecauth/ui/signin/PasskeySignInScreenTest.kt`

**Catálogo de versiones (`gradle/libs.versions.toml`):**
- Modificar: añadir `androidx-activity-compose` al uso del módulo `-ui` (la lib ya existe en el catálogo, línea 56).

---

# Fase A — Core foundations (precede a toda la UI)

### Task A1: `StrongBoxPolicy` + `RecoveryHandler`

**Files:**
- Create: `passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/StrongBoxPolicy.kt`
- Create: `passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/RecoveryHandler.kt`

- [ ] **Step 1: Crear `StrongBoxPolicy`**

```kotlin
package es.fjmarlop.corpsecauth

/**
 * Política de uso de StrongBox para las claves hardware-backed del SDK.
 *
 * - [Preferred]: intenta StrongBox, cae a TEE con normalidad si no está disponible.
 * - [Required]: exige StrongBox; falla en dispositivos que no lo tengan (alta seguridad).
 *
 * Reemplaza el antiguo `requireStrongBox: Boolean` (ver ADR-013).
 */
enum class StrongBoxPolicy { Preferred, Required }
```

- [ ] **Step 2: Crear `RecoveryHandler`**

```kotlin
package es.fjmarlop.corpsecauth

import android.content.Context

/**
 * Handler de recuperación controlada y auditable.
 *
 * SEGURIDAD (ADR-013): la recuperación NUNCA es el PIN/patrón del dispositivo.
 * Es re-aprovisionamiento server-side (magic link / re-provisioning) con su log
 * y su política. Lo provee el dev integrador; null = sin recuperación in-app.
 */
fun interface RecoveryHandler {
    suspend fun recover(context: Context): Result<Unit>
}
```

- [ ] **Step 3: Commit**

```bash
git add passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/StrongBoxPolicy.kt passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/RecoveryHandler.kt
git commit -m "feat(core): StrongBoxPolicy y RecoveryHandler (ADR-013)"
```

---

### Task A2: `PasskeyCapability` + extracción del mapeo de capacidad

**Files:**
- Create: `passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/PasskeyCapability.kt`
- Modify: `passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/core/auth/AndroidBiometricAuthenticator.kt:44-101`

- [ ] **Step 1: Escribir el test fallido (mapeo de códigos `BiometricManager` → `PasskeyCapability`)**

`passkeyauth-core/src/test/java/es/fjmarlop/corpsecauth/PasskeyCapabilityTest.kt`:

```kotlin
package es.fjmarlop.corpsecauth

import androidx.biometric.BiometricManager
import com.google.common.truth.Truth.assertThat
import es.fjmarlop.corpsecauth.core.auth.mapCanAuthenticateToCapability
import org.junit.Test

internal class PasskeyCapabilityTest {

    @Test
    fun `BIOMETRIC_SUCCESS mapea a Ready`() {
        assertThat(mapCanAuthenticateToCapability(BiometricManager.BIOMETRIC_SUCCESS))
            .isEqualTo(PasskeyCapability.Ready)
    }

    @Test
    fun `NONE_ENROLLED mapea a NotEnrolled (recuperable)`() {
        assertThat(mapCanAuthenticateToCapability(BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED))
            .isEqualTo(PasskeyCapability.NotEnrolled)
    }

    @Test
    fun `NO_HARDWARE mapea a NoHardware`() {
        assertThat(mapCanAuthenticateToCapability(BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE))
            .isEqualTo(PasskeyCapability.NoHardware)
    }

    @Test
    fun `HW_UNAVAILABLE mapea a TemporarilyUnavailable`() {
        assertThat(mapCanAuthenticateToCapability(BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE))
            .isEqualTo(PasskeyCapability.TemporarilyUnavailable)
    }

    @Test
    fun `SECURITY_UPDATE_REQUIRED mapea a SecurityUpdateRequired`() {
        assertThat(mapCanAuthenticateToCapability(BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED))
            .isEqualTo(PasskeyCapability.SecurityUpdateRequired)
    }
}
```

- [ ] **Step 2: Ejecutar y verificar que falla**

Run: `./gradlew :passkeyauth-core:testDebugUnitTest --tests "es.fjmarlop.corpsecauth.PasskeyCapabilityTest"`
Expected: FAIL — `PasskeyCapability` y `mapCanAuthenticateToCapability` no existen.

- [ ] **Step 3: Crear `PasskeyCapability`**

```kotlin
package es.fjmarlop.corpsecauth

/**
 * Estado de capacidad biométrica del dispositivo (consulta no-lanzante).
 * La UI usa esto para decidir qué pintar ANTES de mostrar un CTA (ver ADR-013/ADR-014).
 */
sealed interface PasskeyCapability {
    /** BIOMETRIC_STRONG disponible y con biometría registrada. */
    data object Ready : PasskeyCapability
    /** Compatible pero sin biometría registrada. RECUPERABLE → ACTION_BIOMETRIC_ENROLL. */
    data object NotEnrolled : PasskeyCapability
    /** Sin hardware biométrico fuerte. Bloqueo real. */
    data object NoHardware : PasskeyCapability
    /** Hardware presente pero temporalmente no disponible. */
    data object TemporarilyUnavailable : PasskeyCapability
    /** Requiere actualización de seguridad del sistema. Bloqueo. */
    data object SecurityUpdateRequired : PasskeyCapability
}
```

- [ ] **Step 4: Extraer el mapeo en `AndroidBiometricAuthenticator.kt`**

Añadir una función `internal` top-level en el mismo fichero (paquete `es.fjmarlop.corpsecauth.core.auth`) y reescribir `validateBiometricCapabilities()` para reutilizarla:

```kotlin
import es.fjmarlop.corpsecauth.PasskeyCapability

/**
 * Mapea el código de [BiometricManager.canAuthenticate] a [PasskeyCapability].
 * Fuente única de verdad compartida entre la ceremonia (lanzante) y
 * PasskeyAuth.checkCapability() (no-lanzante). Ver ADR-013.
 */
internal fun mapCanAuthenticateToCapability(canAuthenticate: Int): PasskeyCapability =
    when (canAuthenticate) {
        BiometricManager.BIOMETRIC_SUCCESS -> PasskeyCapability.Ready
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> PasskeyCapability.NotEnrolled
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> PasskeyCapability.NoHardware
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> PasskeyCapability.TemporarilyUnavailable
        BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> PasskeyCapability.SecurityUpdateRequired
        else -> PasskeyCapability.NoHardware
    }
```

`validateBiometricCapabilities()` mantiene su contrato lanzante (`Result<Unit>` con `BiometricException`), pero su `when` ahora puede delegar el veredicto a `mapCanAuthenticateToCapability` para no duplicar la clasificación. Conservar los mensajes y excepciones existentes.

- [ ] **Step 5: Ejecutar y verificar que pasa**

Run: `./gradlew :passkeyauth-core:testDebugUnitTest --tests "es.fjmarlop.corpsecauth.PasskeyCapabilityTest"`
Expected: PASS (5 tests).

- [ ] **Step 6: Commit**

```bash
git add passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/PasskeyCapability.kt passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/core/auth/AndroidBiometricAuthenticator.kt passkeyauth-core/src/test/java/es/fjmarlop/corpsecauth/PasskeyCapabilityTest.kt
git commit -m "feat(core): PasskeyCapability + mapeo compartido de capacidad (ADR-013)"
```

---

### Task A3: `PasskeyAuth.checkCapability(context)`

**Files:**
- Modify: `passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/PasskeyAuth.kt`
- Test: `passkeyauth-core/src/test/java/es/fjmarlop/corpsecauth/PasskeyCapabilityTest.kt` (añadir caso Robolectric)

- [ ] **Step 1: Añadir test Robolectric de `checkCapability`**

Crear `passkeyauth-core/src/test/java/es/fjmarlop/corpsecauth/CheckCapabilityRobolectricTest.kt`:

```kotlin
package es.fjmarlop.corpsecauth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class CheckCapabilityRobolectricTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `checkCapability no lanza y devuelve un PasskeyCapability`() {
        // Robolectric sin biometría configurada → NoHardware o NotEnrolled,
        // pero NUNCA debe lanzar (contrato no-lanzante).
        val capability = PasskeyAuth.checkCapability(context)
        assertThat(capability).isInstanceOf(PasskeyCapability::class.java)
    }
}
```

- [ ] **Step 2: Ejecutar y verificar que falla**

Run: `./gradlew :passkeyauth-core:testDebugUnitTest --tests "es.fjmarlop.corpsecauth.CheckCapabilityRobolectricTest"`
Expected: FAIL — `checkCapability` no existe.

- [ ] **Step 3: Implementar `checkCapability` en `PasskeyAuth.kt`**

```kotlin
import androidx.biometric.BiometricManager
import es.fjmarlop.corpsecauth.core.auth.mapCanAuthenticateToCapability

/**
 * Consulta no-lanzante del estado de capacidad biométrica.
 * La UI dirige estados leyendo esto, sin capturar excepciones. Ver ADR-013.
 */
fun checkCapability(context: Context): PasskeyCapability {
    val code = BiometricManager.from(context)
        .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
    return mapCanAuthenticateToCapability(code)
}
```

- [ ] **Step 4: Ejecutar y verificar que pasa**

Run: `./gradlew :passkeyauth-core:testDebugUnitTest --tests "es.fjmarlop.corpsecauth.CheckCapabilityRobolectricTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/PasskeyAuth.kt passkeyauth-core/src/test/java/es/fjmarlop/corpsecauth/CheckCapabilityRobolectricTest.kt
git commit -m "feat(core): PasskeyAuth.checkCapability() no-lanzante (ADR-013)"
```

---

### Task A4: Fusionar `PasskeyAuthConfig`

**Files:**
- Modify: `passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/PasskeyAuthConfig.kt`
- Modify: `passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/PasskeyAuth.kt:391-398` (`createKeyStoreManager`)
- Test: `passkeyauth-core/src/test/java/es/fjmarlop/corpsecauth/PasskeyAuthConfigTest.kt`

- [ ] **Step 1: Escribir el test fallido (compat + nuevos flags)**

```kotlin
package es.fjmarlop.corpsecauth

import com.google.common.truth.Truth.assertThat
import org.junit.Test

internal class PasskeyAuthConfigTest {

    @Test
    fun `Default conserva sessionTimeout y aplica defaults estrictos`() {
        val cfg = PasskeyAuthConfig.Default
        assertThat(cfg.allowHostFallback).isFalse()
        assertThat(cfg.strongBox).isEqualTo(StrongBoxPolicy.Preferred)
        assertThat(cfg.recovery).isNull()
        assertThat(cfg.sessionTimeoutMinutes).isEqualTo(2)
    }

    @Test
    fun `strongBox Required deriva requireStrongBox true (compat)`() {
        val cfg = PasskeyAuthConfig.Custom(strongBox = StrongBoxPolicy.Required)
        @Suppress("DEPRECATION")
        assertThat(cfg.requireStrongBox).isTrue()
    }

    @Test
    fun `strongBox Preferred deriva requireStrongBox false (compat)`() {
        val cfg = PasskeyAuthConfig.Custom(strongBox = StrongBoxPolicy.Preferred)
        @Suppress("DEPRECATION")
        assertThat(cfg.requireStrongBox).isFalse()
    }

    @Test
    fun `Custom acepta allowHostFallback`() {
        val cfg = PasskeyAuthConfig.Custom(allowHostFallback = true)
        assertThat(cfg.allowHostFallback).isTrue()
    }
}
```

- [ ] **Step 2: Ejecutar y verificar que falla**

Run: `./gradlew :passkeyauth-core:testDebugUnitTest --tests "es.fjmarlop.corpsecauth.PasskeyAuthConfigTest"`
Expected: FAIL — `allowHostFallback`, `strongBox`, `recovery` no existen.

- [ ] **Step 3: Reescribir `PasskeyAuthConfig.kt`**

```kotlin
package es.fjmarlop.corpsecauth

import android.content.Context

/**
 * Configuración de PasskeyAuth SDK: seguridad y política de integración.
 *
 * Invariantes NO configurables — son la garantía del SDK, no un knob (ADR-013):
 *  · Verificación = BIOMETRIC_STRONG. Nunca DEVICE_CREDENTIAL en la ceremonia.
 *  · Claves hardware-backed (StrongBox → TEE). No existe modo software.
 *  · Sin setUserAuthenticationValidityDurationSeconds con ventana.
 *
 * @property allowHostFallback Si true, cuando el SDK no puede operar (NoHardware)
 *   ofrece devolver el control al login propio del host. Default estricto: false.
 * @property strongBox Política de hardware. Reemplaza requireStrongBox.
 * @property recovery Recuperación controlada y auditable. null = sin recuperación in-app.
 * @property sessionTimeoutMinutes Minutos de inactividad antes de re-pedir biometría.
 *   0 = siempre pide; >0 = ventana; -1 = nunca invalida (solo testing).
 */
sealed class PasskeyAuthConfig(
    val allowHostFallback: Boolean,
    val strongBox: StrongBoxPolicy,
    val recovery: RecoveryHandler?,
    val sessionTimeoutMinutes: Int,
) {
    /** Compat: derivado de [strongBox]. Ver ADR-013. */
    @Deprecated("Usa strongBox: StrongBoxPolicy", ReplaceWith("strongBox"))
    val requireStrongBox: Boolean get() = strongBox == StrongBoxPolicy.Required

    /** Producción: StrongBox opcional (TEE fallback), timeout 2 min, modo blindado. */
    object Default : PasskeyAuthConfig(
        allowHostFallback = false,
        strongBox = StrongBoxPolicy.Preferred,
        recovery = null,
        sessionTimeoutMinutes = 2,
    )

    /** Debugging: timeout 0 (siempre pide biometría). */
    object Debug : PasskeyAuthConfig(
        allowHostFallback = false,
        strongBox = StrongBoxPolicy.Preferred,
        recovery = null,
        sessionTimeoutMinutes = 0,
    )

    /** Configuración personalizada con defaults estrictos. */
    class Custom(
        allowHostFallback: Boolean = false,
        strongBox: StrongBoxPolicy = StrongBoxPolicy.Preferred,
        recovery: RecoveryHandler? = null,
        sessionTimeoutMinutes: Int = 5,
    ) : PasskeyAuthConfig(allowHostFallback, strongBox, recovery, sessionTimeoutMinutes)
}
```

- [ ] **Step 4: Actualizar `createKeyStoreManager()` en `PasskeyAuth.kt`**

```kotlin
private fun createKeyStoreManager(): KeyStoreManager {
    val cfg = config ?: PasskeyAuthConfig.Default
    return if (cfg.strongBox == StrongBoxPolicy.Required) {
        KeyStoreManager.createWithStrongBox()
    } else {
        KeyStoreManager.createDefault()
    }
}
```

- [ ] **Step 5: Ejecutar TODA la suite de core (detectar fallout en tests existentes)**

Run: `./gradlew :passkeyauth-core:testDebugUnitTest`
Expected: PASS. Si `PasskeyAuthFacadeTest` usa `PasskeyAuthConfig.Custom(sessionTimeoutMinutes = ...)`, sigue compilando (named param conservado). Si algún test usa `requireStrongBox =`, sustituir por `strongBox = StrongBoxPolicy.Required`.

- [ ] **Step 6: Commit**

```bash
git add passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/PasskeyAuthConfig.kt passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/PasskeyAuth.kt passkeyauth-core/src/test/java/es/fjmarlop/corpsecauth/PasskeyAuthConfigTest.kt
git commit -m "feat(core): fusionar PasskeyAuthConfig con allowHostFallback/strongBox/recovery (ADR-013)"
```

- [ ] **Step 7: Build verification de core**

Run: `./gradlew :passkeyauth-core:assembleDebug`
Expected: BUILD SUCCESSFUL.

---

# Fase B — Theme de `passkeyauth-ui`

### Task B1: Dependencia `activity-compose`

**Files:**
- Modify: `passkeyauth-ui/build.gradle.kts`

- [ ] **Step 1: Añadir la dependencia (la lib ya está en el catálogo)**

En `dependencies { ... }` del módulo `-ui`, junto al bloque Compose:

```kotlin
implementation(libs.androidx.activity.compose)
implementation(libs.androidx.lifecycle.viewmodel.compose)
```

- [ ] **Step 2: Sync + build**

Run: `./gradlew :passkeyauth-ui:assembleDebug`
Expected: BUILD SUCCESSFUL (módulo sigue vacío de Kotlin, solo valida resolución de deps).

- [ ] **Step 3: Commit**

```bash
git add passkeyauth-ui/build.gradle.kts
git commit -m "build(ui): activity-compose + viewmodel-compose para el launcher"
```

---

### Task B2: `PasskeyAuthColors` + `PasskeyAuthBranding`

**Files:**
- Create: `passkeyauth-ui/src/main/java/es/fjmarlop/corpsecauth/ui/theme/PasskeyAuthColors.kt`
- Create: `passkeyauth-ui/src/main/java/es/fjmarlop/corpsecauth/ui/theme/PasskeyAuthBranding.kt`

- [ ] **Step 1: `PasskeyAuthColors`**

```kotlin
package es.fjmarlop.corpsecauth.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/** Tokens de color mínimos del SDK. Un solo acento (primary). Ver ADR-014. */
@Immutable
data class PasskeyAuthColors(
    val primary: Color,
    val onPrimary: Color,
    val surface: Color,
    val onSurface: Color,
    val error: Color,
) {
    companion object {
        /** Zero-config: deriva del MaterialTheme del host (se mimetiza con la app). */
        @Composable
        @ReadOnlyComposable
        fun fromMaterial(): PasskeyAuthColors = with(MaterialTheme.colorScheme) {
            PasskeyAuthColors(
                primary = primary,
                onPrimary = onPrimary,
                surface = surface,
                onSurface = onSurface,
                error = error,
            )
        }
    }
}
```

- [ ] **Step 2: `PasskeyAuthBranding`**

```kotlin
package es.fjmarlop.corpsecauth.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.painter.Painter

/** Branding opcional. El logo es un slot Painter, NUNCA un resource hardcodeado (ADR-014). */
@Immutable
data class PasskeyAuthBranding(
    val logo: Painter? = null,
    val brandName: String? = null,
)
```

- [ ] **Step 3: Commit**

```bash
git add passkeyauth-ui/src/main/java/es/fjmarlop/corpsecauth/ui/theme/
git commit -m "feat(ui): tokens PasskeyAuthColors (zero-config) + PasskeyAuthBranding"
```

---

### Task B3: `PasskeyAuthTheme` + CompositionLocals

**Files:**
- Create: `passkeyauth-ui/src/main/java/es/fjmarlop/corpsecauth/ui/theme/PasskeyAuthTheme.kt`

- [ ] **Step 1: Implementar el theme**

```kotlin
package es.fjmarlop.corpsecauth.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalPasskeyColors = compositionLocalOf<PasskeyAuthColors> {
    error("PasskeyAuthColors no provistos: envuelve el contenido en PasskeyAuthTheme { }")
}
private val LocalPasskeyBranding = staticCompositionLocalOf { PasskeyAuthBranding() }

/** Acceso a los tokens del SDK desde cualquier composable hijo. */
object PasskeyAuthTheme {
    val colors: PasskeyAuthColors
        @Composable get() = LocalPasskeyColors.current
    val branding: PasskeyAuthBranding
        @Composable get() = LocalPasskeyBranding.current
}

/**
 * Theme del SDK. Zero-config: si no se pasan [colors], derivan del MaterialTheme
 * del host. Tipografía/shapes se heredan de Material por defecto (ADR-014).
 */
@Composable
fun PasskeyAuthTheme(
    colors: PasskeyAuthColors = PasskeyAuthColors.fromMaterial(),
    branding: PasskeyAuthBranding = PasskeyAuthBranding(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalPasskeyColors provides colors,
        LocalPasskeyBranding provides branding,
        content = content,
    )
}
```

- [ ] **Step 2: Build**

Run: `./gradlew :passkeyauth-ui:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add passkeyauth-ui/src/main/java/es/fjmarlop/corpsecauth/ui/theme/PasskeyAuthTheme.kt
git commit -m "feat(ui): PasskeyAuthTheme con CompositionLocal y default zero-config"
```

---

### Task B4: Strings i18n

**Files:**
- Modify: `passkeyauth-ui/src/main/res/values/strings.xml`

- [ ] **Step 1: Añadir las claves de copy (sobrescribibles por el host)**

```xml
<resources>
    <string name="passkey_signin_title">Acceso seguro</string>
    <string name="passkey_signin_subtitle">Usa tu biometría para entrar</string>
    <string name="passkey_signin_cta">Acceder</string>
    <string name="passkey_loading">Esperando confirmación…</string>
    <string name="passkey_error_retry">Reintentar</string>
    <string name="passkey_not_enrolled_title">Configura tu acceso</string>
    <string name="passkey_not_enrolled_subtitle">Registra tu huella o rostro para continuar</string>
    <string name="passkey_not_enrolled_cta">Configurar biometría</string>
    <string name="passkey_no_hardware_title">Dispositivo no compatible</string>
    <string name="passkey_no_hardware_subtitle">Este dispositivo no admite autenticación biométrica fuerte</string>
    <string name="passkey_host_fallback_cta">Usar otro método</string>
</resources>
```

- [ ] **Step 2: Commit**

```bash
git add passkeyauth-ui/src/main/res/values/strings.xml
git commit -m "feat(ui): strings i18n del módulo de UI"
```

---

# Fase C — Composables primitivos

### Task C1: `PasskeyUiState`

**Files:**
- Create: `passkeyauth-ui/src/main/java/es/fjmarlop/corpsecauth/ui/signin/PasskeyUiState.kt`

- [ ] **Step 1: Definir el estado de UI (capa visual, distinta de los modelos de core)**

```kotlin
package es.fjmarlop.corpsecauth.ui.signin

import es.fjmarlop.corpsecauth.PasskeyCapability

/**
 * Estado de la pantalla de entrada. UN composable dirigido por este `when` (ADR-014).
 * Distinto de AuthResult/PasskeyCapability del core: es la proyección visual.
 */
sealed interface PasskeyUiState {
    data object Idle : PasskeyUiState
    data object Loading : PasskeyUiState
    data class Error(val message: String) : PasskeyUiState
    data object Success : PasskeyUiState
    data object NotEnrolled : PasskeyUiState
    data object NoHardware : PasskeyUiState

    companion object {
        /** Deriva el estado inicial de la capacidad del dispositivo. */
        fun from(capability: PasskeyCapability): PasskeyUiState = when (capability) {
            PasskeyCapability.Ready -> Idle
            PasskeyCapability.NotEnrolled -> NotEnrolled
            PasskeyCapability.NoHardware,
            PasskeyCapability.SecurityUpdateRequired -> NoHardware
            PasskeyCapability.TemporarilyUnavailable -> Error("Biometría temporalmente no disponible")
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add passkeyauth-ui/src/main/java/es/fjmarlop/corpsecauth/ui/signin/PasskeyUiState.kt
git commit -m "feat(ui): PasskeyUiState (6 estados de la pantalla de entrada)"
```

---

### Task C2: `PasskeySignInScaffold` (UI pura, sin lógica) + tests Compose

**Files:**
- Create: `passkeyauth-ui/src/main/java/es/fjmarlop/corpsecauth/ui/signin/PasskeySignInScaffold.kt`
- Test: `passkeyauth-ui/src/test/java/es/fjmarlop/corpsecauth/ui/signin/PasskeySignInScaffoldTest.kt`

- [ ] **Step 1: Test Compose (Robolectric) del scaffold por estado**

```kotlin
package es.fjmarlop.corpsecauth.ui.signin

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import es.fjmarlop.corpsecauth.ui.theme.PasskeyAuthTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class PasskeySignInScaffoldTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `estado Idle muestra el CTA primario de acceso`() {
        composeRule.setContent {
            androidx.compose.material3.MaterialTheme {
                PasskeyAuthTheme {
                    PasskeySignInScaffold(
                        state = PasskeyUiState.Idle,
                        allowHostFallback = false,
                        onPrimaryAction = {},
                        onHostFallback = {},
                    )
                }
            }
        }
        composeRule.onNodeWithText("Acceder").assertIsDisplayed()
    }

    @Test
    fun `estado NoHardware sin fallback NO muestra boton de otro metodo`() {
        composeRule.setContent {
            androidx.compose.material3.MaterialTheme {
                PasskeyAuthTheme {
                    PasskeySignInScaffold(
                        state = PasskeyUiState.NoHardware,
                        allowHostFallback = false,
                        onPrimaryAction = {},
                        onHostFallback = {},
                    )
                }
            }
        }
        composeRule.onNodeWithText("Usar otro método").assertDoesNotExist()
    }
}
```

- [ ] **Step 2: Ejecutar y verificar que falla**

Run: `./gradlew :passkeyauth-ui:testDebugUnitTest --tests "*PasskeySignInScaffoldTest"`
Expected: FAIL — `PasskeySignInScaffold` no existe.

> **Nota de entorno:** los tests Compose con Robolectric requieren `testOptions { unitTests.isIncludeAndroidResources = true }` en el `android {}` del módulo `-ui`. Añadirlo si el build falla por recursos.

- [ ] **Step 3: Implementar el scaffold (UI pura dirigida por estado)**

```kotlin
package es.fjmarlop.corpsecauth.ui.signin

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import es.fjmarlop.corpsecauth.ui.R
import es.fjmarlop.corpsecauth.ui.theme.PasskeyAuthTheme

/**
 * Chrome de la pantalla de entrada: logo → icono → título → subtítulo → CTA.
 * UI pura sin lógica de auth (testeable con Compose UI test). Ver ADR-014.
 * Slots header/footer = escape hatch avanzado.
 */
@Composable
fun PasskeySignInScaffold(
    state: PasskeyUiState,
    allowHostFallback: Boolean,
    onPrimaryAction: () -> Unit,
    onHostFallback: () -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    val colors = PasskeyAuthTheme.colors
    val branding = PasskeyAuthTheme.branding

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        header?.invoke()
        branding.logo?.let { Icon(painter = it, contentDescription = branding.brandName, tint = androidx.compose.ui.graphics.Color.Unspecified) }
        Spacer(Modifier.height(24.dp))

        when (state) {
            PasskeyUiState.Idle -> StateBlock(
                title = stringResource(R.string.passkey_signin_title),
                subtitle = stringResource(R.string.passkey_signin_subtitle),
                ctaLabel = stringResource(R.string.passkey_signin_cta),
                onCta = onPrimaryAction,
                primaryColor = colors.primary,
                onPrimaryColor = colors.onPrimary,
            )
            PasskeyUiState.Loading -> CircularProgressIndicator(color = colors.primary)
            is PasskeyUiState.Error -> StateBlock(
                title = state.message,
                subtitle = null,
                ctaLabel = stringResource(R.string.passkey_error_retry),
                onCta = onPrimaryAction,
                primaryColor = colors.primary,
                onPrimaryColor = colors.onPrimary,
            )
            PasskeyUiState.Success -> CircularProgressIndicator(color = colors.primary)
            PasskeyUiState.NotEnrolled -> StateBlock(
                title = stringResource(R.string.passkey_not_enrolled_title),
                subtitle = stringResource(R.string.passkey_not_enrolled_subtitle),
                ctaLabel = stringResource(R.string.passkey_not_enrolled_cta),
                onCta = onPrimaryAction,
                primaryColor = colors.primary,
                onPrimaryColor = colors.onPrimary,
            )
            PasskeyUiState.NoHardware -> {
                Text(stringResource(R.string.passkey_no_hardware_title))
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.passkey_no_hardware_subtitle))
                if (allowHostFallback) {
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = onHostFallback) {
                        Text(stringResource(R.string.passkey_host_fallback_cta))
                    }
                }
            }
        }
        footer?.invoke()
    }
}

@Composable
private fun StateBlock(
    title: String,
    subtitle: String?,
    ctaLabel: String,
    onCta: () -> Unit,
    primaryColor: androidx.compose.ui.graphics.Color,
    onPrimaryColor: androidx.compose.ui.graphics.Color,
) {
    Text(title)
    subtitle?.let { Spacer(Modifier.height(8.dp)); Text(it) }
    Spacer(Modifier.height(24.dp))
    Button(
        onClick = onCta,
        colors = ButtonDefaults.buttonColors(containerColor = primaryColor, contentColor = onPrimaryColor),
    ) { Text(ctaLabel) }
}
```

- [ ] **Step 4: Ejecutar y verificar que pasa**

Run: `./gradlew :passkeyauth-ui:testDebugUnitTest --tests "*PasskeySignInScaffoldTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add passkeyauth-ui/src/main/java/es/fjmarlop/corpsecauth/ui/signin/PasskeySignInScaffold.kt passkeyauth-ui/src/test/java/es/fjmarlop/corpsecauth/ui/signin/PasskeySignInScaffoldTest.kt
git commit -m "feat(ui): PasskeySignInScaffold dirigido por estado + tests Compose"
```

> **Iteración visual:** el detalle fino (espaciados, iconografía candado/huella, tipografía) se pule en Claude Design sobre este scaffold; los tests validan contrato (qué CTA aparece por estado), no píxeles.

---

### Task C3: `PasskeySignInScreen` (composable con lógica) + `PasskeyEnrollScreen`

**Files:**
- Create: `passkeyauth-ui/src/main/java/es/fjmarlop/corpsecauth/ui/signin/PasskeySignInScreen.kt`
- Create: `passkeyauth-ui/src/main/java/es/fjmarlop/corpsecauth/ui/enroll/PasskeyEnrollScreen.kt`

- [ ] **Step 1: `PasskeySignInScreen` — cablea capacidad + ceremonia al scaffold**

```kotlin
package es.fjmarlop.corpsecauth.ui.signin

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import es.fjmarlop.corpsecauth.PasskeyAuth
import es.fjmarlop.corpsecauth.PasskeyAuthConfig

/**
 * Composable primitivo de sign-in (capa pública). La app lo mete en su nav graph.
 * Dirige estados con checkCapability() y delega la ceremonia en PasskeyAuth.authenticate().
 * El BiometricPrompt real lo pinta el sistema; esto es el chrome (ADR-014).
 */
@Composable
fun PasskeySignInScreen(
    activity: FragmentActivity,
    config: PasskeyAuthConfig,
    onAuthenticated: () -> Unit,
    onHostFallback: () -> Unit = {},
) {
    val context = LocalContext.current
    var state by remember { mutableStateOf<PasskeyUiState>(PasskeyUiState.Loading) }
    val scope = rememberCoroutineScope()

    val enrollLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { state = PasskeyUiState.from(PasskeyAuth.checkCapability(context)) }

    LaunchedEffect(Unit) {
        state = PasskeyUiState.from(PasskeyAuth.checkCapability(context))
    }

    PasskeySignInScaffold(
        state = state,
        allowHostFallback = config.allowHostFallback,
        onHostFallback = onHostFallback,
        onPrimaryAction = {
            when (state) {
                PasskeyUiState.NotEnrolled ->
                    enrollLauncher.launch(Intent(Settings.ACTION_BIOMETRIC_ENROLL))
                else -> scope.launch {
                    state = PasskeyUiState.Loading
                    PasskeyAuth.authenticate(activity)
                        .onSuccess { state = PasskeyUiState.Success; onAuthenticated() }
                        .onFailure { state = PasskeyUiState.Error(it.message ?: "Error de autenticación") }
                }
            }
        },
    )
}
```

> Nota: `import kotlinx.coroutines.launch` necesario. `ACTION_BIOMETRIC_ENROLL` requiere API 30+; en 26-29 usar `Settings.ACTION_SECURITY_SETTINGS` como fallback (añadir guard `Build.VERSION.SDK_INT`).

- [ ] **Step 2: `PasskeyEnrollScreen` — mapea `EnrollmentState`**

```kotlin
package es.fjmarlop.corpsecauth.ui.enroll

import androidx.compose.runtime.*
import androidx.fragment.app.FragmentActivity
import es.fjmarlop.corpsecauth.PasskeyAuth
import es.fjmarlop.corpsecauth.core.models.EnrollmentState
import es.fjmarlop.corpsecauth.ui.signin.PasskeySignInScaffold
import es.fjmarlop.corpsecauth.ui.signin.PasskeyUiState

/**
 * Composable de enrollment. Reutiliza el scaffold y mapea EnrollmentState → PasskeyUiState.
 */
@Composable
fun PasskeyEnrollScreen(
    activity: FragmentActivity,
    email: String,
    temporaryPassword: String,
    onEnrolled: () -> Unit,
) {
    var state by remember { mutableStateOf<PasskeyUiState>(PasskeyUiState.Idle) }

    LaunchedEffect(email) {
        PasskeyAuth.enrollDevice(activity, email, temporaryPassword).collect { es ->
            state = when (es) {
                is EnrollmentState.Success -> { onEnrolled(); PasskeyUiState.Success }
                is EnrollmentState.Error -> PasskeyUiState.Error(es.exception.message ?: "Error en enrollment")
                EnrollmentState.Idle -> PasskeyUiState.Idle
                else -> PasskeyUiState.Loading // ValidatingCredentials, GeneratingCryptoKey, AwaitingBiometric, BindingDevice...
            }
        }
    }

    PasskeySignInScaffold(
        state = state,
        allowHostFallback = false,
        onPrimaryAction = {},
        onHostFallback = {},
    )
}
```

- [ ] **Step 3: Build**

Run: `./gradlew :passkeyauth-ui:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add passkeyauth-ui/src/main/java/es/fjmarlop/corpsecauth/ui/signin/PasskeySignInScreen.kt passkeyauth-ui/src/main/java/es/fjmarlop/corpsecauth/ui/enroll/PasskeyEnrollScreen.kt
git commit -m "feat(ui): PasskeySignInScreen y PasskeyEnrollScreen (composables primitivos)"
```

---

# Fase D — Launcher híbrido

### Task D1: `ActivityResultContract` de una línea

**Files:**
- Create: `passkeyauth-ui/src/main/java/es/fjmarlop/corpsecauth/ui/launcher/PasskeyAuthContract.kt`

- [ ] **Step 1: Definir el resultado + contrato**

```kotlin
package es.fjmarlop.corpsecauth.ui.launcher

/** Resultado del launcher híbrido. */
sealed interface PasskeyAuthResult {
    data object Authenticated : PasskeyAuthResult
    data object Cancelled : PasskeyAuthResult
    data class Failed(val reason: String) : PasskeyAuthResult
}
```

> El launcher autocontenido se implementa como una `PasskeyAuthActivity` interna (FragmentActivity) que hospeda `PasskeySignInScreen` y devuelve `PasskeyAuthResult` vía `ActivityResultContract`. La app integra con una línea:
> `val launcher = rememberLauncherForActivityResult(PasskeyAuthContract()) { result -> ... }`.
> Detalle de la Activity + manifest entry se especifica al ejecutar esta fase (depende de cómo el sample la consuma).

- [ ] **Step 2: Commit**

```bash
git add passkeyauth-ui/src/main/java/es/fjmarlop/corpsecauth/ui/launcher/PasskeyAuthContract.kt
git commit -m "feat(ui): PasskeyAuthResult (base del launcher híbrido)"
```

---

# Fase E — Sample (first-run experience) + verificación

### Task E1: Cablear el launcher en el sample

**Files:**
- Modify: archivos del módulo `sample` (pantalla de demo)

- [ ] **Step 1:** Reemplazar/añadir en el sample una pantalla que use `PasskeySignInScreen` envuelto en `PasskeyAuthTheme` (zero-config) para validar el first-run experience end-to-end en device.

- [ ] **Step 2: Build del sample**

Run: `./gradlew :sample:assembleDebug`
Expected: BUILD SUCCESSFUL (requiere `google-services.json` local).

- [ ] **Step 3: Commit**

```bash
git add sample/
git commit -m "feat(sample): first-run experience con PasskeySignInScreen"
```

---

### Task E2: Verificación final

- [ ] **Step 1: Suite completa**

Run: `./gradlew :passkeyauth-core:testDebugUnitTest :passkeyauth-ui:testDebugUnitTest :passkeyauth-lint:test`
Expected: PASS. Core mantiene los 73 previos + nuevos (PasskeyCapability, checkCapability, config). UI añade los Compose tests.

- [ ] **Step 2: Build agregado**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Smoke test manual en device** — sign-in zero-config, override de branding/colores, y los seis estados (forzar `NotEnrolled` quitando huella, `NoHardware` en emulador sin biometría).

---

## Self-Review

**Spec coverage:**
- Híbrido (composables + launcher) → Fase C (composables) + Fase D (launcher). ✅
- Theming `CompositionLocal` zero-config → Task B2/B3. ✅
- Logo `Painter?` slot → `PasskeyAuthBranding`. ✅
- 6 estados, un composable → Task C1/C2 (`PasskeyUiState` + `when` en scaffold). ✅
- `NotEnrolled` → `ACTION_BIOMETRIC_ENROLL` → Task C3. ✅
- `NoHardware` + `allowHostFallback` → Task C2 (test verifica ocultación). ✅
- Core: `checkCapability`/`PasskeyCapability` reutilizando `validateBiometricCapabilities` → Task A2/A3. ✅
- Config fusionado conservando `sessionTimeoutMinutes` → Task A4. ✅
- Invariantes no configurables documentados → en `PasskeyAuthConfig.kt` (Task A4) + ADR-013. ✅
- i18n → Task B4. ✅
- header/footer slots → Task C2. ✅

**Gaps conocidos (resolver al ejecutar):**
- API level de `ACTION_BIOMETRIC_ENROLL` (30+) necesita guard para 26-29.
- `PasskeyAuthActivity` interna del launcher (Task D1) se detalla en ejecución.
- `unitTests.isIncludeAndroidResources = true` puede requerirse para Compose+Robolectric en `-ui`.

**Type consistency:** `PasskeyCapability` (core) ↔ `PasskeyUiState.from()` (ui) cubren los 5 casos. `PasskeyAuthConfig.allowHostFallback` consumido en scaffold. `EnrollmentState` (core, verificado) mapeado en `PasskeyEnrollScreen`. ✅

---

## Orden de dependencias (resumen)

```
Fase A (core) ──► Fase B (theme) ──► Fase C (composables) ──► Fase D (launcher) ──► Fase E (sample)
   A1,A2,A3,A4       B1,B2,B3,B4         C1,C2,C3                D1                    E1,E2
```

Fase A es bloqueante de todo (la UI consume `checkCapability`/`PasskeyCapability`/`config`). Dentro de A: A1→A2→A3 secuencial (A3 usa el mapeo de A2); A4 independiente de A2/A3. Fase B independiente de A salvo que C la consume. **No paralelizar A4 con los tests de A2/A3 sin cuidado** (ambos tocan ramas de `PasskeyAuth.kt`).
