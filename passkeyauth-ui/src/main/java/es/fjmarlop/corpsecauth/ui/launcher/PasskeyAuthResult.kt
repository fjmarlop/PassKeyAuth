package es.fjmarlop.corpsecauth.ui.launcher

/** Resultado del launcher híbrido. */
sealed interface PasskeyAuthResult {
    data object Authenticated : PasskeyAuthResult
    data object Cancelled : PasskeyAuthResult
    data class Failed(val reason: String) : PasskeyAuthResult
}
