package es.fjmarlop.corpsecauth.ui.launcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import es.fjmarlop.corpsecauth.PasskeyAuthConfig

/**
 * Launcher híbrido de una línea. Integración mínima:
 *
 * ```kotlin
 * val launcher = rememberLauncherForActivityResult(PasskeyAuthContract()) { result ->
 *     when (result) {
 *         PasskeyAuthResult.Authenticated -> navigateToHome()
 *         PasskeyAuthResult.Cancelled     -> { /* fallback del host */ }
 *         is PasskeyAuthResult.Failed     -> showError(result.reason)
 *     }
 * }
 * launcher.launch(PasskeyAuthConfig.Default)
 * ```
 *
 * Para control total (branding, nav graph propio), usar PasskeySignInScreen directamente.
 */
class PasskeyAuthContract : ActivityResultContract<PasskeyAuthConfig, PasskeyAuthResult>() {

    override fun createIntent(context: Context, input: PasskeyAuthConfig): Intent =
        Intent(context, PasskeyAuthActivity::class.java).apply {
            putExtra(PasskeyAuthActivity.EXTRA_ALLOW_HOST_FALLBACK, input.allowHostFallback)
            putExtra(PasskeyAuthActivity.EXTRA_STRONGBOX, input.strongBox.name)
            putExtra(PasskeyAuthActivity.EXTRA_SESSION_TIMEOUT, input.sessionTimeoutMinutes)
        }

    override fun parseResult(resultCode: Int, intent: Intent?): PasskeyAuthResult =
        when (resultCode) {
            Activity.RESULT_OK -> PasskeyAuthResult.Authenticated
            Activity.RESULT_CANCELED -> PasskeyAuthResult.Cancelled
            else -> PasskeyAuthResult.Failed(
                intent?.getStringExtra(PasskeyAuthActivity.EXTRA_FAILURE_REASON) ?: "Unknown"
            )
        }
}
