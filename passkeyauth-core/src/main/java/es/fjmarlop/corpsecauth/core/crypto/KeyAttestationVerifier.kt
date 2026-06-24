package es.fjmarlop.corpsecauth.core.crypto

import android.annotation.SuppressLint
import android.os.Build
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import es.fjmarlop.corpsecauth.HardwareSecurityLevel
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory

/**
 * Verifica el nivel de seguridad hardware de una clave AndroidKeyStore (ADR-015, D2).
 *
 * Usa [KeyInfo] (API 23+) como primera capa: rápido, sin parseo ASN.1.
 * En API 31+, distingue StrongBox de TEE via [KeyProperties.SECURITY_LEVEL_STRONGBOX].
 * En API < 31, solo distingue "hardware-backed" de "software" via [KeyInfo.isInsideSecureHardware].
 *
 * El [keyInfoProvider] se inyecta para permitir tests JVM sin AndroidKeyStore real.
 */
internal class KeyAttestationVerifier(
    private val keyInfoProvider: (SecretKey) -> Result<KeyInfo> = defaultProvider()
) {

    fun checkSecurityLevel(key: SecretKey): HardwareSecurityLevel =
        keyInfoProvider(key)
            .map { resolveLevel(it) }
            .getOrElse { HardwareSecurityLevel.UNKNOWN }

    @SuppressLint("NewApi") // Guarded by Build.VERSION.SDK_INT >= S check
    @Suppress("DEPRECATION") // isInsideSecureHardware deprecated en API 31 — se usa en API < 31
    private fun resolveLevel(keyInfo: KeyInfo): HardwareSecurityLevel =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            when (keyInfo.securityLevel) {
                KeyProperties.SECURITY_LEVEL_STRONGBOX -> HardwareSecurityLevel.STRONGBOX
                KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> HardwareSecurityLevel.TRUSTED_ENVIRONMENT
                KeyProperties.SECURITY_LEVEL_SOFTWARE -> HardwareSecurityLevel.SOFTWARE
                else -> HardwareSecurityLevel.UNKNOWN
            }
        } else {
            if (keyInfo.isInsideSecureHardware) HardwareSecurityLevel.TRUSTED_ENVIRONMENT
            else HardwareSecurityLevel.SOFTWARE
        }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"

        private fun defaultProvider(): (SecretKey) -> Result<KeyInfo> = { key ->
            runCatching {
                val factory = SecretKeyFactory.getInstance(key.algorithm, ANDROID_KEYSTORE)
                factory.getKeySpec(key, KeyInfo::class.java) as KeyInfo
            }
        }
    }
}
