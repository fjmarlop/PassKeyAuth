package es.fjmarlop.corpsecauth.sample.ui.screens.enrollment

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import es.fjmarlop.corpsecauth.core.models.EnrollmentState
import es.fjmarlop.corpsecauth.sample.ui.viewmodel.AuthViewModel

@Composable
fun EnrollmentScreen(
    viewModel: AuthViewModel,
    onNavigateToHome: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as FragmentActivity
    /* TODO email y contraseña Hardcodeados para pruebas
    var email by remember { mutableStateOf("") }
    var tempPassword by remember { mutableStateOf("") }
    */
    var email = "test@fjmarlop.es"
    var tempPassword = "12345678"

    val enrollmentState by viewModel.enrollmentState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    LaunchedEffect(enrollmentState) {
        if (enrollmentState is EnrollmentState.Success) {
            onNavigateToHome()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "🔐",
            style = MaterialTheme.typography.displayLarge
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Registro de Dispositivo",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Configura tu autenticacion biometrica",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Estado del enrollment
        when (val state = enrollmentState) {
            is EnrollmentState.ValidatingCredentials -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Validando credenciales...")
            }
            is EnrollmentState.RequiresPasswordChange -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Invalidando credencial temporal...")
            }
            is EnrollmentState.GeneratingCryptoKey -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Generando claves de seguridad...")
            }
            is EnrollmentState.AwaitingBiometric -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Registra tu huella digital...")
            }
            is EnrollmentState.BindingDevice -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Vinculando dispositivo...")
            }
            else -> {
                // Formulario simplificado - SOLO 2 CAMPOS
                OutlinedTextField(
                    value = email,
                    onValueChange = { /*email = it*/ },
                    label = { Text("Email corporativo") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isLoading
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = tempPassword,
                    onValueChange = { /*tempPassword = it*/ },
                    label = { Text("Contrasena temporal") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = !isLoading
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        viewModel.enrollDevice(
                            activity = activity,
                            email = email,
                            temporaryPassword = tempPassword
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = email.isNotBlank() && 
                             tempPassword.isNotBlank() && 
                             !isLoading
                ) {
                    Text("Registrar Dispositivo")
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Mensaje informativo
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "ℹ️ Autenticacion sin contrasenias",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "La contrasena temporal sera invalidada automaticamente. " +
                                   "Solo usaras tu huella para acceder.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }

        // Error message
        errorMessage?.let { error ->
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}