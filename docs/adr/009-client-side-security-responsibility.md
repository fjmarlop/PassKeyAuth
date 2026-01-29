# ADR-009: Client-Side Security Responsibility

**Estado:** Aceptado  
**Fecha:** 2026-01-26  
**Autores:** Francisco Javier Marmolejo López

---

## Contexto

PasskeyAuth SDK provee herramientas de autenticacion biometrica pero NO puede forzar tecnicamente que el cliente las use correctamente.

**Pregunta clave:** ¿Debe el SDK intentar "forzar" comportamientos seguros en la UI del cliente?

---

## Decision

**El SDK NO fuerza checks de seguridad en la UI del cliente.**

El SDK provee:
1. ✅ Herramientas claras (`isDeviceEnrolled`, `isAuthenticated`, `authenticate`)
2. ✅ Documentacion exhaustiva de mejores practicas
3. ✅ Sample app como ejemplo de implementacion segura
4. ✅ Warnings en logs cuando se detectan patrones inseguros (opcional)

El SDK **NO** provee:
1. ❌ Bloqueos tecnicos que impidan mal uso
2. ❌ Validaciones que fuercen navegacion especifica
3. ❌ Restricciones que limiten flexibilidad del cliente

---

## Rationale

### Por que NO forzar tecnicamente:

**1. Imposible de Implementar**
```kotlin
// Cliente SIEMPRE puede hacer esto:
if (PasskeyAuth.isDeviceEnrolled()) {
    navigateAnywhere()  // SDK no puede bloquear esto
}
```

**2. Casos de Uso Legitimos**
```kotlin
// App de notas personales - baja seguridad
if (isEnrolled && isLowSecurity) {
    showHome()
}

// App corporativa - red segura
if (isEnrolled && isIntranet) {
    showHome()  // No requiere biometria en red interna
}
```

**3. Responsabilidad del Cliente**
- Cada app tiene diferente modelo de amenazas
- Cliente conoce sus requisitos de seguridad
- SDK es herramienta, no policia

**4. Precedente de Industria**
```kotlin
// Firebase Auth NO fuerza verificacion
val user = FirebaseAuth.getInstance().currentUser

// Biometric Library NO fuerza uso del resultado
biometricPrompt.authenticate(...)

// Keychain NO fuerza verificacion biometrica
let data = keychain.get("token")
```

---

## Consecuencias

### Positivas ✅

1. **Flexibilidad para el Cliente**
   - Implementa su propio modelo de seguridad
   - Adapta a sus necesidades especificas
   - No limitado por decisiones del SDK

2. **API Mas Simple**
   - Menos restricciones = menos complejidad
   - Mas facil de integrar
   - Menos "magia" oculta

3. **Casos de Uso Diversos**
   - Apps de alta seguridad
   - Apps de baja seguridad
   - Apps con seguridad contextual

### Negativas ⚠️

1. **Cliente Puede Implementar Mal**
   - Riesgo de implementaciones inseguras
   - Requiere educacion del desarrollador
   - Documentacion debe ser EXCELENTE

2. **Responsabilidad Compartida**
   - SDK provee herramientas
   - Cliente debe usarlas correctamente
   - Ambos tienen responsabilidad

---

## Mitigaciones

### 1. Documentacion Exhaustiva

**SECURITY.md:**
- Guia completa de mejores practicas
- Ejemplos de codigo seguro vs inseguro
- Checklist pre-deployment
- Modelo de amenazas

**README.md:**
- Seccion destacada de seguridad
- Tabla de metodos y su nivel de seguridad
- Link a SECURITY.md

### 2. Sample App Como Referencia
```kotlin
// SplashScreen - EJEMPLO CORRECTO
when {
    !isEnrolled -> navigateToEnrollment()
    else -> navigateToLogin()  // SIEMPRE requiere biometria
}
```

### 3. KDoc Detallado
```kotlin
/**
 * Verifica si el dispositivo esta enrollado.
 * 
 * ⚠️ ADVERTENCIA: Este metodo NO verifica autenticacion reciente.
 * Solo verifica si existen claves biometricas guardadas.
 * 
 * Para verificar autenticacion activa, usa [isAuthenticated].
 * 
 * INSEGURO:
 * ```kotlin
 * if (isDeviceEnrolled()) {
 *     showSensitiveData()  // ❌ Cualquiera con dispositivo puede acceder
 * }
 * ```
 * 
 * SEGURO:
 * ```kotlin
 * if (isAuthenticated()) {
 *     showSensitiveData()  // ✅ Biometria verificada
 * }
 * ```
 */
suspend fun isDeviceEnrolled(): Boolean
```

### 4. Warnings en Logs (Opcional)
```kotlin
fun isDeviceEnrolled(): Boolean {
    val enrolled = /* check */
    
    if (enrolled && !isAuthenticated()) {
        Log.w("PasskeyAuth", 
            "⚠️ Device enrolled but not authenticated. " +
            "Call authenticate() before accessing sensitive data."
        )
    }
    
    return enrolled
}
```

---

## Caso Real: Fallo de Seguridad Descubierto

### El Bug
```kotlin
// SplashScreen - INSEGURO (version inicial)
LaunchedEffect(Unit) {
    val isAuthenticated = PasskeyAuth.isAuthenticated()
    
    when {
        isAuthenticated -> navigateToHome()  // ❌ FALLO CRITICO
        else -> navigateToLogin()
    }
}
```

**Problema:**
1. Usuario autentica y cierra app
2. `authState` permanece `Authenticated` en memoria
3. Usuario reabre app
4. SplashScreen lee `isAuthenticated()` = true
5. Navega directo a Home sin pedir biometria
6. **Cualquiera con dispositivo puede acceder**

### La Solucion
```kotlin
// SplashScreen - SEGURO (version corregida)
LaunchedEffect(Unit) {
    when {
        !isEnrolled -> navigateToEnrollment()
        else -> navigateToLogin()  // ✅ SIEMPRE requiere biometria
    }
}
```

**Por que funciona:**
1. No confia en estado de autenticacion previo
2. Siempre fuerza verificacion biometrica
3. No hay race conditions
4. No hay bypass posible

**Leccion:** Incluso con documentacion, errores son posibles. Por eso el sample app debe ser impecable.

---

## Alternativas Consideradas

### Alternativa 1: Forzar Checks Tecnicamente

**Propuesta:** Lanzar exception si se accede a datos sin autenticar.
```kotlin
suspend fun getSensitiveData(): Data {
    require(isAuthenticated()) {
        "Must call authenticate() first"
    }
    // ...
}
```

**Rechazado porque:**
- Solo funciona para APIs del SDK
- No puede controlar navegacion del cliente
- Cliente puede ignorar exceptions
- Demasiado intrusivo

### Alternativa 2: Modo "Strict" vs "Permissive"

**Propuesta:** Config que habilita validaciones estrictas.
```kotlin
PasskeyAuthConfig.Custom(
    strictMode = true  // Lanza exceptions en mal uso
)
```

**Rechazado porque:**
- Complejidad innecesaria
- No soluciona el problema fundamental
- Puede romper apps existentes

### Alternativa 3: Proveer NavGraph Pre-Hecho

**Propuesta:** SDK incluye NavGraph de Compose Navigation pre-configurado.

**Rechazado porque:**
- Ata clientes a Jetpack Navigation
- No todos usan Compose
- Demasiado opinionado
- Cliente pierde flexibilidad

---

## Conclusion

La seguridad es **responsabilidad compartida**:

**SDK:**
- ✅ Proveer herramientas seguras
- ✅ Documentar exhaustivamente
- ✅ Ejemplo perfecto en sample app
- ✅ Advertir sobre mal uso

**Cliente:**
- ✅ Leer documentacion
- ✅ Implementar correctamente
- ✅ Auditar su implementacion
- ✅ Seguir mejores practicas

**Esta decision es consistente con la industria y respeta la autonomia del desarrollador mientras provee guias claras para uso seguro.**