package es.fjmarlop.corpsecauth.core.models

import es.fjmarlop.corpsecauth.core.errors.PasskeyAuthException

/**
 * Representa el estado de autenticacion del usuario.
 *
 * Esta sealed class permite modelar todos los posibles estados de la sesion
 * de forma type-safe, facilitando el manejo en la UI con when expressions.
 *
 * @see AuthUser
 */
sealed class AuthResult {
    /**
     * Estado inicial o cuando se esta verificando la sesion existente.
     * 
     * Se usa durante:
     * - Inicializacion de la app
     * - Validacion de sesion al volver de background
     * - Verificacion de device binding
     */
    data object Loading : AuthResult()

    /**
     * Usuario autenticado correctamente con sesion activa.
     *
     * @property user Informacion del usuario autenticado desde Firebase
     * 
     * Garantias:
     * - Firebase session valida
     * - Device binding verificado
     * - Biometria configurada en el dispositivo
     */
    data class Authenticated(val user: AuthUser) : AuthResult()

    /**
     * No hay usuario autenticado o la sesion expiro.
     * 
     * Posibles causas:
     * - Usuario nunca hizo login
     * - Logout explicito
     * - Token de Firebase expirado
     * - Device binding revocado remotamente
     */
    data object Unauthenticated : AuthResult()

    /**
     * Error durante el proceso de autenticacion.
     *
     * @property exception Detalle del error ocurrido
     * 
     * Este estado permite manejar errores sin perder el estado anterior,
     * util para mostrar mensajes al usuario sin destruir la sesion.
     */
    data class Error(val exception: PasskeyAuthException) : AuthResult()
}