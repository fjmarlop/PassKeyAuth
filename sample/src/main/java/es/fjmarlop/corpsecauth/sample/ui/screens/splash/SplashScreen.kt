package es.fjmarlop.corpsecauth.sample.ui.screens.splash

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import es.fjmarlop.corpsecauth.sample.ui.viewmodel.AuthViewModel
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onNavigateToEnrollment: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToHome: () -> Unit,
    viewModel: AuthViewModel = viewModel()
) {
    var hasNavigated by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(1000)

        if (!hasNavigated) {
            hasNavigated = true

            val isEnrolled = viewModel.isDeviceEnrolled()

            when {
                !isEnrolled -> {
                    println("🔐 SplashScreen: Dispositivo no enrollado")
                    onNavigateToEnrollment()
                }
                else -> {
                    println("🔐 SplashScreen: Dispositivo enrollado - requiere autenticacion")
                    onNavigateToLogin()
                }
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "🔐",
                fontSize = 72.sp
            )
            Text(
                text = "PasskeyAuth",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = "Autenticacion sin contrasenias",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))
            CircularProgressIndicator()
        }
    }
}