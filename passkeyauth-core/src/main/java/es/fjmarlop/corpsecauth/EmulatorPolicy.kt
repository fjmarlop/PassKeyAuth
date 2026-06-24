package es.fjmarlop.corpsecauth

/**
 * Política frente a emuladores.
 *
 * Los emuladores no tienen hardware biométrico real — la ceremonia puede ser
 * simulada o bypass-eada. En producción conviene bloquear; en desarrollo no.
 *
 * - [Block]: lanza [es.fjmarlop.corpsecauth.core.errors.IntegrityException] si
 *   se detecta emulador.
 * - [Warn]: loggea pero continúa.
 * - [Allow]: omite la comprobación.
 *
 * El default del SDK (`PasskeyAuthConfig.Default`) resuelve la política según el
 * build: `Block` en release, `Warn` en debug. Ver ADR-015.
 */
enum class EmulatorPolicy { Block, Warn, Allow }
