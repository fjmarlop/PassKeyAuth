# ADR-007: Requerimiento de FragmentActivity

**Estado:** Aceptado  
**Fecha:** 2026-01-25  
**Autores:** Francisco Javier Marmolejo López

---

## Contexto

PasskeyAuth SDK utiliza `BiometricPrompt` de Android para la autenticación biométrica. `BiometricPrompt` es la API oficial de Android para gestionar autenticación con huella, reconocimiento facial, e iris.

**Problema técnico:**

BiometricPrompt internamente utiliza `Fragment` transactions para mostrar el diálogo de autenticación. Esto significa que la `Activity` que invoca `BiometricPrompt` **debe** ser una `FragmentActivity` para poder manejar estas transacciones correctamente.

---

## Decisión

**PasskeyAuth SDK requiere que la Activity que llame a:**
- `PasskeyAuth.enrollDevice()`
- `PasskeyAuth.authenticate()`

**Extienda `FragmentActivity`.**

---

## Consecuencias

### Positivas ✅

1. **BiometricPrompt funciona correctamente**
   - No hay workarounds ni hacks
   - API oficial de Android sin modificaciones

2. **Soporte completo de Fragment lifecycle**
   - Manejo correcto de rotaciones
   - Gestión de estado durante cambios de configuración

3. **Compatibilidad hacia atrás**
   - FragmentActivity funciona desde API 14
   - No hay restricciones adicionales de versión

4. **Código más simple**
   - No necesitamos wrappers internos
   - Menos complejidad en el SDK

### Negativas ⚠️

1. **Restricción en el tipo de Activity**
   - No funciona con `ComponentActivity` (Jetpack Compose default)
   - No funciona con `AppCompatActivity`

2. **Migración necesaria para algunos usuarios**
   - Apps usando `ComponentActivity` deben cambiar
   - Cambio en clase base puede tener implicaciones

3. **Documentación crítica**
   - Debe estar muy claro en README
   - Error en runtime si no se cumple

---

## Alternativas Consideradas

### 1. ❌ Wrapper Interno de Fragment

**Idea:** Crear un Fragment interno que maneje BiometricPrompt.
```kotlin
internal class BiometricFragment : Fragment() {
    fun showBiometric() {
        val biometricPrompt = BiometricPrompt(this, ...)
        biometricPrompt.authenticate(...)
    }
}
```

**Rechazado porque:**
- Complejidad innecesaria
- Manejo de lifecycle frágil
- Posibles memory leaks
- Difícil de mantener

### 2. ❌ Soportar Ambos Tipos de Activity

**Idea:** Detectar tipo de Activity y adaptar.
```kotlin
when (activity) {
    is FragmentActivity -> useFragmentBiometric()
    is ComponentActivity -> useReflectionHack()
}
```

**Rechazado porque:**
- Técnicamente imposible sin hacks
- Reflection es frágil y puede romperse
- Viola principios de Android
- Mantenimiento complejo

### 3. ✅ Documentar Requisito (ELEGIDO)

**Idea:** Requerir `FragmentActivity` y documentarlo claramente.

**Por qué es mejor:**
- Solución simple y clara
- Usa Android como está diseñado
- Fácil de entender para desarrolladores
- Menos código = menos bugs
- Error claro en runtime si se incumple

---

## Implementación

### Error Runtime

Si el usuario pasa una Activity que no es FragmentActivity:
```
java.lang.ClassCastException: MainActivity cannot be cast to 
androidx.fragment.app.FragmentActivity
```

**Este error es inmediato y claro**, lo que facilita el debug.

### Documentación

**README.md:**
```markdown
### ⚠️ Important: FragmentActivity Required

Your MainActivity MUST extend FragmentActivity.
```

**Sample App:**
```kotlin
/**
 * IMPORTANTE: MainActivity DEBE extender FragmentActivity.
 * BiometricPrompt lo requiere internamente.
 */
class MainActivity : FragmentActivity()
```

**KDoc en API:**
```kotlin
/**
 * @param activity FragmentActivity requerida para BiometricPrompt
 * @throws ClassCastException si activity no es FragmentActivity
 */
suspend fun enrollDevice(activity: FragmentActivity, ...)
```

---

## Mitigaciones

### 1. Documentación Clara

- ✅ README con warning destacado
- ✅ DEVELOPMENT.md con troubleshooting
- ✅ Sample app como referencia
- ✅ Este ADR explicando el por qué

### 2. Error Descriptivo

Considerar agregar una validación explícita:
```kotlin
fun enrollDevice(activity: FragmentActivity, ...) {
    require(activity is FragmentActivity) {
        "PasskeyAuth requires FragmentActivity. " +
        "Change your MainActivity to extend FragmentActivity instead of ComponentActivity."
    }
    // ...
}
```

**Decisión:** No agregar por ahora porque el casteo ya falla de forma clara.

### 3. Sample App como Template

El sample app usa FragmentActivity correctamente, sirviendo como ejemplo.

---

## Referencias

- [Android BiometricPrompt Documentation](https://developer.android.com/reference/androidx/biometric/BiometricPrompt)
- [FragmentActivity Documentation](https://developer.android.com/reference/androidx/fragment/app/FragmentActivity)
- Stack Overflow: "Why does BiometricPrompt need FragmentActivity?"

---

## Notas de Implementación

### Para Compose Apps

Jetpack Compose por defecto usa `ComponentActivity`, pero es trivial cambiar a `FragmentActivity`:
```kotlin
// Antes
class MainActivity : ComponentActivity()

// Después
class MainActivity : FragmentActivity()
```

**No hay pérdida de funcionalidad** - FragmentActivity soporta todo lo que ComponentActivity hace, y más.

### Para Apps Legacy

Apps usando `AppCompatActivity` deben cambiar a `FragmentActivity`, pero como `AppCompatActivity` extiende `FragmentActivity`, es solo cuestión de cambiar el import:
```kotlin
// Funciona (AppCompatActivity IS-A FragmentActivity)
class MainActivity : AppCompatActivity()

// Mejor (más claro)
class MainActivity : FragmentActivity()
```

---

## Revisión Futura

Si Android introduce una API de BiometricPrompt que funcione sin Fragments (improbable), se puede reconsiderar este requisito en una versión mayor (v2.0+).

Por ahora, este es el enfoque más simple, robusto y mantenible.