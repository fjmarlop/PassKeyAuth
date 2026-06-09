# Bugfixes

<!--
Bugfixes and workarounds worth remembering for this project.
Append entries with: mobiai brain save bugfix (coming in Phase 2).
Mark temporary workarounds as status: temporary so the agent does not
treat them as permanent decisions.
-->

## SplashScreen permitía bypass de biometría tras reabrir app

**Status:** Resuelto (v0.2.0) · **Severidad:** Crítica · **Referencia:** ADR-009

**Bug:**
```kotlin
// SplashScreen — INSEGURO
LaunchedEffect(Unit) {
    val isAuthenticated = PasskeyAuth.isAuthenticated()
    when {
        isAuthenticated -> navigateToHome()  // ❌ FALLO CRÍTICO
        else -> navigateToLogin()
    }
}
```

**Cómo se reproducía:**
1. Usuario autentica y cierra la app
2. `authState` permanece `Authenticated` en memoria del proceso
3. Usuario reabre la app
4. SplashScreen lee `isAuthenticated()` = true → navega directo a Home
5. **Cualquiera con el dispositivo accede sin biometría**

**Fix:**
```kotlin
// SplashScreen — SEGURO
LaunchedEffect(Unit) {
    when {
        !isEnrolled -> navigateToEnrollment()
        else -> navigateToLogin()  // ✅ SIEMPRE pide biometría
    }
}
```

**Por qué funciona:** No confía en estado de autenticación previo. Siempre fuerza verificación biométrica. Sin race conditions ni bypass.

**Lección:** `isAuthenticated()` refleja estado en memoria, no autenticación reciente verificada. En cold start, hay que tratar al usuario como no autenticado. El sample app es la referencia que los integradores copian — debe ser impecable.

---

## ClassCastException: MainActivity cannot be cast to FragmentActivity

**Status:** Documentado como requisito (no es bug del SDK) · **Referencia:** ADR-007

**Error:**
```
java.lang.ClassCastException: MainActivity cannot be cast to
androidx.fragment.app.FragmentActivity
```

**Causa:** `BiometricPrompt` usa Fragment transactions internamente. Requiere `FragmentActivity`.

**Fix (cliente):**
```kotlin
// Cambiar
class MainActivity : ComponentActivity()
// Por
class MainActivity : FragmentActivity()
```

**Por qué no se resuelve en el SDK:** alternativas evaluadas (wrapper Fragment interno, reflection sobre `ComponentActivity`) introducían fragilidad y memory leaks. Decidido en ADR-007: documentar el requisito.

**Mitigación pendiente:** considerar agregar `require(activity is FragmentActivity)` con mensaje descriptivo. Decisión actual: no agregar — el `ClassCastException` ya es claro.

---

## println() expone información en logcat de release

**Status:** Workaround temporal — pendiente de fix en v0.3 · **Referencia:** ADR-005

**Problema:** El SDK usa `println()` para debugging. En release builds esto:
- Expone info potencialmente sensible en logcat (tokens, emails, deviceIds)
- Viola OWASP MASVS-STORAGE-2
- No se puede deshabilitar sin recompilar

**Workaround actual (v0.1 / v0.2):** Marcar cada `println()` con `// TODO: Migrate to PasskeyAuthLogger`.

**Fix planeado (v0.3):**
1. Implementar `PasskeyAuthLogger` con niveles configurables (`NONE`/`ERROR`/`WARNING`/`INFO`/`DEBUG`/`VERBOSE`)
2. Reemplazar todos los `println()`
3. ProGuard rules en v1.0 para remover `v()`, `d()`, `i()` en release

**Reglas críticas al migrar:** NUNCA loguear email completo, tokens completos, deviceIds sin ofuscar, claves criptográficas. Si se necesita: usar ofuscación (email truncado, token prefix, device hash).

---

## Comentario incorrecto en EnrollmentManager sobre returns de SecureStorage

**Status:** Detectado por el primer test, sin fix de código aún · **Referencia:** ADR-011

**Bug menor:**

En `EnrollmentManager.enrollDevice()` paso 7, el comentario dice:
```kotlin
// saveUserId, saveDeviceId, saveLastActivityTimestamp son suspend pero NO retornan Result
secureStorage.saveUserId(session.user.uid)
secureStorage.saveDeviceId(deviceId)
secureStorage.saveLastActivityTimestamp(System.currentTimeMillis())
```

La realidad: las 3 funciones SÍ devuelven `Result<Unit>`. El comentario es incorrecto.

**Por qué no es bug funcional:** los `Result<Unit>` simplemente se descartan (Kotlin permite ignorar valores de retorno). El comportamiento es el mismo que si devolvieran `Unit`. Pero el comentario engaña al lector.

**Cómo se descubrió:** primer intento de testear con `coJustRun { saveUserId(any()) }` falló porque `coJustRun` solo funciona para `Unit`. Tuvimos que usar `coEvery { ... } returns Result.success(Unit)`.

**Fix pendiente (opcional, low priority):** corregir el comentario, o mejor, manejar los `Result` (al menos loguear los failures) para no perder errores silenciosamente.

---

## SDK usaba android.util.Base64 incompatible con JVM puro

**Status:** ✅ Resuelto · **Referencia:** ADR-011

**Síntoma original:** `EnrollmentManagerHappyPathTest` fallaba en paso 7 con "NullPointerException" porque `android.util.Base64.encodeToString` retorna `null` en JVM puro (con `isReturnDefaultValues=true`).

**Causa raíz:** `EnrollmentManager` paso 5 y `EncryptedData.kt` usaban `android.util.Base64` que es framework Android, no implementado en JVM stub.

**Fix aplicado:**
- `EnrollmentManager.kt` paso 5: `java.util.Base64.getEncoder().encodeToString(iv + ciphertext)`
- `EncryptedData.kt`:
  - `toBase64String()`: `java.util.Base64.getEncoder().encodeToString(combined)`
  - `fromBase64String()`: `java.util.Base64.getDecoder().decode(base64String)`
- Comentario explícito en cada sitio referenciando ADR-011

**Por qué `java.util.Base64` es seguro:** disponible desde Java 8 / API 26 (= nuestro minSdk). El comportamiento es idéntico al `android.util.Base64.NO_WRAP` (no añade newlines).

**Verificación:**
- ✅ `passkeyauth-core:testDebugUnitTest` verde (6/6 tests)
- ✅ `assembleDebug` (full build con sample app) verde
- ✅ Test simplificado: eliminado `mockkStatic`, `unmockkStatic`, `every`, `@After tearDown` del happy path test

**Lección aplicable:** evitar `android.util.*` cuando exista equivalente `java.util.*` con la misma API level mínima — mejora portabilidad y testabilidad sin coste.

---

## EnrollmentManager paso 5 (cifrado) no hace rollback si falla

**Status:** Detectado por inspección al escribir `EnrollmentManagerRollbackTest`, sin fix aún · **Referencia:** ADR-006, ADR-011

**Bug latente:**

En `EnrollmentManager.enrollDevice()` flow, los pasos 4, 6 y 7 tienen `getOrElse { ... }` con rollback explícito. El **paso 5 (cifrado real con `cipher.doFinal()` + Base64)** NO:

```kotlin
// PASO 5: Cifrar token de sesion (SIN rollback explicito)
val token = session.idToken
val ciphertext = authenticatedCipher.doFinal(token.toByteArray(Charsets.UTF_8))  // ← puede lanzar BadPaddingException, IllegalBlockSizeException
val iv = authenticatedCipher.iv
val encryptedBase64 = java.util.Base64.getEncoder().encodeToString(iv + ciphertext)
```

Si `doFinal()` lanza, cae al `catch (e: Exception)` outer del flow:

```kotlin
} catch (e: Exception) {
    emit(EnrollmentState.Error(wrapException(e)))  // ← NO borra key, NO sign out
}
```

**Consecuencia:** queda la clave en KeyStore + sesión Firebase activa pero sin device binding ni token guardado → estado inconsistente que viola la garantía "todo o nada" del ADR-006.

**Probabilidad de ocurrencia:** baja. `cipher.doFinal()` solo falla si el cipher quedó en estado inválido entre la autenticación biométrica (paso 4) y este uso. En la práctica con `BiometricPrompt.CryptoObject` esto no debería ocurrir, pero la defensa en profundidad lo exige.

**Fix sugerido:**

```kotlin
// PASO 5: Cifrar token de sesion
val encryptedBase64 = try {
    val token = session.idToken
    val ciphertext = authenticatedCipher.doFinal(token.toByteArray(Charsets.UTF_8))
    val iv = authenticatedCipher.iv
    java.util.Base64.getEncoder().encodeToString(iv + ciphertext)
} catch (e: Exception) {
    keyStoreManager.deleteKey()
    authBackend.signOut()
    emit(EnrollmentState.Error(wrapException(CryptoException.EncryptionFailed("Cifrado fallo en paso 5", e))))
    return@flow
}
```

**Test que falta:** cuando se implemente este rollback, añadir `EnrollmentManagerRollbackTest.dado cipher doFinal lanza...` configurando un mock del Cipher que lance en `doFinal()`.

---

## KeyPermanentlyInvalidatedException tras cambio de biometría

**Status:** Comportamiento esperado — manejo definido · **Referencia:** ADR-004

**Síntoma:** Tras agregar/quitar una huella en el dispositivo, las operaciones de descifrado lanzan `KeyPermanentlyInvalidatedException`.

**Causa:** Configuración intencional con `setInvalidatedByBiometricEnrollment(true)` — protege contra atacante que registra su propia huella en un dispositivo robado.

**Manejo obligatorio en el cliente:**
1. Invalidar sesión local
2. Forzar re-enrollment (login con credenciales temporales nuevas)
3. Notificar al usuario que la biometría cambió

**NO es un bug — es defensa contra escenario de amenaza explícito.**
