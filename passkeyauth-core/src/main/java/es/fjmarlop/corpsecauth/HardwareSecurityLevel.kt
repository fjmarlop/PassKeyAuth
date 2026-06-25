package es.fjmarlop.corpsecauth

/**
 * Nivel de seguridad hardware verificado tras la generación de clave (ADR-015, D2).
 *
 * Obtenido via [android.security.keystore.KeyInfo]: no es una garantía criptográfica
 * firmada, pero en un entorno de ejecución íntegro (garantizado por [IntegrityGuard])
 * es un indicador fiable del tipo de hardware que custodia la clave.
 *
 * Uso recomendado: auditoría/logging en el lado del integrador. No usar como único
 * mecanismo de seguridad — complementar con [RootPolicy.Block] e [IntegrityGuard].
 */
enum class HardwareSecurityLevel {
    /** Chip de seguridad dedicado (Pixel 3+, Samsung S9+). Máxima protección. */
    STRONGBOX,

    /** TEE (Trusted Execution Environment) — seguro pero comparte silicio con el CPU. */
    TRUSTED_ENVIRONMENT,

    /** No respaldada por hardware. Indica dispositivo sin TEE, emulador o compromiso. */
    SOFTWARE,

    /** Attestation no disponible o falló (KeyInfo inaccesible en este entorno). */
    UNKNOWN
}
