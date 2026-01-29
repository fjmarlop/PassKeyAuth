# Development Guide

Guia para desarrolladores que trabajan en PasskeyAuth SDK.

## Arquitectura del Proyecto

### Modulos

#### passkeyauth-core
**Proposito:** Logica de autenticacion sin dependencias de UI.

**Estructura de paquetes:**
```
es.fjmarlop.corpsecauth.core/
â”œâ”€â”€ auth/               # BiometricAuthenticator
â”œâ”€â”€ crypto/             # KeyStoreManager, CryptoProvider, EncryptedData
â”œâ”€â”€ enrollment/         # EnrollmentManager
â”œâ”€â”€ errors/             # Jerarquia de PasskeyAuthException (6 archivos)
â”œâ”€â”€ firebase/           # FirebaseAuthManager, DeviceBindingManager
â”œâ”€â”€ models/             # AuthResult, AuthUser, BiometricConfig, EnrollmentState, DeviceInfo
â””â”€â”€ storage/            # SecureStorage (DataStore wrapper)
```

**Archivos implementados (19 total):**
- Models: 5
- Exceptions: 6
- Crypto: 3
- Auth: 1
- Firebase: 2
- Storage: 1
- Enrollment: 1

**Testing:** Unit tests + Integration tests (Robolectric) - Pendiente

#### passkeyauth-ui
**Proposito:** Componentes Compose opcionales.

**Paquetes (en desarrollo):**
```
es.fjmarlop.corpsecauth.ui/
â”œâ”€â”€ components/         # Componentes reutilizables
â”œâ”€â”€ screens/            # Pantallas completas (Enrollment, Auth)
â””â”€â”€ theme/              # Temas y colores
```

**Testing:** Compose UI tests - Pendiente

#### sample
**Proposito:** App de demostracion y validacion.

**Escenarios (planeados):**
- Enrollment flow completo
- Login biometrico
- Revocacion de dispositivo
- Manejo de errores

## Setup de Desarrollo

### Requisitos
- Android Studio Ladybug o superior
- JDK 17
- Android SDK 35
- Dispositivo fisico con biometria (emulador no soporta StrongBox)

### Primera Configuracion
```bash
# 1. Clonar repo
git clone https://github.com/fjmarlop/PasskeyAuth.git
cd PasskeyAuth

# 2. Crear local.properties (si no existe)
echo "sdk.dir=C:\\Users\\TuUsuario\\AppData\\Local\\Android\\Sdk" > local.properties

# 3. Build inicial
.\gradlew.bat build

# 4. Abrir en Android Studio
# File > Open > seleccionar carpeta PasskeyAuth
```

### Firebase Setup (Para Sample App)

1. Crear proyecto en [Firebase Console](https://console.firebase.google.com)
2. Agregar app Android: `es.fjmarlop.corpsecauth.sample`
3. Descargar `google-services.json`
4. Copiar a `sample/google-services.json`
5. Habilitar Authentication > Email/Password en Firebase Console

## Convenciones de Codigo

### Naming

- **Clases:** PascalCase (`BiometricAuthenticator`)
- **Funciones:** camelCase (`authenticateUser()`)
- **Constantes:** UPPER_SNAKE_CASE (`MAX_RETRY_ATTEMPTS`)
- **Packages:** lowercase (`crypto`, `auth`)

### Comentarios

**Decisiones de seguridad:** Espaniol
```kotlin
// SEGURIDAD: Usamos StrongBox cuando este disponible porque proporciona
// aislamiento hardware completo de las claves. Fallback a TEE si no existe.
```

**API publica:** KDoc en ingles
```kotlin
/**
 * Authenticates the user using biometric credentials.
 *
 * @return [Result] containing [AuthToken] on success or [PasskeyAuthException] on failure
 * @throws IllegalStateException if device is not enrolled
 */
suspend fun authenticate(): Result<AuthToken>
```

### Error Handling

Usar `Result<T>` para todas las operaciones que pueden fallar:
```kotlin
suspend fun enrollDevice(userId: String, password: String): Result<Unit> {
    return try {
        // Operacion
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(PasskeyAuthException("Enrollment failed", e))
    }
}
```

### Coroutines

- Todas las operaciones async son `suspend fun`
- Usar `Dispatchers.IO` para operaciones de red/disco
- Usar `Dispatchers.Main` para UI
- Usar `SupervisorJob` para trabajos independientes

## Testing

### Unit Tests (Pendiente)

Ubicacion: `src/test/java/`
```kotlin
@Test
fun `authenticate should return success when biometric succeeds`() = runTest {
    // Given
    val authenticator = BiometricAuthenticator(mockContext)
    coEvery { mockBiometricPrompt.authenticate() } returns AuthResult.Success
    
    // When
    val result = authenticator.authenticate()
    
    // Then
    assertTrue(result.isSuccess)
}
```

### Integration Tests (Pendiente)

Ubicacion: `src/androidTest/java/`
```kotlin
@RunWith(RobolectricTestRunner::class)
class KeyStoreManagerTest {
    @Test
    fun `should generate key in KeyStore`() {
        val manager = KeyStoreManager(ApplicationProvider.getApplicationContext())
        val result = manager.generateKey()
        assertTrue(result.isSuccess)
    }
}
```

### Ejecutar Tests
```bash
# Unit tests
.\gradlew.bat test

# Android tests
.\gradlew.bat connectedAndroidTest

# Con coverage
.\gradlew.bat jacocoTestReport
```

## Decisiones Arquitectonicas (ADRs)

Toda decision importante debe documentarse en `docs/adr/`.

**Template:** `docs/adr/template.md`

**Proceso:**
1. Crear archivo `XXX-titulo-decision.md`
2. Seguir template
3. Estado inicial: `Propuesto`
4. Despues de revision: `Aceptado` o `Rechazado`
5. Si se cambia despues: `Deprecated` + link a nuevo ADR

**ADRs existentes:**
- ADR-001: Estructura multi-modulo
- ADR-002: Result<T> para errores
- ADR-003: Suspend functions
- ADR-004: KeyStoreManager con AES-GCM y StrongBox
- ADR-005: Sistema de logging configurable
- ADR-006: EnrollmentManager transaccional con Flow

## Seguridad

### Checklist Antes de Commit

- [ ] No hay claves hardcodeadas
- [ ] No hay logs de informacion sensible
- [ ] Cifrado aplicado a datos en storage
- [ ] Input validation en boundary points
- [ ] Error messages no revelan info de sistema

### Security Tests (Pendiente)

Ubicacion: `src/androidTest/java/security/`
```kotlin
@Test
fun `keys should not be extractable from KeyStore`() {
    val keyStore = KeyStore.getInstance("AndroidKeyStore")
    keyStore.load(null)
    val key = keyStore.getKey("test_alias", null)
    
    // Deberia lanzar excepcion porque las claves en KeyStore no son extractables
    assertThrows<KeyStoreException> {
        key.encoded
    }
}
```

## Release Process (Futuro)

1. Update version en `gradle.properties`
2. Update `CHANGELOG.md`
3. Run full test suite
4. Create release branch: `release/vX.Y.Z`
5. Build release artifacts: `.\gradlew.bat assembleRelease`
6. Tag commit: `git tag vX.Y.Z`
7. Publish to Maven Central
8. Create GitHub Release

## Metricas de Calidad

### Code Coverage Target
- Core: >80%
- UI: >60%
- Sample: >40%

### Static Analysis
```bash
.\gradlew.bat detekt
.\gradlew.bat lint
```

## Debugging

### Logs

Actualmente usando `println()` para desarrollo:
```kotlin
println("ðŸ” KeyStoreManager: Clave generada exitosamente")
```

**Futuro (v0.3):** Implementar PasskeyAuthLogger configurable segun ADR-005.

**Nunca loguear:**
- Contrasenias
- Tokens de sesion
- Device IDs sin ofuscar
- PII (Personally Identifiable Information)

### Android Studio Profiler

Para analizar performance de crypto operations:
1. Run > Profile 'sample'
2. CPU Profiler > Record
3. Ejecutar operacion
4. Stop recording > analizar flame chart

## Contacto

Preguntas sobre desarrollo: [Abrir issue en GitHub]

---

**Autor:** Francisco Javier Marmolejo Lopez  
**Last updated:** 2026-01-18


## Common Issues

### ClassCastException: Cannot cast to FragmentActivity

**Error:**
```
java.lang.ClassCastException: MainActivity cannot be cast to 
androidx.fragment.app.FragmentActivity
```

**Cause:** BiometricPrompt internally requires `FragmentActivity`.

**Solution:** Change your MainActivity to extend `FragmentActivity`:
```kotlin
import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Your initialization code
    }
}
```

**Why does this happen?**

BiometricPrompt uses Fragment transactions internally to show the authentication dialog. This requires the host Activity to be a `FragmentActivity`. Using `ComponentActivity` or `AppCompatActivity` will result in a ClassCastException at runtime.

---
