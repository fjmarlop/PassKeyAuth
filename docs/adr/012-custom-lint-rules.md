# ADR-012: Custom Lint Rules para enforcing del contrato del SDK

**Estado:** Aceptado
**Fecha:** 2026-06-12
**Autores:** Francisco Javier Marmolejo López

---

## Contexto

Tres ADRs anteriores documentan requisitos del SDK que el código del consumer puede violar:

- **ADR-007** (FragmentActivity Required) — pasar `ComponentActivity` causa `ClassCastException` en runtime. Solo documentación + error claro al fallar.
- **ADR-009** (Client-Side Security Responsibility) — el bug del SplashScreen (`if (isDeviceEnrolled()) navigateToHome()`) salta verificación biométrica. Documentación + sample app, pero el SDK explícitamente NO fuerza la corrección.
- **README "Implementar Lifecycle Hooks (CRÍTICO)"** — sin `onAppForeground()` / `onAppBackground()` en `onStart()` / `onStop()`, el `sessionTimeoutMinutes` queda silenciosamente roto.

**Todos estos requisitos viven hoy solo en documentación.** Si el desarrollador no la lee, los bugs entran en producción.

## Decisión

Crear un módulo **`passkeyauth-lint`** con **lint rules custom** que detectan estas violaciones **en compile time** del consumer, sin tocar runtime ni la API pública.

Las rules se distribuyen automáticamente vía `lintPublish(project(":passkeyauth-lint"))` en `passkeyauth-core`. Los consumers reciben las checks sin configuración extra cuando ejecutan `./gradlew lint` en su app.

### Rules implementadas

| # | ID | Severidad | Detecta |
|---|---|---|---|
| L1 | `PasskeyAuthMissingFragmentActivity` | **ERROR** | Llamada a `enrollDevice()` / `authenticate()` con Activity que NO extiende `FragmentActivity` (ADR-007) |
| L2 | `PasskeyAuthSkipBiometricNavigation` | **WARNING** | `if (isDeviceEnrolled()) navigate*()` sin `authenticate()` en el branch (ADR-009) |
| L3 | `PasskeyAuthMissingLifecycleHooks` | **WARNING** | Clase que llama `initialize()` sin llamar también a `onAppForeground` y `onAppBackground` |

### Justificación de severidades

- **L1 = ERROR:** runtime crash garantizado (`ClassCastException`), no es edge case.
- **L2 = WARNING:** ADR-009 documenta explícitamente que el SDK respeta la autonomía del developer. Existen casos legítimos (apps de baja seguridad, redes internas). Solo avisamos.
- **L3 = WARNING:** el SDK no crashea sin los hooks, pero `sessionTimeoutMinutes` queda silenciosamente roto. El consumer puede no notarlo.

---

## Stack técnico

- **Versión de lint:** 32.0.0 (regla: lint version = AGP version + 23 desde AGP 7.0; AGP 9.0.0 → Lint 32.0.0)
- **Dependencias:** `com.android.tools.lint:lint-api`, `lint-checks`, `lint-tests`
- **Plugin:** `org.jetbrains.kotlin.jvm` (módulo JVM-only, no Android library)
- **Tests:** `LintDetectorTest` de `lint-tests`, requieren `@RunWith(JUnit4::class)` porque `LintDetectorTest` extiende `junit.framework.TestCase` (JUnit 3) y sin el runner forzado, las rutinas `@Test` con nombres no `testXxx` no se ejecutan.
- **Registry:** `PasskeyAuthIssueRegistry` registrada vía `Lint-Registry-v2` en JAR manifest (más simple que `META-INF/services` para una sola entrada).

---

## Restricciones del análisis estático (documentadas en cada rule)

### L1 — `MissingFragmentActivityDetector`
- ✅ Detecta `FragmentActivity`, `AppCompatActivity`, `ComponentActivity`, y subclasses custom
- ✅ Funciona con named arguments en cualquier orden (uso de `getArgumentForParameter` por nombre)
- ⚠️ NO detecta si el tipo del argumento se infiere via Smart Cast a un tipo desconocido

### L2 — `SkipBiometricNavigationDetector`
- ✅ Detecta `if (isDeviceEnrolled()) navigate()` sin authenticate
- ✅ Detecta varios keywords de navegación (`navigate`, `startActivity`, `Intent`, `composable`)
- ⚠️ **No cubre la forma `when`** del patrón. Skip de `TestMode.IF_TO_WHEN` documentado. La forma `if` es la canónica del bug histórico (ver ADR-009). Mejora futura si surge necesidad.
- ⚠️ Falsos positivos posibles cuando authenticate se llama en función auxiliar dentro del branch
- ⚠️ Falsos negativos: navegación via reflection, callbacks asíncronos

### L3 — `MissingLifecycleHooksDetector`
- ✅ AST traversal robusto (sobrevive transformaciones de whitespace, comentarios)
- ✅ Detecta llamadas en cualquier método de la clase (no solo en `onCreate`)
- ⚠️ Falsos negativos si los hooks están en `Application` con `ProcessLifecycleOwner`. Workaround: `@Suppress("PasskeyAuthMissingLifecycleHooks")`.

---

## Tests

12 tests verdes en `passkeyauth-lint/src/test/`:

| Detector | Tests |
|---|---|
| L1 | 4 (FragmentActivity directo / AppCompatActivity / ComponentActivity / authenticate también) |
| L2 | 4 (bug del SplashScreen / authenticate evita warning / sin navegación → clean / startActivity también) |
| L3 | 4 (sin hooks / ambos hooks / solo uno / clase sin initialize → clean) |

Comandos:
```bash
./gradlew :passkeyauth-lint:test                    # ejecuta los 12 tests
./gradlew :passkeyauth-lint:build                   # compila el módulo
./gradlew :passkeyauth-core:assemble                # incluye lint rules en el AAR
./gradlew :sample:lint                              # rules aplican al sample app (consumer)
```

---

## Alternativas consideradas

### 1. Solo documentación (status quo)
**Rechazado:** ADR-009 ya documentó el bug del SplashScreen y aun así se introdujo. La documentación no se lee.

### 2. Validación runtime con excepciones
**Rechazado para L1:** ya tira `ClassCastException` runtime — el problema es que es tarde (en producción).
**Rechazado para L2 y L3:** son patrones que el SDK no puede detectar runtime de forma fiable.

### 3. Annotations + `@RequiresFragmentActivity` con APT
**Rechazado:** sumar APT al SDK es invasivo para los consumers (afecta sus build times). Lint rules son menos invasivas y más estándar.

### 4. Detección de patrones en `assembleDebug` del consumer vía Gradle task custom
**Rechazado:** complejo de mantener, no integra con Android Studio. Lint es la herramienta canónica de Google para esto.

---

## Consecuencias

### Positivas
- ✅ Bugs detectados **en compile time del consumer**, antes de que lleguen a runtime
- ✅ Mensajes accionables con referencia a ADR + fix sugerido (copy-pasteable)
- ✅ Cero coste para consumers que no opted-in — los warnings se ven solo al ejecutar `./gradlew lint` o en Android Studio Editor
- ✅ Severidades configurables por consumer (`lint.xml`)
- ✅ ADR-009 cierra su gap principal: la guía técnica que documentaba sin enforcing ahora tiene enforcing parcial

### Negativas
- ⚠️ Nuevo módulo Gradle a mantener (`passkeyauth-lint`)
- ⚠️ Lint version atada a AGP version (cada upgrade de AGP requiere bump de lint)
- ⚠️ Falsos positivos posibles (mitigado: severidad WARNING + `@Suppress` documentado)
- ⚠️ Tests de lint son lentos (~1-7s cada uno, lint runner pesado)

### Neutral
- ⚪ Las rules NO bloquean el build por defecto (el consumer decide la severity vía `lint.xml`)
- ⚪ Visible en Android Studio Editor como subrayado — UX consistente con lint nativo

---

## Estructura del módulo

```
passkeyauth-lint/
├── build.gradle.kts                              # plugin kotlin-jvm + lint-api + Lint-Registry-v2 manifest
├── src/main/java/.../lint/
│   ├── PasskeyAuthIssueRegistry.kt               # IssueRegistry con las 3 issues
│   ├── MissingFragmentActivityDetector.kt        # L1
│   ├── SkipBiometricNavigationDetector.kt        # L2
│   └── MissingLifecycleHooksDetector.kt          # L3
└── src/test/java/.../lint/
    ├── MissingFragmentActivityDetectorTest.kt    # 4 tests
    ├── SkipBiometricNavigationDetectorTest.kt    # 4 tests
    └── MissingLifecycleHooksDetectorTest.kt      # 4 tests
```

## Cómo añadir una nueva rule

1. Crear `<NombreDetector>.kt` en `src/main/java/.../lint/`
2. Implementar `Detector + SourceCodeScanner` (o variante apropiada)
3. Definir `companion object { val ISSUE = Issue.create(...) }`
4. Añadir `ISSUE` a la lista en `PasskeyAuthIssueRegistry.issues`
5. Crear `<NombreDetector>Test.kt` con `@RunWith(JUnit4::class) class ... : LintDetectorTest()`
6. Añadir `.allowMissingSdk()` a cada llamada `.run()`
7. Verificar `./gradlew :passkeyauth-lint:test`

---

## Lint configuration trick (gotchas descubiertos durante implementación)

1. **JUnit 3 vs 4:** `LintDetectorTest` extiende `junit.framework.TestCase` (JUnit 3). Tests con `@Test` y nombres no `testXxx` se ignoran silenciosamente sin `@RunWith(JUnit4::class)`.
2. **`allowMissingSdk()`:** los tests JVM puros no tienen Android SDK configurado. Sin este flag, fallan con "This test requires an Android SDK".
3. **REORDER_ARGUMENTS test mode:** lint testa con named arguments reordenados. Detectores que asumen posición 0 fallan. Usar `getArgumentForParameter` por nombre.
4. **WHITESPACE test mode:** text matching (`classText.contains("...")`) falla cuando lint añade whitespace entre tokens. Usar AST traversal con `AbstractUastVisitor`.
5. **IF_TO_WHEN test mode:** lint convierte `if` en `when`. Detectores que solo manejan `UIfExpression` fallan. Opciones: handle ambos, o skip el test mode con razón documentada.
6. **`lintPublish` requiere `isTransitive = false`:** sin esto, kotlin-stdlib se añade como segundo jar a la config y falla `prepareLintJarForPublish` con "Found more than one jar".
7. **Mismatch overload PsiElement/UElement:** `context.report(node, ...)` es ambiguo cuando `node` es `UClass`. Cast explícito: `node as UElement`.

---

## Plan de evolución

| Iteración | Posibles rules futuras |
|---|---|
| v1 (esta ADR) | L1 + L2 + L3 |
| v2 | L4: detectar inicializaciones simultáneas en múltiples Activities |
| v2 | L5: detectar uso de `getCurrentUser()` sin previo `authenticate()` para acceder a info sensible |
| v3 | Soporte para la forma `when` en L2 |
| v3 | QuickFix automático para L1 (cambiar superclass) |

---

## Referencias

- [Android Lint API documentation](https://googlesamples.github.io/android-custom-lint-rules/)
- [Tor's "Writing your own Lint Checks" series](https://github.com/googlesamples/android-custom-lint-rules)
- ADR-007 — FragmentActivity Requirement
- ADR-009 — Client-Side Security Responsibility
- ADR-011 — Testing Stack (los tests de lint siguen el mismo patrón canónico)
