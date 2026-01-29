package es.fjmarlop.corpsecauth.sample

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import es.fjmarlop.corpsecauth.PasskeyAuth
import es.fjmarlop.corpsecauth.PasskeyAuthConfig
import kotlinx.coroutines.launch

/**
 * Application class de la sample app.
 *
 * Inicializa PasskeyAuth SDK en onCreate.
 */
class SampleApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Inicializar PasskeyAuth SDK
        ProcessLifecycleOwner.get().lifecycleScope.launch {
            PasskeyAuth.initialize(
                context = this@SampleApplication,
                config = PasskeyAuthConfig.Custom(false,1) // Logs habilitados para desarrollo
            ).onSuccess {
                println("✅ SampleApp: PasskeyAuth inicializado")
            }.onFailure { error ->
                println("❌ SampleApp: Error inicializando PasskeyAuth: ${error.message}")
                error.printStackTrace()
            }
        }
    }
}