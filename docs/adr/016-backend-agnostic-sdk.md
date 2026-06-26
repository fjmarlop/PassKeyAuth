# ADR-016: SDK Backend-Agnostic — Inyección de AuthBackend y DeviceRegistry

**Estado:** Aceptado
**Fecha:** 2026-06-25
**Autores:** Francisco Javier Marmolejo López

---

## Contexto

El SDK v0.3.x tenía `AuthBackend`, `DeviceRegistry` y `PasswordManagementBackend` como
interfaces `internal` en el paquete `core.firebase`. Esto impedía que integradores
implementaran backends alternativos (Keycloak, REST custom, etc.) sin modificar el SDK.

Además, `Credentials` y `AuthSession` eran `internal`, lo que impedía al integrador
construir instancias de estos tipos (necesario para implementar la interfaz `AuthBackend`).

El plan completo vive en [`docs/plans/2026-06-25-backend-agnostic-v0.4.0.md`](../plans/2026-06-25-backend-agnostic-v0.4.0.md).

---

## Decisión

### 1. Contratos públicos en el paquete raíz

Las tres interfaces de backend se mueven a `es.fjmarlop.corpsecauth` (mismo paquete
que `PasskeyAuth`, `PasskeyAuthConfig`, etc.) como tipos `public`:

- `AuthBackend` — autenticación
- `DeviceRegistry` — binding usuario-dispositivo
- `PasswordManagementBackend` — capability opcional de gestión de password

Los modelos `Credentials` y `AuthSession` pasan a `public`.

### 2. Inyección opcional en `initialize()`

```kotlin
PasskeyAuth.initialize(
    context = this,
    config = PasskeyAuthConfig.Default,           // sin cambios breaking
    authBackend = MyKeycloakBackend(),            // NUEVO — opcional
    deviceRegistry = MyPostgresDeviceRegistry()   // NUEVO — opcional
)
```

Si no se pasan, el SDK usa Firebase como antes. Cero cambios breaking.

### 3. Firebase como referencia incluida

`FirebaseAuthBackend` y `FirestoreDeviceRegistry` siguen incluidos en el SDK
como implementaciones `internal`. Son la opción por defecto y no requieren
configuración adicional por parte del integrador.

`PasswordManagementBackend` funciona como capability: si el `authBackend` inyectado
la implementa, se usa; si no, se usa `FirebaseAuthBackend`. El paso 2 del enrollment
(invalidación de password temporal) está actualmente comentado en `EnrollmentManager`;
se revisará en v0.5.0.

### 4. Limitaciones conocidas (candidatas para v0.5.0)

- `getCurrentUser()` es síncrono (modelo Firebase con cache). Backends OIDC sin
  cache necesitan un adaptador de coroutine con timeout.
- `FirebaseException` es el tipo de error más común en los `Result.failure` internos.
  Renombrar a `AuthBackendException` genérico está pendiente para v0.5.0.
- La extracción de `passkeyauth-firebase` como módulo Gradle separado (para hacer
  Firebase verdaderamente opcional) está pendiente para v0.5.0.

---

## Consecuencias

**Positivas:**
- Integradores pueden usar Keycloak, Auth0, backends REST propios.
- La abstracción ya existía como `internal` — el riesgo de regresión es mínimo.
- Cero cambios breaking para integradores existentes.

**Negativas / trade-offs:**
- `DeviceRegistry.createDefault()` expone una referencia a `FirestoreDeviceRegistry`
  desde un tipo público. Es opaca (tipo de retorno `DeviceRegistry`) pero indica
  que Firebase sigue acoplado al módulo hasta v0.5.0.
- `PasswordManagementBackend` es `public` pero el paso 2 del enrollment (que la usa)
  sigue comentado en `EnrollmentManager`. La interfaz es API pública sin flujo activo.

---

## Alternativas consideradas

- **Módulo `passkeyauth-firebase` opcional:** más limpio, pero requiere reestructurar
  el grafo de dependencias Gradle. Aplazado a v0.5.0.
- **Mantener todo `internal` con factory method:** rechazado — el integrador no puede
  implementar la interfaz si es `internal`.

---

## Referencias

- Plan de implementación: [`docs/plans/2026-06-25-backend-agnostic-v0.4.0.md`](../plans/2026-06-25-backend-agnostic-v0.4.0.md)
- ADR-010: Contrato del backend de autenticación (Path C)
- ADR-013: Invariantes de seguridad no negociables
- ADR-015: Runtime integrity hardening (fase anterior)
