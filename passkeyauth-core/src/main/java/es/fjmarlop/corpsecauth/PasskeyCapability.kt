package es.fjmarlop.corpsecauth

/**
 * Estado de capacidad biométrica del dispositivo (consulta no-lanzante).
 * La UI usa esto para decidir qué pintar ANTES de mostrar un CTA (ver ADR-013/ADR-014).
 */
sealed interface PasskeyCapability {
    /** BIOMETRIC_STRONG disponible y con biometría registrada. */
    data object Ready : PasskeyCapability
    /** Compatible pero sin biometría registrada. RECUPERABLE → ACTION_BIOMETRIC_ENROLL. */
    data object NotEnrolled : PasskeyCapability
    /** Sin hardware biométrico fuerte. Bloqueo real. */
    data object NoHardware : PasskeyCapability
    /** Hardware presente pero temporalmente no disponible. */
    data object TemporarilyUnavailable : PasskeyCapability
    /** Requiere actualización de seguridad del sistema. Bloqueo. */
    data object SecurityUpdateRequired : PasskeyCapability
}
