package es.fjmarlop.corpsecauth

/**
 * Política frente a dispositivos rooteados o con hooks de instrumentación.
 *
 * En un device comprometido, `su` o un framework como Frida/Xposed pueden
 * extraer claves del KeyStore, parchear `setAllowedAuthenticators`, o falsear
 * la respuesta del BiometricPrompt. El SDK no debería operar en ese entorno.
 *
 * - [Block]: lanza [es.fjmarlop.corpsecauth.core.errors.IntegrityException] en
 *   `initialize()` si se detecta compromiso. Default de producción.
 * - [Warn]: loggea la detección pero continúa. Útil para entornos de QA con
 *   devices rooteados de prueba.
 * - [Allow]: omite la comprobación (no recomendado).
 *
 * Ver ADR-015.
 */
enum class RootPolicy { Block, Warn, Allow }
