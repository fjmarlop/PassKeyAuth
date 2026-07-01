# Guía de Seguridad - PasskeyAuth SDK

## 🔒 Visión General de Seguridad

PasskeyAuth SDK proporciona autenticación sin contraseñas de nivel empresarial usando claves biométricas respaldadas por hardware. Sin embargo, el SDK **NO impone** políticas de seguridad en tu capa de UI - esa es tu responsabilidad como implementador.

---

## ⚠️ Conceptos Críticos de Seguridad

### `isDeviceEnrolled()` vs `isAuthenticated()`

**Entender la diferencia es CRÍTICO para la seguridad:**
```kotlin
// ❌ INSEGURO - Solo verifica si existen claves
fun isDeviceEnrolled(): Boolean
// Retorna: true si hay claves biométricas guardadas (dispositivo enrollado)
// NO verifica: que la biometría fue validada recientemente

// ✅ SEGURO - Verifica validación biométrica reciente
fun isAuthenticated(): Boolean
// Retorna: true si el usuario se autenticó con biometría en la sesión actual
// Verificado: la biometría fue validada dentro del timeout de sesión
```

---

## 🚨 Errores Comunes de Seguridad

### ❌ ERROR #1: Confiar Solo en `isDeviceEnrolled()`

**CÓDIGO INSEGURO:**
```kotlin
@Composable
fun SplashScreen() {
    LaunchedEffect(Unit) {
        if (PasskeyAuth.isDeviceEnrolled()) {
            navegarAHome()  // ❌ FALLO CRÍTICO DE SEGURIDAD
        }
    }
}
```

**Por qué es peligroso:**
- Cualquiera con acceso físico al dispositivo puede abrir la app
- No requiere verificación biométrica
- Equivalente a "sin seguridad"

---

### ❌ ERROR #2: Verificar Estado de Autenticación Demasiado Pronto

**CÓDIGO INSEGURO:**
```kotlin
@Composable
fun SplashScreen() {
    val isAuthenticated = PasskeyAuth.isAuthenticated()
    
    LaunchedEffect(Unit) {
        when {
            !isEnrolled -> navegarAEnrollment()
            isAuthenticated -> navegarAHome()  // ❌ Condición de carrera
            else -> navegarALogin()
        }
    }
}
```

**Por qué es peligroso:**
- Condición de carrera con invalidación de `onAppForeground()`
- Estado de autenticación obsoleto de sesión anterior
- Usuario podría saltarse el prompt biométrico

---

### ❌ ERROR #3: No Llamar Métodos de Ciclo de Vida

**CÓDIGO INSEGURO:**
```kotlin
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ❌ Faltan hooks de onStart() y onStop()
    }
}
```

**Por qué es peligroso:**
- El timeout de sesión no funcionará
- La app permanece "autenticada" para siempre
- Usuario puede dejar el dispositivo desatendido

---

## ✅ Patrones de Implementación Segura

### Patrón #1: Siempre Requerir Biometría al Abrir App

**CÓDIGO SEGURO:**
```kotlin
@Composable
fun SplashScreen(
    onNavigateToEnrollment: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: AuthViewModel = viewModel()
) {
    LaunchedEffect(Unit) {
        val isEnrolled = viewModel.isDeviceEnrolled()
        
        when {
            !isEnrolled -> onNavigateToEnrollment()
            else -> onNavigateToLogin()  // ✅ SIEMPRE requiere biometría
        }
    }
}
```

**Por qué es seguro:**
- Cada apertura de app requiere verificación biométrica
- Sin condiciones de carrera
- No hay bypass posible

---

### Patrón #2: Implementar Hooks de Ciclo de Vida

**CÓDIGO SEGURO:**
```kotlin
class MainActivity : FragmentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        lifecycleScope.launch {
            PasskeyAuth.initialize(
                context = applicationContext,
                config = PasskeyAuthConfig.Custom(
                    sessionTimeoutMinutes = 5  // Timeout configurable
                )
            )
        }
    }
    
    override fun onStart() {
        super.onStart()
        
        // ✅ Verificar timeout cuando app vuelve a primer plano
        if (!isChangingConfigurations) {
            PasskeyAuth.onAppForeground()
        }
    }
    
    override fun onStop() {
        super.onStop()
        
        // ✅ Guardar timestamp cuando app va a segundo plano
        if (!isChangingConfigurations) {
            PasskeyAuth.onAppBackground()
        }
    }
}
```

**Por qué es seguro:**
- El timeout de sesión funciona correctamente
- Usuario debe re-autenticarse después del timeout
- La rotación no dispara re-autenticación

---

### Patrón #3: Verificar Autenticación Antes de Operaciones Sensibles

**CÓDIGO SEGURO:**
```kotlin
@Composable
fun TransferirDineroScreen(viewModel: BankViewModel) {
    
    // ✅ Verificar autenticación antes de acción sensible
    Button(onClick = {
        if (!PasskeyAuth.isAuthenticated()) {
            mostrarPantallaLogin()
            return@Button
        }
        
        viewModel.transferirDinero(cantidad)
    }) {
        Text("Transferir €1000")
    }
}
```

**Por qué es seguro:**
- Doble verificación antes de operaciones críticas
- La sesión podría haber expirado durante interacción del usuario
- Defensa en profundidad

---

## 🔐 Checklist de Seguridad

Antes de desplegar tu app, verifica:

- [ ] **Splash/Pantalla Inicial:** Siempre navega a Login (nunca directo a Home)
- [ ] **MainActivity:** Implementa `onStart()` y `onStop()` con hooks de ciclo de vida
- [ ] **Timeout de Sesión:** Configurado apropiadamente para tu modelo de amenazas
- [ ] **Pantallas Sensibles:** Verificar `isAuthenticated()` antes de mostrar contenido
- [ ] **Acciones Críticas:** Re-verificar autenticación antes de ejecutar
- [ ] **Manejo de Errores:** No filtrar información en mensajes de error
- [ ] **Logging:** Eliminar logs de debug en builds de producción

---

## 📊 Consideraciones del Modelo de Amenazas

### Baja Seguridad (App de Notas Personales)
```kotlin
PasskeyAuthConfig.Custom(
    sessionTimeoutMinutes = 15  // Timeout relajado
)
```

### Seguridad Media (App Corporativa)
```kotlin
PasskeyAuthConfig.Custom(
    sessionTimeoutMinutes = 5  // Timeout balanceado
)
```

### Alta Seguridad (Banca/Salud)
```kotlin
PasskeyAuthConfig.Custom(
    sessionTimeoutMinutes = 0  // Siempre requiere biometría
)
```

---

## 🚨 Contra Qué NO Protege PasskeyAuth

> Desde ADR-015 (v0.3.0) el SDK **sí** incluye root/emulator/hooking detection,
> anti-debug, y protección de screenshots/tapjacking — pero esta última **solo
> cubre las pantallas propias del SDK** (`PasskeyAuthActivity` cuando usas el
> launcher `PasskeyAuthContract`, o `PasskeySignInScreen`/`PasskeyEnrollScreen`
> mientras se están dibujando). El resto de tu app es tu responsabilidad. Ver
> [DEVELOPMENT.md § Guía de Integración, Paso 5](DEVELOPMENT.md#paso-5--seguridad-del-host-flag_secure--anti-tapjacking).

### Fuera del Alcance:
- ❌ **Grabación de pantalla / Screenshots en TU Activity** — si usas los composables
  del SDK embebidos en tu propia Activity (no el launcher), debes aplicar
  `FLAG_SECURE` y el guard de tapjacking tú mismo, igual que hace `MainActivity.kt`
  en el `sample`. `FLAG_SECURE` es un flag por-`Window`; el SDK no puede aplicarlo
  a una Activity que no controla.
- ❌ **Seguridad de Red más allá de TLS** (cert pinning — ver [DEVELOPMENT.md § Cert pinning para integradores](DEVELOPMENT.md#cert-pinning-para-integradores))
- ❌ **Ingeniería Inversa** (usar ProGuard/R8)
- ❌ **Ataques Físicos** (cifrado de dispositivo, secure boot)

### Responsabilidades del Cliente:
- `FLAG_SECURE` + anti-tapjacking en tus propias Activities si usas los composables directamente (Modo B, no aplica si usas el launcher)
- Cert pinning si tu modelo de amenaza lo requiere
- Seguir mejores prácticas de seguridad de Android
- Realizar auditorías de seguridad
- Monitorizar vulnerabilidades de seguridad

---

## 📚 Recursos Adicionales

- [OWASP Mobile Security](https://owasp.org/www-project-mobile-security/)
- [Mejores Prácticas de Seguridad Android](https://developer.android.com/topic/security/best-practices)
- [Guía de Autenticación Biométrica](https://developer.android.com/training/sign-in/biometric-auth)
- [Sistema Android KeyStore](https://developer.android.com/training/articles/keystore)

---

## 🐛 Reportar Problemas de Seguridad

Si descubres una vulnerabilidad de seguridad en PasskeyAuth SDK:

1. **NO** abras un issue público en GitHub
2. Envía email con preocupaciones de seguridad a: [tu-email-de-seguridad]
3. Incluye pasos detallados de reproducción
4. Permite tiempo razonable para corregir antes de divulgación pública

---

## 📝 Historial de Auditorías de Seguridad

| Versión | Fecha | Auditor | Hallazgos |
|---------|-------|---------|-----------|
| v0.2.0  | 2026-01 | Interno | Release inicial |
| v0.3.0  | 2026-06 | Interno | Runtime integrity hardening (root/emulator/hooking detection, anti-debug, key attestation, tapjacking guard, FLAG_SECURE, privacy overlay, memory zeroing) — ADR-015 |
| v0.4.1  | 2026-06 | Interno | SDK silencioso por defecto (sin `println` → cero riesgo de filtrar PII/tokens a logcat); thread safety en `scope` — ADR-005 (rev.) |

---

**Recuerda: La seguridad es una responsabilidad compartida. El SDK provee herramientas - tú debes usarlas correctamente.**