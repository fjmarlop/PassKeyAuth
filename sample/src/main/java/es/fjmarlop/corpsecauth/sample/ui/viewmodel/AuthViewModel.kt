package es.fjmarlop.corpsecauth.sample.ui.viewmodel

import androidx.lifecycle.ViewModel
import es.fjmarlop.corpsecauth.PasskeyAuth

class AuthViewModel : ViewModel() {

    suspend fun isDeviceEnrolled(): Boolean = PasskeyAuth.isDeviceEnrolled()

    fun logout() = PasskeyAuth.logout()
}
