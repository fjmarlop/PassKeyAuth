package es.fjmarlop.corpsecauth.ui.launcher

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.fragment.app.FragmentActivity
import es.fjmarlop.corpsecauth.PasskeyAuthConfig
import es.fjmarlop.corpsecauth.StrongBoxPolicy
import es.fjmarlop.corpsecauth.ui.signin.PasskeySignInScreen
import es.fjmarlop.corpsecauth.ui.theme.PasskeyAuthTheme

/**
 * Activity interna del launcher híbrido. No es parte de la API pública del SDK.
 * El integrador usa PasskeyAuthContract, no esta Activity directamente (ADR-014).
 *
 * Nota: RecoveryHandler no puede pasar por Intent — si se necesita recovery,
 * usar PasskeySignInScreen directamente (capa de composables).
 */
internal class PasskeyAuthActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val config = PasskeyAuthConfig.Custom(
            allowHostFallback = intent.getBooleanExtra(EXTRA_ALLOW_HOST_FALLBACK, false),
            strongBox = StrongBoxPolicy.valueOf(
                intent.getStringExtra(EXTRA_STRONGBOX) ?: StrongBoxPolicy.Preferred.name
            ),
            sessionTimeoutMinutes = intent.getIntExtra(EXTRA_SESSION_TIMEOUT, 2),
        )

        setContent {
            MaterialTheme {
                PasskeyAuthTheme {
                    PasskeySignInScreen(
                        activity = this,
                        config = config,
                        onAuthenticated = {
                            setResult(Activity.RESULT_OK)
                            finish()
                        },
                        onHostFallback = {
                            setResult(Activity.RESULT_CANCELED)
                            finish()
                        },
                    )
                }
            }
        }
    }

    companion object {
        internal const val EXTRA_ALLOW_HOST_FALLBACK = "passkey_allow_host_fallback"
        internal const val EXTRA_STRONGBOX = "passkey_strongbox"
        internal const val EXTRA_SESSION_TIMEOUT = "passkey_session_timeout"
        internal const val EXTRA_FAILURE_REASON = "passkey_failure_reason"
    }
}
