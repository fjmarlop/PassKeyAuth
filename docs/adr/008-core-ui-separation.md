# ADR-008: Separacion Core/UI - Eliminacion de SystemState

**Estado:** Aceptado  
**Fecha:** 2026-01-26  
**Autores:** Francisco Javier Marmolejo López

---

## Contexto

En iteraciones anteriores, se creo `SystemState` como sealed class para que el SDK "dictara" la navegacion de la UI:
```kotlin
sealed class SystemState {
    object RequiresEnrollment
    data class RequiresReEnrollment(val reason: String)
    data class ReadyForBiometricLogin(val user: AuthUser)
    data class AlreadyAuthenticated(val user: AuthUser)
}
```

El sample app usaba `getSystemState()` para decidir navegacion.

**Problema identificado:**

Esto viola la separacion de responsabilidades:
- ❌ El CORE (SDK) estaba dictando logica de UI
- ❌ Clientes quedaban atados a esta estructura de navegacion
- ❌ Dificultad para implementar navegacion personalizada
- ❌ Complejidad innecesaria en el core

---

## Decision

**Eliminar `SystemState` del core.**

El SDK debe proveer:
1. ✅ **Estado observable**: `authState: StateFlow<AuthResult>`
2. ✅ **Queries simples**: `isDeviceEnrolled()`, `isAuthenticated()`
3. ✅ **Acciones**: `enrollDevice()`, `authenticate()`, `logout()`

El SDK **NO** debe:
1. ❌ Dictar navegacion
2. ❌ Imponer estructura de UI
3. ❌ Tomar decisiones de presentacion

---

## Consecuencias

### Positivas ✅

1. **Separacion clara de responsabilidades**
   - Core = Estado + Acciones
   - Cliente = Navegacion + UI

2. **Mayor flexibilidad para clientes**
```kotlin
   // Cliente decide su navegacion
   when {
       !isEnrolled -> ShowEnrollment()
       !isAuthenticated -> ShowLogin()
       else -> ShowHome()
   }
```

3. **API mas simple**
   - Menos conceptos para aprender
   - Menos codigo en el core
   - Mas facil de mantener

4. **Clientes pueden usar cualquier patron de navegacion**
   - Jetpack Navigation
   - Compose Navigation
   - Custom state machines
   - Otros frameworks

### Negativas ⚠️

1. **Clientes deben implementar su logica de navegacion**
   - Pero esto es apropiado - cada app es diferente
   - Sample app provee ejemplo claro

2. **Migracion necesaria si usaban SystemState**
   - Breaking change para early adopters
   - Documentar migracion en CHANGELOG

---

## Implementacion

### Core Simplificado
```kotlin
object PasskeyAuth {
    // Estado observable
    val authState: StateFlow<AuthResult>
    
    // Queries
    suspend fun isDeviceEnrolled(): Boolean
    fun isAuthenticated(): Boolean
    
    // Acciones
    suspend fun initialize(context, config)
    fun enrollDevice(...): Flow<EnrollmentState>
    suspend fun authenticate(...): Result<AuthUser>
    fun logout()
}
```

### Cliente Implementa Navegacion
```kotlin
@Composable
fun MyAppNav(viewModel: AuthViewModel) {
    val isEnrolled by remember { /* check */ }
    val isAuthenticated = viewModel.isAuthenticated()
    
    when {
        !isEnrolled -> EnrollmentScreen()
        !isAuthenticated -> LoginScreen()
        else -> HomeScreen()
    }
}
```

---

## Alternativas Consideradas

### 1. Mantener SystemState como Helper Opcional

**Propuesta:** Dejar `getSystemState()` pero documentar que es opcional.

**Rechazado porque:**
- Sigue existiendo la tentacion de usarlo
- Aumenta superficie de API sin beneficio claro
- Clientes pueden implementarlo facilmente si lo necesitan

### 2. Proveer Navegacion como Modulo Separado

**Propuesta:** `passkeyauth-navigation` con helpers de navegacion.

**Rechazado porque:**
- Overengineering para problema simple
- Cada framework de navegacion es diferente
- Sample app ya provee ejemplo suficiente

### 3. No Hacer Nada (Mantener Status Quo)

**Rechazado porque:**
- Viola principios de diseño limpio
- Dificulta adopcion del SDK
- No es sostenible a largo plazo

---

## Migracion

### Antes (v0.1.x):
```kotlin
when (val state = PasskeyAuth.getSystemState()) {
    is SystemState.RequiresEnrollment -> navigate(Enrollment)
    is SystemState.ReadyForBiometricLogin -> navigate(Login)
    is SystemState.AlreadyAuthenticated -> navigate(Home)
}
```

### Despues (v0.2.0+):
```kotlin
val isEnrolled = PasskeyAuth.isDeviceEnrolled()
val isAuthenticated = PasskeyAuth.isAuthenticated()

when {
    !isEnrolled -> navigate(Enrollment)
    !isAuthenticated -> navigate(Login)
    else -> navigate(Home)
}
```

---

## Referencias

- "Clean Architecture" - Robert C. Martin (separacion de concerns)
- Android Architecture Guide - UI layer independence
- Effective Kotlin - Item 17: "Separate concerns"

---

## Notas

Este cambio es parte de v0.2.0 y representa un paso importante hacia un SDK mas limpio y profesional.

El sample app ha sido actualizado para reflejar las mejores practicas de navegacion sin depender de SystemState.