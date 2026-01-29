package es.fjmarlop.corpsecauth

/**
 * Configuracion de PasskeyAuth SDK.
 *
 * Define el comportamiento del SDK en cuanto a seguridad y timeouts.
 *
 * @property requireStrongBox Si true, requiere StrongBox (falla si no disponible).
 *                            Si false, intenta StrongBox con fallback a TEE.
 * @property sessionTimeoutMinutes Minutos de inactividad antes de requerir biometria.
 *                                 - 0: Siempre pide biometria (maxima seguridad)
 *                                 - 5: Pide biometria despues de 5 min en background
 *                                 - -1: Nunca invalida sesion (solo para testing)
 */
sealed class PasskeyAuthConfig(
    val requireStrongBox: Boolean,
    val sessionTimeoutMinutes: Int
) {
    
    /**
     * Configuracion por defecto para produccion.
     *
     * - StrongBox opcional (fallback a TEE)
     * - Session timeout: 2 minutos
     *
     * Balance entre seguridad y UX.
     */
    object Default : PasskeyAuthConfig(
        requireStrongBox = false,
        sessionTimeoutMinutes = 2
    )
    
    /**
     * Configuracion para debugging.
     *
     * - StrongBox opcional (fallback a TEE)
     * - Session timeout: 0 (siempre pide biometria)
     *
     * Maxima seguridad, util para desarrollo.
     */
    object Debug : PasskeyAuthConfig(
        requireStrongBox = false,
        sessionTimeoutMinutes = 0
    )
    
    /**
     * Configuracion personalizada.
     *
     * Permite ajustar todos los parametros segun necesidades.
     *
     * Ejemplos de uso:
     *
     * App bancaria (maxima seguridad):
     * ```kotlin
     * PasskeyAuthConfig.Custom(
     *     requireStrongBox = true,
     *     sessionTimeoutMinutes = 0  // Siempre pide
     * )
     * ```
     *
     * App corporativa (balance):
     * ```kotlin
     * PasskeyAuthConfig.Custom(
     *     sessionTimeoutMinutes = 5  // 5 minutos
     * )
     * ```
     *
     * App casual (relajado):
     * ```kotlin
     * PasskeyAuthConfig.Custom(
     *     sessionTimeoutMinutes = 15  // 15 minutos
     * )
     * ```
     *
     * Testing (sin timeout):
     * ```kotlin
     * PasskeyAuthConfig.Custom(
     *     sessionTimeoutMinutes = -1  // Sin timeout
     * )
     * ```
     *
     * @param requireStrongBox Si requiere StrongBox obligatorio
     * @param sessionTimeoutMinutes Minutos antes de invalidar sesion
     */
    class Custom(
        requireStrongBox: Boolean = false,
        sessionTimeoutMinutes: Int = 5
    ) : PasskeyAuthConfig(requireStrongBox, sessionTimeoutMinutes)
}