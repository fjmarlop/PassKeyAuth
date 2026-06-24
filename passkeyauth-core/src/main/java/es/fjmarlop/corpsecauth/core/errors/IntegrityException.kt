package es.fjmarlop.corpsecauth.core.errors

/**
 * Errores de integridad del entorno de ejecución (ADR-015).
 *
 * Se lanzan en `PasskeyAuth.initialize()` cuando la política configurada
 * ([es.fjmarlop.corpsecauth.RootPolicy] / [es.fjmarlop.corpsecauth.EmulatorPolicy])
 * es `Block` y se detecta un entorno comprometido. La garantía de seguridad del
 * SDK no puede sostenerse en estos entornos, por lo que se rechaza la operación.
 */
sealed class IntegrityException(
    message: String,
    cause: Throwable? = null
) : PasskeyAuthException(message, cause) {

    /** Device rooteado (binarios `su`, apps de root, build tags inseguros, etc.). */
    class RootDetected(
        message: String = "Dispositivo rooteado detectado"
    ) : IntegrityException(message) {
        override val errorCode = "INTEGRITY_ROOT_DETECTED"
        override fun getUserMessage() =
            "Este dispositivo no es seguro para autenticación. Contacta con IT."
    }

    /** Emulador (no tiene hardware biométrico real). */
    class EmulatorDetected(
        message: String = "Emulador detectado"
    ) : IntegrityException(message) {
        override val errorCode = "INTEGRITY_EMULATOR_DETECTED"
        override fun getUserMessage() =
            "La autenticación biométrica no está disponible en emuladores."
    }

    /** Framework de hooking (Frida, Xposed) o instrumentación dinámica. */
    class HookingDetected(
        message: String = "Framework de instrumentación detectado"
    ) : IntegrityException(message) {
        override val errorCode = "INTEGRITY_HOOKING_DETECTED"
        override fun getUserMessage() =
            "Este dispositivo no es seguro para autenticación. Contacta con IT."
    }

    /** Depurador adjunto en un build de release. */
    class DebuggerAttached(
        message: String = "Depurador adjunto en build de release"
    ) : IntegrityException(message) {
        override val errorCode = "INTEGRITY_DEBUGGER_ATTACHED"
        override fun getUserMessage() =
            "Este dispositivo no es seguro para autenticación."
    }
}
