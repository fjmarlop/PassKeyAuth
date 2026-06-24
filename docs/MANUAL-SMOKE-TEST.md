# Manual Smoke Test — BiometricPrompt y flujos end-to-end

Checklist de verificación manual que cubre lo que los tests automatizados **no pueden** validar: la interacción real del usuario con `BiometricPrompt`, comportamiento físico del sensor biométrico, y flujos completos enrollment → login → logout en device de producción.

Complementa los tests automatizados documentados en [ADR-011](adr/011-testing-stack-and-strategy.md). Los tests instrumented validan `AndroidKeyStoreManager` con hardware real, pero `BiometricPrompt` requiere interacción humana real (huella física, gestos, cancelaciones) que no se puede automatizar de forma fiable.

---

## Cuándo ejecutar este checklist

- ✅ **Antes de cada release** (incluido pre-releases / alphas)
- ✅ Después de tocar `AndroidBiometricAuthenticator`, `EnrollmentManager`, `AndroidKeyStoreManager` o el flujo del sample app
- ✅ Cuando se actualiza la dependencia `androidx.biometric` o `compileSdk`
- ⚠️ Recomendado al cambiar `BiometricConfig.Default` o `PasskeyAuthConfig`

---

## Pre-requisitos

### Setup del entorno

- [ ] Sample app firmada con debug keystore (`./gradlew installDebug`)
- [ ] `google-services.json` válido en `sample/` con proyecto Firebase configurado
- [ ] Firebase Authentication habilitado (Email/Password)
- [ ] Al menos 1 usuario creado en Firebase con email + password temporal conocidos
- [ ] `adb logcat` corriendo para capturar logs durante el smoke test

### Devices a usar

| Device | Hardware | Por qué |
|---|---|---|
| **Con StrongBox** (Pixel 3+, Samsung S20+, OnePlus 7T+) | Verificar path StrongBox real |
| **Sin StrongBox** (TEE only) | Verificar fallback TEE — mayoría de usuarios reales |
| **API 26 / 27** (mínimo soportado) | Verificar compatibilidad con API legacy de `BiometricPrompt` |
| **Foldable** (opcional, si llega el caso) | Validar lifecycle con cambios de configuración complejos |

Mínimo viable: 1 device con StrongBox + 1 device sin StrongBox.

### Estado inicial del device (antes de cada escenario)

- [ ] Al menos 1 huella digital registrada en Settings
- [ ] Lock screen configurado (PIN/pattern/password)
- [ ] Sample app desinstalada si vienes de otro escenario (`adb uninstall es.fjmarlop.corpsecauth.sample`)
- [ ] Si quieres KeyStore limpio del SDK: `adb shell pm clear es.fjmarlop.corpsecauth.sample`

---

## Captura de evidencia por escenario

Para cada escenario fallido recolectar:

1. **Logcat filtrado:**
   ```bash
   adb logcat -v time -s PasskeyAuth:V EnrollmentManager:V FirebaseAuthBackend:V KeyStoreManager:V BiometricAuthenticator:V > smoke-test-$(date +%Y%m%d-%H%M%S).log
   ```
2. **Screenshot** del estado final (`adb exec-out screencap -p > screen.png`)
3. **Anotar:** modelo del device, API level, build del sample app

---

## Escenarios

### S1 — Enrollment exitoso (happy path)

**Pre-condición:** App recién instalada, sin enrollment previo.

**Pasos:**
1. Abrir sample app → `SplashScreen` detecta no enrollado → navega a `CredentialsScreen`
2. Verificar que los campos email y contraseña temporal están presentes (prefilled con valores de test)
3. Pulsar "Continuar"
4. `PasskeyEnrollScreen` inicia la ceremonia — cuando aparezca `BiometricPrompt`, autenticar con huella registrada
5. Esperar a que el flow complete → debe navegar directamente a `HomeScreen` (sin pedir huella de nuevo)

**Expected:**
- Estados emitidos en orden: `ValidatingCredentials` → `GeneratingCryptoKey` → `AwaitingBiometric` → `BindingDevice` → `Success`
- Navega a `HomeScreen` directamente tras el enrollment (sin pasar por Login)
- En Firebase Console: documento creado en `devices/{userId}/history/current` con `isActive=true` y `deviceId` = ANDROID_ID del device
- En logcat: sin excepciones; mensajes `✅` en cada paso

**Pass criteria:**
- [ ] `CredentialsScreen` muestra email + password con botón "Continuar"
- [ ] Llega a `HomeScreen` tras la huella (sin segundo BiometricPrompt)
- [ ] Documento Firestore correcto
- [ ] Logcat sin errores

---

### S2 — Login post-enrollment (biometric path)

**Pre-condición:** S1 completado exitosamente.

**Pasos:**
1. Cerrar la app (swipe en recents, no logout)
2. Reabrir sample app
3. `SplashScreen` detecta device enrollado → navega a `PasskeySignInScreen` (NO a Home directamente, ver [ADR-009](adr/009-client-side-security-responsibility.md))
4. Pulsar "Acceder"
5. Autenticar con huella en el `BiometricPrompt`

**Expected:**
- `PasskeySignInScreen` muestra icono de huella + CTA "Acceder"
- BiometricPrompt aparece pidiendo huella
- Tras autenticar: navega a `HomeScreen`
- Logcat: cipher descifrado correctamente, no excepciones

**Pass criteria:**
- [ ] `PasskeySignInScreen` aparece con CTA "Acceder" (no salto directo a Home)
- [ ] BiometricPrompt aparece al pulsar "Acceder"
- [ ] Tras autenticar llega a `HomeScreen`
- [ ] **NO se pide email/password** (es passwordless real)

---

### S3 — Cancelación de BiometricPrompt durante enrollment

**Pre-condición:** App recién instalada.

**Pasos:**
1. Iniciar enrollment como S1
2. Cuando aparezca el `BiometricPrompt`, **pulsar "Cancelar"** (botón negativo)

**Expected:**
- Estado emitido: `EnrollmentState.Error` con `BiometricException.UserCancelled`
- UI muestra mensaje de error apropiado (no crash)
- Rollback ejecutado: clave del KeyStore eliminada, sesión Firebase cerrada
- Re-intentar enrollment desde cero debe funcionar (estado limpio)

**Pass criteria:**
- [ ] No crash
- [ ] Mensaje de error visible al usuario
- [ ] Re-intentar enrollment funciona (no queda en estado inconsistente)
- [ ] `adb shell dumpsys account` no muestra sesión Firebase residual (opcional)

---

### S4 — Cancelación de BiometricPrompt durante login

**Pre-condición:** S1 completado, app cerrada.

**Pasos:**
1. Reabrir sample app → Login screen
2. Pulsar "Login with biometric"
3. Cancelar el BiometricPrompt

**Expected:**
- Vuelve a Login screen
- Usuario puede reintentar el login
- Enrollment NO se invalida (sigue siendo válido)

**Pass criteria:**
- [ ] Vuelve a Login (no crash, no navegación a Home)
- [ ] Reintento de login funciona

---

### S5 — Lockout por intentos fallidos

**Pre-condición:** S1 completado.

**Pasos:**
1. Cerrar y reabrir app → Login
2. Pulsar "Login with biometric"
3. Usar **dedo NO registrado** 5 veces consecutivas

**Expected:**
- Tras 5 fallos: device entra en lockout temporal (Android nativo)
- SDK emite `BiometricException.AuthenticationFailed` con mensaje sobre lockout
- UI muestra error apropiado
- Después del timeout (típicamente 30s), reintentar con huella correcta debe funcionar

**Pass criteria:**
- [ ] No crash durante los fallos repetidos
- [ ] SDK detecta y reporta el lockout
- [ ] Tras timeout, login con huella correcta funciona

---

### S6 — Cambio de biometría (KeyPermanentlyInvalidatedException)

**Pre-condición:** S1 completado.

**Pasos:**
1. Ir a Settings del device → Biometrics
2. **Añadir una nueva huella** O **borrar y volver a añadir la huella existente**
3. Volver a la sample app
4. Intentar login con biometric

**Expected:**
- `KeyPermanentlyInvalidatedException` lanzada (comportamiento intencional por `setInvalidatedByBiometricEnrollment(true)`, ver [ADR-004](adr/004-keystoremanager-aes-gcm.md))
- SDK detecta la invalidación y emite error apropiado
- App debe forzar re-enrollment (limpia estado local, vuelve a EnrollmentScreen)

**Pass criteria:**
- [ ] Detecta `KeyPermanentlyInvalidatedException` en logcat
- [ ] Sample app fuerza re-enrollment (no queda colgada intentando descifrar)
- [ ] Re-enrollment desde cero funciona

**Nota:** este es el escenario de seguridad clave del SDK. Si falla, atacante con device robado podría descifrar datos históricos tras registrar su propia huella.

---

### S7 — Logout y re-enrollment del mismo usuario

**Pre-condición:** S1 completado y usuario en Home.

**Pasos:**
1. Pulsar "Cerrar sesión" en `HomeScreen`
2. Verificar navegación a `CredentialsScreen` (NO a `PasskeySignInScreen` — el logout borra el enrollment)
3. Introducir credenciales y pulsar "Continuar"
4. Re-enrollment completo del mismo usuario (S1)

**Expected:**
- Logout: sesión Firebase cerrada, device queda desenrolado, BiometricPrompt NO se invoca
- Navegación directa a `CredentialsScreen` (no bucle de error en Login)
- Re-enrollment: genera nueva clave (puede ser distinta a la original)
- En Firestore: documento `devices/{userId}/history/current` actualizado con nuevo timestamp

**Pass criteria:**
- [ ] Logout navega a `CredentialsScreen` (no a `PasskeySignInScreen`)
- [ ] No aparece bucle de error/reintentar
- [ ] Re-enrollment del mismo usuario funciona
- [ ] Mensaje de soporte visible en `CredentialsScreen` ("¿No tienes contraseña temporal?")

---

### S8 — Device revocado remotamente

**Pre-condición:** S1 completado, app cerrada.

**Pasos:**
1. En Firebase Console, manualmente editar `devices/{userId}/history/current` → `isActive = false`
2. Reabrir sample app
3. Intentar login con biometric

**Expected:**
- `DeviceBindingManager.validateDevice()` devuelve `false`
- SDK detecta la revocación y emite `DeviceException.Revoked`
- Sample app fuerza logout y muestra mensaje "Dispositivo revocado por administrador"

**Pass criteria:**
- [ ] Detecta revocación durante el login
- [ ] No deja al usuario acceder a Home
- [ ] Mensaje de revocación visible

---

### S9 — Backgrounding durante BiometricPrompt

**Pre-condición:** App recién instalada (o post-S7).

**Pasos:**
1. Iniciar enrollment como S1
2. Cuando aparezca `BiometricPrompt`, **pulsar Home** (background app sin cancelar)
3. Esperar 5 segundos
4. Volver a la app desde recents

**Expected:**
- `BiometricPrompt` se cancela automáticamente (Android lo cierra al background)
- SDK emite `BiometricException.UserCancelled` o `BiometricException.AuthenticationFailed`
- Rollback ejecutado correctamente
- Re-intentar funciona

**Pass criteria:**
- [ ] No crash al volver al foreground
- [ ] Estado consistente: sin clave colgando, sin sesión Firebase residual
- [ ] Re-intentar enrollment funciona

---

### S10 — Rotación de Activity durante enrollment

**Pre-condición:** Auto-rotate habilitado en device.

**Pasos:**
1. Iniciar app en portrait
2. Comenzar enrollment como S1
3. Cuando aparezca `BiometricPrompt`, **rotar el device a landscape**
4. Esperar a que la Activity se recree
5. Verificar estado de la UI

**Expected:**
- `BiometricPrompt` se mantiene visible tras la rotación (Fragment Activity lo preserva, ver [ADR-007](adr/007-fragmentactivity-requirement.md))
- O bien se cancela y permite reintentar — ambos comportamientos son aceptables
- NO debe quedar el flow colgado

**Pass criteria:**
- [ ] No crash durante la rotación
- [ ] UI consistente tras la rotación
- [ ] Flow puede continuar o reintentar

---

### S11 — Bloqueo por integridad del entorno (ADR-015)

**Pre-condición:** Device rooteado (o emulador con `Default`/`Custom` config), o Frida/Xposed instalado.

**Pasos:**
1. Abrir la sample app en el device comprometido

**Expected:**
- `PasskeyAuth.initialize()` devuelve `Result.failure(IntegrityException)` (en logcat: "Integridad comprometida")
- La app muestra `SecurityBlockScreen` con mensaje amigable ("Este dispositivo no es seguro…") y botón "Cerrar aplicación"
- **NO** navega a Credentials ni intenta enrolar; **NO** crashea con `IllegalStateException`
- Al pulsar "Cerrar aplicación" la app se cierra y se elimina de recientes

**Pass criteria:**
- [ ] Aparece la pantalla de bloqueo (no crash, no nav graph normal)
- [ ] El mensaje corresponde al tipo de compromiso (root/emulador/hooking)
- [ ] "Cerrar aplicación" cierra limpiamente
- [ ] En un device limpio, la app arranca normal (sin falso positivo)

**Nota:** para forzar el bloqueo en emulador, usar `PasskeyAuthConfig.Default` (emulatorPolicy=Block) en vez de `Custom`.

---

## Comportamientos esperados (NO son bugs)

Documentado en [bugfixes.md](../.mobiai/brain/memories/bugfixes.md):

1. **`KeyPermanentlyInvalidatedException` tras cambio de biometría** — comportamiento de seguridad intencional (ADR-004). Si lo ves en S6, es CORRECTO.
2. **Lockout tras 5 fallos** — comportamiento nativo de Android, no del SDK.
3. **BiometricPrompt cancelado al background** — comportamiento de `androidx.biometric`, no del SDK.

---

## Criterios de PASS global

Para que un release sea válido:

- [ ] **S1, S2, S3** en ambos devices (StrongBox y TEE) — **bloqueantes**
- [ ] **S6, S8** en al menos un device — **bloqueantes** (validan ADR-004 y revocación remota)
- [ ] S4, S5, S7, S9, S10 en al menos un device — **recomendados**
- [ ] Logcat sin excepciones inesperadas en escenarios "pass"
- [ ] Comportamiento Firestore consistente con lo esperado en cada escenario

---

## Plantilla de reporte (rellenar tras ejecutar)

```
Smoke Test — PasskeyAuth SDK v0.X.Y
Fecha: 2026-XX-XX
Tester: <nombre>

Device A (con StrongBox): <modelo>, API <NN>
Device B (sin StrongBox): <modelo>, API <NN>

| Escenario | Device A | Device B | Notas |
|-----------|----------|----------|-------|
| S1        | ✅/❌    | ✅/❌    |       |
| S2        | ✅/❌    | ✅/❌    |       |
| S3        | ✅/❌    | ✅/❌    |       |
| S4        | ✅/❌    | ✅/❌    |       |
| S5        | ✅/❌    | ✅/❌    |       |
| S6        | ✅/❌    | ✅/❌    |       |
| S7        | ✅/❌    | ✅/❌    |       |
| S8        | ✅/❌    | ✅/❌    |       |
| S9        | ✅/❌    | ✅/❌    |       |
| S10       | ✅/❌    | ✅/❌    |       |

Veredicto: APROBADO / RECHAZADO
Logs adjuntos: <ruta>
```

---

## Mantenimiento de este documento

Añadir un escenario nuevo cada vez que:
- Se descubra un edge case en producción
- Se añada feature al SDK que el usuario interactúe (UI / BiometricPrompt)
- Un ADR nuevo introduzca comportamiento testeable manualmente

Revisar y actualizar como mínimo en cada release mayor.
