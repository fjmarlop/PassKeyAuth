package es.fjmarlop.corpsecauth.ui.signin

import es.fjmarlop.corpsecauth.PasskeyCapability

/**
 * Estado de la pantalla de entrada. UN composable dirigido por este `when` (ADR-014).
 * Distinto de AuthResult/PasskeyCapability del core: es la proyección visual.
 */
sealed interface PasskeyUiState {
    data object Idle : PasskeyUiState
    data object Loading : PasskeyUiState
    data class Error(val message: String) : PasskeyUiState
    data object Success : PasskeyUiState
    data object NotEnrolled : PasskeyUiState
    data object NoHardware : PasskeyUiState

    companion object {
        /** Deriva el estado inicial de la capacidad del dispositivo. */
        fun from(capability: PasskeyCapability): PasskeyUiState = when (capability) {
            PasskeyCapability.Ready -> Idle
            PasskeyCapability.NotEnrolled -> NotEnrolled
            PasskeyCapability.NoHardware,
            PasskeyCapability.SecurityUpdateRequired -> NoHardware
            PasskeyCapability.TemporarilyUnavailable -> Error("Biometría temporalmente no disponible")
        }
    }
}
