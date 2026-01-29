# ADR-002: Result<T> para Manejo de Errores

**Fecha:** 2026-01-17
**Estado:** Aceptado

## Contexto
Necesitamos un mecanismo consistente para manejar errores en operaciones asincronas.

## Opciones Consideradas
1. Exceptions tradicionales
2. Kotlin Result<T>
3. Sealed classes custom (Success/Error)
4. Arrow Either<L,R>

## Decision
Usar `Result<T>` de Kotlin stdlib con excepciones custom.

## Justificacion
- Result<T> es idiomatico en Kotlin moderno
- Integracion natural con suspend functions
- No requiere dependencias adicionales
- Fuerza manejo explicito de errores

## Consecuencias

### Positivas
- API clara: `suspend fun authenticate(): Result<AuthToken>`
- El compilador fuerza manejo de errores
- Testing simplificado con `Result.success()` y `Result.failure()`

### Negativas
- Los clientes deben estar familiarizados con Result<T>

## Ejemplo de Uso
```kotlin
lifecycleScope.launch {
    passkeyAuth.authenticate()
        .onSuccess { token -> 
            // Navegar a home
        }
        .onFailure { error ->
            // Mostrar error
        }
}
```

## Referencias
- https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-result/