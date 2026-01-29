# ADR-006: EnrollmentManager como Orquestador Transaccional con Passwordless Real

**Estado:** Actualizado  
**Fecha:** 2026-01-23  
**Autores:** Francisco Javier Marmolejo López  
**Actualización:** Transición a passwordless verdadero sin contraseña del usuario

---

## Contexto

El proceso de enrollment debe ser:
1. **Atómico** - Todo o nada, sin estados parciales
2. **Seguro** - Con rollback automático si algo falla
3. **Passwordless real** - Usuario nunca conoce contraseña final
4. **UX-friendly** - Feedback progresivo del proceso

**Decisión crítica:** Cambio de arquitectura para eliminar la contraseña elegida por el usuario y usar invalidación automática con password aleatoria.

---

## Decisión

Implementar `EnrollmentManager` como orquestador transaccional que emite estados via Flow, con **invalidación automática de contraseña temporal** usando password aleatoria.

### Flujo de 7 Pasos
```kotlin
fun enrollDevice(
    email: String,
    temporaryPassword: String  // Sin newPassword
): Flow<EnrollmentState>
```

**Pasos:**
1. **Validar credenciales temporales** con Firebase
2. **Invalidar password temporal** → Password aleatoria 32 chars (usuario NO la conoce)
3. **Generar clave** en KeyStore (StrongBox/TEE)
4. **Autenticar biométricamente** con BiometricPrompt
5. **Cifrar token** de sesión con AES-GCM
6. **Vincular dispositivo** en Firestore
7. **Guardar en storage** local cifrado

---

## Estados Progresivos
```kotlin
sealed class EnrollmentState {
    object Idle
    data class ValidatingCredentials(val email: String)
    data class RequiresPasswordChange(val isTemporaryPassword: Boolean)
    object GeneratingCryptoKey
    data class AwaitingBiometric(val config: BiometricConfig)
    object BindingDevice
    data class Success(val user: AuthUser)
    data class Error(val exception: PasskeyAuthException)
}
```

**Ventaja:** UI puede mostrar progreso detallado y feedback específico.

---

## Rollback Automático

| Paso | Falla en | Rollback |
|------|----------|----------|
| 1 | Validación | Ninguno |
| 2 | Invalidar password | Sign out Firebase |
| 3 | Generar clave | Sign out Firebase |
| 4 | Biometría | Delete key + Sign out |
| 5 | Cifrado | Delete key + Sign out |
| 6 | Device binding | Delete key + Clear storage + Sign out |
| 7 | Storage | Delete key + Revoke device + Sign out |

**Garantía:** Nunca quedan estados parciales inconsistentes.

---

## Passwordless Real vs Passwordless con Contraseña Usuario

### ❌ Anterior (Passwordless con contraseña)
```
Usuario recibe: test@empresa.com / TempPass123
Usuario ingresa nueva: "MiPassword456"
Sistema cambia temporal → nueva
❌ Usuario CONOCE contraseña → Puede usarla manualmente
```

### ✅ Actual (Passwordless verdadero)
```
Usuario recibe: test@empresa.com / TempPass123
Usuario ingresa SOLO temporal
Sistema genera: "8jK#mP9$xL2@qN..." (32 chars aleatorios)
Sistema cambia temporal → aleatoria
✅ Usuario NUNCA conoce contraseña → Solo biometría
```

---

## Generación de Password Aleatoria
```kotlin
private fun generateSecureRandomPassword(length: Int = 32): String {
    val charset = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()-_=+[]{}|;:,.<>?"
    val random = SecureRandom()
    
    return (1..length)
        .map { charset[random.nextInt(charset.length)] }
        .joinToString("")
}
```

**Seguridad:**
- SecureRandom (cryptographically strong)
- 32 caracteres
- Mayúsculas, minúsculas, números, símbolos especiales
- Entropía: ~200 bits

---

## Alternativas Consideradas

### 1. ❌ APIs Individuales Expuestas
```kotlin
// Usuario debe orquestar manualmente
enrollmentManager.validateCredentials()
enrollmentManager.changePassword()
enrollmentManager.generateKey()
// ...
```

**Rechazado:**
- Usuario debe manejar rollback manualmente
- Propenso a errores
- Estados inconsistentes posibles

### 2. ❌ Callbacks
```kotlin
enrollmentManager.enrollDevice(
    onProgress: (step: Int) -> Unit,
    onSuccess: (user: AuthUser) -> Unit,
    onError: (error: Exception) -> Unit
)
```

**Rechazado:**
- Callback hell en 7 pasos
- No cancelable
- Difícil integración con Compose

### 3. ✅ Flow con Estados Detallados (ELEGIDO)
```kotlin
enrollDevice(email, tempPassword).collect { state ->
    when (state) {
        is EnrollmentState.AwaitingBiometric -> showBiometricPrompt()
        is EnrollmentState.Success -> navigateToHome()
        // ...
    }
}
```

**Ventajas:**
- Cancelable (coroutines)
- Integración perfecta con Compose
- Estados ricos para UX
- Rollback automático

### 4. ❌ Mantener newPassword del Usuario
**Rechazado porque:**
- No es passwordless real
- Usuario puede caer en usar password manualmente
- Menos seguro (usuarios eligen passwords débiles)
- Más fricción en UX (pensar en password)

---

## Consecuencias

### Positivas
✅ **Atomicidad garantizada** - Todo o nada  
✅ **UX rica** - Estados progresivos detallados  
✅ **Cancelable** - Via coroutines  
✅ **Testeable** - Flow fácil de testear con Turbine  
✅ **Compose-friendly** - `collectAsState()` directo  
✅ **Rollback automático** - Sin estados inconsistentes  
✅ **Passwordless real** - Usuario nunca conoce contraseña final  
✅ **Más seguro** - Password de 32 chars aleatorios  
✅ **Mejor UX** - Solo 2 campos en vez de 4  

### Negativas
⚠️ Código más complejo que callbacks simples  
⚠️ Requiere entender Flow/coroutines  
⚠️ Breaking change en API (cambio de firma)

### Mitigaciones
- Documentación completa con ejemplos
- Sample app demuestra uso correcto
- Estados auto-explicativos
- Migration guide para usuarios de v0.1.0

---

## Breaking Changes

### v0.1.0 → v0.2.0

**Antes:**
```kotlin
PasskeyAuth.enrollDevice(
    activity = activity,
    email = "user@empresa.com",
    temporaryPassword = "TempPass123",
    newPassword = "MiPassword456"  // ← ELIMINADO
)
```

**Ahora:**
```kotlin
PasskeyAuth.enrollDevice(
    activity = activity,
    email = "user@empresa.com",
    temporaryPassword = "TempPass123"
    // newPassword eliminado
)
```

**UI:**
- EnrollmentScreen: 4 campos → 2 campos
- Sin validación de passwords iguales
- Mensaje informativo sobre invalidación automática

---

## Notas de Implementación

- `FirebaseAuthManager.invalidateTemporaryPassword()` reemplaza a `changePassword()`
- Password generada nunca se almacena localmente
- Password solo existe en Firebase Auth
- Usuario nunca ve ni conoce esta password
- Recovery debe hacerse via IT (reset de password temporal)

---

## Referencias

- NIST SP 800-63B - Digital Identity Guidelines (Authenticator Assurance Level)
- OWASP MASVS - Mobile Application Security Verification Standard
- Android Keystore System Documentation
- Kotlin Flow Documentation

---

## Cambios en esta Versión

**v0.2.0 (2026-01-23):**
- Eliminado parámetro `newPassword` de `enrollDevice()`
- Agregado método `invalidateTemporaryPassword()` con generación aleatoria
- Actualizado paso 2 del enrollment: invalidación automática
- Simplificada UI de enrollment: 2 campos en vez de 4
- Mejorada documentación sobre passwordless real