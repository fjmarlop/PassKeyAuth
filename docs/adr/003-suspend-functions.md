# ADR-003: Suspend Functions sobre Callbacks

**Fecha:** 2026-01-17
**Estado:** Aceptado

## Contexto
BiometricPrompt y Firebase usan callbacks. Debemos decidir la API publica.

## Decision
Usar suspend functions en toda la API publica, wrapeando callbacks internamente.

## Justificacion
- Compose usa coroutines nativamente
- Evita callback hell
- Cancellation automatica con Job
- Testing mas simple

## Consecuencias

### Positivas
- API moderna y limpia
- Integracion directa con ViewModels

### Negativas
- Los clientes DEBEN usar coroutines

## Implementacion
```kotlin
suspend fun authenticate(): Result<Unit> = suspendCancellableCoroutine { cont ->
    biometricPrompt.authenticate(
        promptInfo,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: AuthenticationResult) {
                cont.resume(Result.success(Unit))
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                cont.resume(Result.failure(BiometricException(errorCode, errString)))
            }
        }
    )
}
```

## Referencias
- https://kotlinlang.org/docs/coroutines-guide.html
- https://developer.android.com/kotlin/coroutines