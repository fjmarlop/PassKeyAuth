# ADR-004: KeyStoreManager con AES-GCM y StrongBox Support

**Fecha:** 2026-01-18
**Estado:** Aceptado

## Contexto y Problema

PasskeyAuth SDK requiere almacenar datos sensibles (tokens de sesión, credenciales) de forma segura en el dispositivo. Necesitamos decidir:

1. **Algoritmo de cifrado:** ¿AES-CBC o AES-GCM?
2. **Hardware security:** ¿Cómo aprovechar StrongBox cuando esté disponible?
3. **Key lifecycle:** ¿Cuándo invalidar claves automáticamente?
4. **Auth timeout:** ¿Siempre requiere biometría o permitir timeout?

## Factores de Decisión

- **Seguridad:** Cumplir OWASP MASVS L2 y NIST SP 800-63B
- **Compatibilidad:** Soportar dispositivos desde Android 8.0 (API 26)
- **UX:** Balance entre seguridad y fricción del usuario
- **Enterprise:** Requisitos de revocación y auditoría

## Opciones Consideradas

### Opción 1: AES-CBC con HMAC
**Pros:**
- Ampliamente soportado (API 23+)
- Bien conocido y documentado
- Compatible con hardware antiguo

**Contras:**
- Requiere HMAC separado para autenticación
- Más código, más superficie de ataque
- No recomendado por NIST para nuevos desarrollos

### Opción 2: AES-GCM sin StrongBox
**Pros:**
- Cifrado autenticado (AEAD)
- Una sola operación para cifrado + autenticación
- Recomendado por NIST/OWASP

**Contras:**
- No aprovecha hardware security moderno
- Vulnerable a ataques side-channel en software

### Opción 3: AES-GCM con StrongBox mandatory
**Pros:**
- Máxima seguridad (hardware aislado)
- Protección contra ataques físicos
- Cumple requisitos enterprise más estrictos

**Contras:**
- Solo disponible en Pixel 3+ y dispositivos high-end
- Excluye ~70% de dispositivos Android

### Opción 4: AES-GCM con StrongBox optional (Elegida)
**Pros:**
- Usa StrongBox cuando disponible, fallback a TEE
- Balance perfecto seguridad/compatibilidad
- Permite modo enterprise con `requireStrongBox`

**Contras:**
- Lógica de fallback más compleja
- Testing en múltiples tipos de hardware

## Decisión

**Usar AES-GCM con StrongBox opcional:**
```kotlin
internal class KeyStoreManager(
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
    private val authTimeoutSeconds: Int = 0, // Siempre requiere auth por defecto
    private val requireStrongBox: Boolean = false
) {
    // Implementación con:
    // - AES-256-GCM (no CBC)
    // - setIsStrongBoxBacked(true) con try-catch
    // - setInvalidatedByBiometricEnrollment(true)
    // - setUserAuthenticationRequired(true)
}
```

**Factory methods:**
```kotlin
KeyStoreManager.createDefault() // Sin timeout, StrongBox optional
KeyStoreManager.createWithTimeout(300) // 5 min timeout
KeyStoreManager.createWithStrongBox() // Falla si no hay StrongBox
```

## Justificación

### Por qué AES-GCM sobre AES-CBC

1. **NIST SP 800-38D recomienda GCM** para nuevas implementaciones
2. **AEAD (Authenticated Encryption with Associated Data):**
   - Cifra Y autentica en una operación
   - Detecta manipulación de ciphertext
   - No necesita HMAC separado (menos código = menos bugs)
3. **OWASP MASVS L2 (Requisito 6.2.6):**
   > "La aplicación utiliza primitivas criptográficas que son apropiadas para el caso de uso particular, configuradas con parámetros que se adhieren a las mejores prácticas de la industria"

### Por qué StrongBox Optional

1. **Seguridad vs Alcance:**
   - ~30% de dispositivos tienen StrongBox (Pixel 3+, Samsung S20+, OnePlus 7T+)
   - ~95% tienen TEE (Trusted Execution Environment)
   - Fallback automático permite máxima cobertura

2. **Enterprise Flexibility:**
   - Modo `requireStrongBox=false`: Apps consumer
   - Modo `requireStrongBox=true`: Apps enterprise sensibles (banca, salud)

3. **Defense in Depth:**
   - Incluso con TEE, las claves están hardware-backed
   - No son extraíbles del KeyStore
   - Requieren autenticación biométrica para uso

### Por qué Invalidación Automática
```kotlin
.setInvalidatedByBiometricEnrollment(true)
```

**Escenario de amenaza:**
1. Usuario pierde el teléfono
2. Atacante registra su propia huella (factory reset no necesario)
3. Sin invalidación: Atacante puede descifrar datos históricos

**Con invalidación:**
- Si cambian las huellas, la clave se vuelve permanentemente inválida
- Usuario debe re-enrollar (login con credenciales temporales)
- Previene acceso a datos cifrados previamente

### Por qué Auth Timeout Configurable

**Por defecto: `authTimeoutSeconds = 0` (siempre requiere auth)**

Razonamiento:
- Máxima seguridad: cada operación requiere huella
- Previene acceso si el dispositivo queda desbloqueado

**Opción: `authTimeoutSeconds = 300` (5 minutos)**

Para casos de uso específicos:
- Apps con operaciones frecuentes (ej: banking app con múltiples transacciones)
- Balance UX/seguridad evaluado por el cliente

## Consecuencias

### Positivas

✅ **Seguridad moderna:** Cumple OWASP MASVS L2, NIST SP 800-63B  
✅ **Hardware-backed:** Claves nunca expuestas a software  
✅ **Autenticación integrada:** GCM detecta manipulación de datos  
✅ **Flexibilidad:** Soporta desde devices básicos hasta enterprise  
✅ **Prevención de ataques:**
   - Side-channel attacks mitigados por hardware
   - Replay attacks prevenidos por IV único
   - Key extraction imposible (no exportable)

### Negativas

❌ **Complejidad de testing:**
   - Necesitamos tests en dispositivos con/sin StrongBox
   - Robolectric tiene limitaciones con KeyStore
   - Requiere tests en dispositivos físicos

❌ **API 28+ para StrongBox:**
   - Devices API 26-27 solo tienen TEE
   - Aceptable: TEE sigue siendo seguro

❌ **GCM IV management:**
   - Debemos guardar IV junto con ciphertext
   - IV nunca debe reutilizarse (mitigado con randomizedEncryption)

### Neutral

⚪ **Performance:**
   - GCM es ligeramente más rápido que CBC+HMAC
   - Overhead de hardware security es mínimo (<50ms)

⚪ **Battery:**
   - Operaciones crypto en hardware consumen menos batería
   - Impacto negligible en uso normal

## Notas de Implementación

### Estructura de EncryptedData
```kotlin
data class EncryptedData(
    val ciphertext: ByteArray,
    val iv: ByteArray // 12 bytes para GCM
)

// Storage en DataStore/Firestore:
fun toBase64String(): String {
    val combined = iv + ciphertext
    return Base64.encodeToString(combined, Base64.NO_WRAP)
}
```

### Key Compromise Detection

Si `getKey()` lanza `KeyPermanentlyInvalidatedException`:
1. Invalidar sesión local
2. Forzar re-enrollment
3. Notificar al usuario (biometría cambió)

### StrongBox Detection
```kotlin
// Test StrongBox availability
try {
    val spec = KeyGenParameterSpec.Builder(...)
        .setIsStrongBoxBacked(true)
        .build()
    keyGenerator.init(spec)
    // StrongBox available
} catch (e: StrongBoxUnavailableException) {
    // Fallback to TEE
}
```

## Alternativas Descartadas

### ChaCha20-Poly1305
- Más rápido en software
- Pero: No disponible en AndroidKeyStore hasta API 28
- Descartado: Perdemos compatibilidad con API 26-27

### RSA + AES Hybrid
- RSA para key wrapping, AES para datos
- Pero: Más complejo, sin beneficio real
- Descartado: Over-engineering

### EncryptedSharedPreferences
- Librería de Jetpack Security
- Pero: No permite integración con BiometricPrompt
- Descartado: No cumple requisito de auth biométrica por operación

## Referencias

- [NIST SP 800-38D - GCM Mode](https://nvlpubs.nist.gov/nistpubs/Legacy/SP/nistspecialpublication800-38d.pdf)
- [OWASP MASVS v2.0 - MASVS-CRYPTO](https://mas.owasp.org/MASVS/05-MASVS-CRYPTO/)
- [Android KeyStore Documentation](https://developer.android.com/training/articles/keystore)
- [Android StrongBox Documentation](https://source.android.com/docs/security/features/keystore)
- [CDD Section 9.11 - Keys and Credentials](https://source.android.com/docs/compatibility/cdd)

## Revisiones

- **2026-01-18:** Creación inicial
- **Próxima revisión:** Al implementar v2.0 con session management avanzado

---
