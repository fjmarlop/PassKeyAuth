# ADR-005: Sistema de Logging Configurable

**Fecha:** 2026-01-18
**Estado:** Aceptado

## Contexto y Problema

El SDK actualmente usa `println()` para debugging durante desarrollo:
```kotlin
println("🔐 KeyStoreManager: Intentando crear clave con StrongBox")
println("✅ KeyStoreManager: Clave generada exitosamente")
println("❌ KeyStoreManager: Error generando clave: ${e.message}")
```

**Problemas en producción:**
- ❌ Información sensible expuesta en logcat
- ❌ No hay control sobre qué se loguea
- ❌ Imposible deshabilitar logs sin recompilar
- ❌ No cumple con requisitos de auditoría enterprise

## Factores de Decisión

- **Seguridad:** No exponer información sensible (tokens, emails, deviceIds)
- **Performance:** Logs en producción impactan rendimiento
- **Debugging:** Developers necesitan logs durante desarrollo
- **Auditoría:** Enterprise puede requerir logs de eventos críticos

## Decisión

Implementar sistema de logging configurable con niveles y filtrado:
```kotlin
internal object PasskeyAuthLogger {
    enum class Level { NONE, ERROR, WARNING, INFO, DEBUG, VERBOSE }
    
    var level: Level = if (BuildConfig.DEBUG) Level.VERBOSE else Level.NONE
    var tag: String = "PasskeyAuth"
    
    fun v(message: String) {
        if (level >= Level.VERBOSE) Log.v(tag, message)
    }
    
    fun d(message: String) {
        if (level >= Level.DEBUG) Log.d(tag, message)
    }
    
    fun i(message: String) {
        if (level >= Level.INFO) Log.i(tag, message)
    }
    
    fun w(message: String, throwable: Throwable? = null) {
        if (level >= Level.WARNING) Log.w(tag, message, throwable)
    }
    
    fun e(message: String, throwable: Throwable? = null) {
        if (level >= Level.ERROR) Log.e(tag, message, throwable)
    }
}
```

**Uso en el SDK:**
```kotlin
// Antes (desarrollo)
println("🔐 KeyStoreManager: Intentando crear clave con StrongBox")

// Después (producción-ready)
PasskeyAuthLogger.d("KeyStoreManager: Intentando crear clave con StrongBox")
```

**Configuración por el cliente:**
```kotlin
// En desarrollo
PasskeyAuth.initialize(context, PasskeyAuthConfig(
    logLevel = PasskeyAuthLogger.Level.VERBOSE
))

// En producción
PasskeyAuth.initialize(context, PasskeyAuthConfig(
    logLevel = PasskeyAuthLogger.Level.ERROR // Solo errores críticos
))
```

## Justificación

### Por qué NO usar println()

1. **Seguridad:**
   - println() siempre imprime, incluso en release builds
   - Puede exponer tokens, emails, deviceIds en logcat
   - Violación de OWASP MASVS-STORAGE-2

2. **Performance:**
   - String formatting tiene costo
   - En producción es overhead innecesario

3. **Auditoría:**
   - No hay control sobre qué se loguea
   - Imposible implementar log filtering

### Por qué Sistema Custom (no Timber)

**Pros de Timber:**
- Bien probado, estable
- Múltiples log trees (Crashlytics, File, etc)
- Debug tree auto-tag

**Contras de Timber:**
- Dependencia externa (añade 50KB al SDK)
- No es esencial para nuestro caso de uso
- Clientes pueden no querer otra dependencia

**Decisión:** Custom logger interno, simple y sin dependencias.

Si los clientes quieren integrar con Timber/Crashlytics, pueden hacerlo:
```kotlin
// Cliente puede reenviar logs a Timber
PasskeyAuth.setLogListener { level, message ->
    when (level) {
        Level.ERROR -> Timber.e(message)
        Level.WARNING -> Timber.w(message)
        else -> Timber.d(message)
    }
}
```

## Niveles de Logging

### NONE
- **Producción por defecto**
- Cero logs, máxima performance

### ERROR
- **Producción con telemetría**
- Solo errores críticos (ej: KeyStore failure)
- No expone información del usuario

### WARNING
- **Staging/QA**
- Advertencias (ej: StrongBox fallback a TEE)
- Eventos no críticos pero notables

### INFO
- **QA con debugging básico**
- Eventos principales (enrollment success, logout)
- Sin detalles sensibles

### DEBUG
- **Desarrollo local**
- Flujo detallado de operaciones
- Puede contener IDs ofuscados

### VERBOSE
- **Debugging profundo**
- Todos los detalles (incluyendo stack traces)
- NUNCA en producción

## Reglas de Logging

### ✅ PERMITIDO loguear:
```kotlin
PasskeyAuthLogger.d("Biometric authentication started")
PasskeyAuthLogger.i("Device enrolled successfully")
PasskeyAuthLogger.w("StrongBox not available, using TEE")
PasskeyAuthLogger.e("Key generation failed", exception)
```

### ❌ PROHIBIDO loguear:
```kotlin
// ❌ NO: Email del usuario
PasskeyAuthLogger.d("User ${user.email} logged in")

// ❌ NO: Tokens completos
PasskeyAuthLogger.d("Firebase token: ${token}")

// ❌ NO: Device IDs sin ofuscar
PasskeyAuthLogger.d("Device ID: $deviceId")

// ❌ NO: Claves criptográficas (obvio pero importante)
PasskeyAuthLogger.d("Generated key: ${key.encoded}")
```

### ✅ PERMITIDO loguear (con ofuscación):
```kotlin
// ✅ OK: Email ofuscado
val obfuscatedEmail = user.email.take(3) + "***@" + user.email.substringAfter("@")
PasskeyAuthLogger.d("User $obfuscatedEmail logged in")

// ✅ OK: Token truncado
val tokenPrefix = token.take(8) + "..."
PasskeyAuthLogger.d("Token generated: $tokenPrefix")

// ✅ OK: Device ID hash
val deviceHash = deviceId.hashCode().toString(16)
PasskeyAuthLogger.d("Device hash: $deviceHash")
```

## Implementación

### Fase 1: Crear PasskeyAuthLogger (v0.2)
```kotlin
// passkeyauth-core/src/main/java/es/fjmarlop/corpsecauth/core/util/PasskeyAuthLogger.kt
internal object PasskeyAuthLogger {
    // Implementación completa
}
```

### Fase 2: Reemplazar println() (v0.3)

Buscar todos los `println()` y reemplazar con:
- `PasskeyAuthLogger.d()` para debug
- `PasskeyAuthLogger.i()` para info
- `PasskeyAuthLogger.w()` para warnings
- `PasskeyAuthLogger.e()` para errors

### Fase 3: ProGuard Rules (v1.0)
```proguard
# Remover todos los logs en release
-assumenosideeffects class es.fjmarlop.corpsecauth.core.util.PasskeyAuthLogger {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}

# Mantener solo warnings y errors
-keep class es.fjmarlop.corpsecauth.core.util.PasskeyAuthLogger {
    public static *** w(...);
    public static *** e(...);
}
```

## Consecuencias

### Positivas

✅ Control granular sobre logging  
✅ Cumple OWASP MASVS (no expone datos sensibles)  
✅ Performance óptimo en producción (logs removidos por ProGuard)  
✅ Facilita debugging en desarrollo  
✅ Auditoría enterprise posible  

### Negativas

❌ Requiere migración de todos los println()  
❌ Código extra para mantener  
❌ Developers deben recordar usar logger correcto  

### Neutral

⚪ Tamaño del SDK aumenta ~5KB (negligible)  
⚪ Clientes pueden elegir no usar logs  

## Plan de Migración

**v0.1-v0.2 (Actual):**
- `println()` está OK para desarrollo rápido
- Marcar con `// TODO: Migrate to PasskeyAuthLogger` en código

**v0.3:**
- Implementar PasskeyAuthLogger
- Migrar println() existentes
- Agregar tests de logging

**v1.0 (Release):**
- Auditoría completa de logs
- Verificar que NO hay información sensible
- ProGuard rules activadas
- Testing en release builds

## Referencias

- [OWASP MASVS-STORAGE-2](https://mas.owasp.org/MASVS/controls/MASVS-STORAGE-2/)
- [Android Logging Best Practices](https://developer.android.com/studio/debug/am-logcat)
- [ProGuard Log Removal](https://www.guardsquare.com/manual/configuration/examples)

## Revisiones

- **2026-01-18:** Creación inicial
- **Próxima revisión:** En v0.3 al implementar PasskeyAuthLogger

---

**Nota:** Los `println()` actuales son temporales y serán reemplazados antes de v1.0.