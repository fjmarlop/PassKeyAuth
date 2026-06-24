package es.fjmarlop.corpsecauth

/**
 * Configuración de PasskeyAuth SDK: seguridad y política de integración.
 *
 * Invariantes NO configurables — son la garantía del SDK, no un knob (ADR-013/015):
 *  · Verificación = BIOMETRIC_STRONG. Nunca DEVICE_CREDENTIAL en la ceremonia.
 *  · Claves hardware-backed (StrongBox → TEE). No existe modo software.
 *  · Sin setUserAuthenticationValidityDurationSeconds con ventana.
 *  · FLAG_SECURE en las pantallas del SDK (anti-screenshot).
 *  · Anti-debug en builds de release.
 *
 * @property allowHostFallback Si true, cuando el SDK no puede operar (NoHardware)
 *   ofrece devolver el control al login propio del host. Default estricto: false.
 * @property strongBox Política de hardware. Reemplaza requireStrongBox.
 * @property recovery Recuperación controlada y auditable. null = sin recuperación in-app.
 * @property sessionTimeoutMinutes Minutos de inactividad antes de re-pedir biometría.
 *   0 = siempre pide; >0 = ventana; -1 = nunca invalida (solo testing).
 * @property rootPolicy Política frente a root/hooking. Default: Block. Ver ADR-015.
 * @property emulatorPolicy Política frente a emuladores. Ver ADR-015.
 * @property enablePrivacyOverlay Si true, las pantallas del SDK muestran un overlay
 *   opaco al pasar a segundo plano (complementa FLAG_SECURE). Default: true.
 */
sealed class PasskeyAuthConfig(
    val allowHostFallback: Boolean,
    val strongBox: StrongBoxPolicy,
    val recovery: RecoveryHandler?,
    val sessionTimeoutMinutes: Int,
    val rootPolicy: RootPolicy,
    val emulatorPolicy: EmulatorPolicy,
    val enablePrivacyOverlay: Boolean,
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
        rootPolicy = RootPolicy.Block,
        emulatorPolicy = EmulatorPolicy.Block,
        enablePrivacyOverlay = true,
    )

    /** Debugging: timeout 0 (siempre pide biometría), tolerante con entornos de desarrollo. */
    object Debug : PasskeyAuthConfig(
        allowHostFallback = false,
        strongBox = StrongBoxPolicy.Preferred,
        recovery = null,
        sessionTimeoutMinutes = 0,
        rootPolicy = RootPolicy.Warn,
        emulatorPolicy = EmulatorPolicy.Allow,
        enablePrivacyOverlay = true,
    )

    /**
     * Configuración personalizada con defaults estrictos.
     *
     * Nota: `emulatorPolicy` por defecto es [EmulatorPolicy.Warn] (no Block) para no
     * romper el desarrollo en emuladores; súbelo a Block para producción endurecida.
     */
    class Custom(
        allowHostFallback: Boolean = false,
        strongBox: StrongBoxPolicy = StrongBoxPolicy.Preferred,
        recovery: RecoveryHandler? = null,
        sessionTimeoutMinutes: Int = 5,
        rootPolicy: RootPolicy = RootPolicy.Block,
        emulatorPolicy: EmulatorPolicy = EmulatorPolicy.Warn,
        enablePrivacyOverlay: Boolean = true,
    ) : PasskeyAuthConfig(
        allowHostFallback,
        strongBox,
        recovery,
        sessionTimeoutMinutes,
        rootPolicy,
        emulatorPolicy,
        enablePrivacyOverlay,
    )
}
