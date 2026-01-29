package es.fjmarlop.corpsecauth.core.crypto

import android.util.Base64

/**
 * Modelo para datos cifrados con AES-GCM.
 *
 * Contiene el ciphertext (datos cifrados) y el IV (initialization vector)
 * necesario para descifrar.
 *
 * @property ciphertext Datos cifrados
 * @property iv Initialization Vector (12 bytes para GCM)
 */
data class EncryptedData(
    val ciphertext: ByteArray,
    val iv: ByteArray
) {
    /**
     * Convierte a Base64 para almacenamiento.
     *
     * Formato: IV + ciphertext concatenados y codificados en Base64
     */
    fun toBase64String(): String {
        val combined = iv + ciphertext
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EncryptedData

        if (!ciphertext.contentEquals(other.ciphertext)) return false
        if (!iv.contentEquals(other.iv)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = ciphertext.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        return result
    }

    companion object {
        private const val GCM_IV_LENGTH = 12

        /**
         * Parsea desde Base64.
         *
         * @param base64String String en Base64 (IV + ciphertext)
         * @return EncryptedData o null si formato invalido
         */
        fun fromBase64String(base64String: String): EncryptedData? {
            return try {
                val combined = Base64.decode(base64String, Base64.NO_WRAP)
                
                if (combined.size < GCM_IV_LENGTH) {
                    return null
                }

                val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
                val ciphertext = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

                EncryptedData(ciphertext, iv)
            } catch (e: Exception) {
                null
            }
        }
    }
}