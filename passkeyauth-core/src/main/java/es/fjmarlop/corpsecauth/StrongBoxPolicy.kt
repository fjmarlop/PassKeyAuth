package es.fjmarlop.corpsecauth

/**
 * Política de uso de StrongBox para las claves hardware-backed del SDK.
 *
 * - [Preferred]: intenta StrongBox, cae a TEE con normalidad si no está disponible.
 * - [Required]: exige StrongBox; falla en dispositivos que no lo tengan (alta seguridad).
 *
 * Reemplaza el antiguo `requireStrongBox: Boolean` (ver ADR-013).
 */
enum class StrongBoxPolicy { Preferred, Required }
