package es.fjmarlop.corpsecauth.sample.ui.viewmodel

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.fjmarlop.corpsecauth.PasskeyAuth
import es.fjmarlop.corpsecauth.core.models.AuthResult
import es.fjmarlop.corpsecauth.core.models.EnrollmentState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {

    val authState: StateFlow<AuthResult> = PasskeyAuth.authState

    private val _enrollmentState = MutableStateFlow<EnrollmentState>(EnrollmentState.Idle)
    val enrollmentState: StateFlow<EnrollmentState> = _enrollmentState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    suspend fun isDeviceEnrolled(): Boolean {
        return PasskeyAuth.isDeviceEnrolled()
    }

    fun isAuthenticated(): Boolean {
        return PasskeyAuth.isAuthenticated()
    }

    fun enrollDevice(
        activity: FragmentActivity,
        email: String,
        temporaryPassword: String
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                PasskeyAuth.enrollDevice(
                    activity = activity,
                    email = email,
                    temporaryPassword = temporaryPassword
                ).collect { state ->
                    _enrollmentState.value = state

                    when (state) {
                        is EnrollmentState.Success -> {
                            _isLoading.value = false
                        }
                        is EnrollmentState.Error -> {
                            _isLoading.value = false
                            _errorMessage.value = state.exception.message ?: "Error en enrollment"
                        }
                        else -> {
                        }
                    }
                }
            } catch (e: Exception) {
                _isLoading.value = false
                _errorMessage.value = e.message ?: "Error desconocido"
            }
        }
    }

    fun authenticate(activity: FragmentActivity) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            PasskeyAuth.authenticate(activity)
                .onSuccess {
                    _isLoading.value = false
                }
                .onFailure { error ->
                    _isLoading.value = false
                    _errorMessage.value = error.message ?: "Error de autenticacion"
                }
        }
    }

    fun logout() {
        PasskeyAuth.logout()
    }
}