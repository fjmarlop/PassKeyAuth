package es.fjmarlop.corpsecauth

/**
 * Configuración de PasskeyAuth SDK: seguridad y política de integración.
 *
 * Invariantes NO configurables — son la garantía del SDK, no un knob (ADR-013):
 *  · Verificación = BIOMETRIC_STRONG. Nunca DEVICE_CREDENTIAL en la ceremonia.
 *  · Claves hardware-backed (StrongBox → TEE). No existe modo software.
 *  · Sin setUserAuthenticationValidityDurationSeconds con ventana.
 *
 * @property allowHostFallback Si true, cuando el SDK no puede operar (NoHardware)
 *   ofrece devolver el control al login propio del host. Default estricto: false.
 * @property strongBox Política de hardware. Reemplaza requireStrongBox.
 * @property recovery Recuperación controlada y auditable. null = sin recuperación in-app.
 * @property sessionTimeoutMinutes Minutos de inactividad antes de re-pedir biometría.
 *   0 = siempre pide; >0 = ventana; -1 = nunca invalida (solo testing).
 */
sealed class PasskeyAuthConfig(
    val allowHostFallback: Boolean,
    val strongBox: StrongBoxPolicy,
    val recovery: RecoveryHandler?,
    val sessionTimeoutMinutes: Int,
) {
    /** Compat: derivado de [strongBox]. Ver ADR-013. */
    @Deprecated("Usa strongBox: StrongBoxPolicy", ReplaceWith("strongBox"))
    val requireStrongBox: Boolean get() = strongBox == StrongBoxPolicy.Required

    /** Producción: StrongBox opcional (TEE fallback), timeout 2 min, modo blindado. */
    object Default : PasskeyAuthConfig(
        allowHostFallback = false,
        strongBox = StrongBoxPolicy.Preferred,
        recovery = null,
        sessionTimeoutMinutes = 2,
    )

    /** Debugging: timeout 0 (siempre pide biometría). */
    object Debug : PasskeyAuthConfig(
        allowHostFallback = false,
        strongBox = StrongBoxPolicy.Preferred,
        recovery = null,
        sessionTimeoutMinutes = 0,
    )

    /** Configuración personalizada con defaults estrictos. */
    class Custom(
        allowHostFallback: Boolean = false,
        strongBox: StrongBoxPolicy = StrongBoxPolicy.Preferred,
        recovery: RecoveryHandler? = null,
        sessionTimeoutMinutes: Int = 5,
    ) : PasskeyAuthConfig(allowHostFallback, strongBox, recovery, sessionTimeoutMinutes)
}
