package es.fjmarlop.corpsecauth.sample.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import es.fjmarlop.corpsecauth.PasskeyAuthConfig
import es.fjmarlop.corpsecauth.sample.ui.screens.home.HomeScreen
import es.fjmarlop.corpsecauth.sample.ui.screens.splash.SplashScreen
import es.fjmarlop.corpsecauth.sample.ui.viewmodel.AuthViewModel
import es.fjmarlop.corpsecauth.ui.enroll.PasskeyEnrollScreen
import es.fjmarlop.corpsecauth.ui.signin.PasskeySignInScreen
import es.fjmarlop.corpsecauth.ui.theme.PasskeyAuthTheme

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Enrollment : Screen("enrollment")
    object Login : Screen("login")
    object Home : Screen("home")
}

@Composable
fun AppNavigation(
    viewModel: AuthViewModel = viewModel(),
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route,
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                onNavigateToEnrollment = {
                    navController.navigate(Screen.Enrollment.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
            )
        }

        composable(Screen.Enrollment.route) {
            val activity = LocalContext.current as FragmentActivity
            PasskeyAuthTheme {
                // Credenciales hardcodeadas para demo — en producción el host
                // recopila email/password en su propia pantalla antes de navegar aquí.
                PasskeyEnrollScreen(
                    activity = activity,
                    email = "test@fjmarlop.es",
                    temporaryPassword = "12345678",
                    onEnrolled = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Enrollment.route) { inclusive = true }
                        }
                    },
                )
            }
        }

        composable(Screen.Login.route) {
            val activity = LocalContext.current as FragmentActivity
            PasskeyAuthTheme {
                PasskeySignInScreen(
                    activity = activity,
                    config = PasskeyAuthConfig.Default,
                    onAuthenticated = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    },
                )
            }
        }

        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = viewModel,
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
            )
        }
    }
}
