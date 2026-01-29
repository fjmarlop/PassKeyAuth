package es.fjmarlop.corpsecauth.sample.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import es.fjmarlop.corpsecauth.core.models.AuthResult
import es.fjmarlop.corpsecauth.sample.ui.screens.enrollment.EnrollmentScreen
import es.fjmarlop.corpsecauth.sample.ui.screens.home.HomeScreen
import es.fjmarlop.corpsecauth.sample.ui.screens.login.LoginScreen
import es.fjmarlop.corpsecauth.sample.ui.screens.splash.SplashScreen
import es.fjmarlop.corpsecauth.sample.ui.viewmodel.AuthViewModel

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Enrollment : Screen("enrollment")
    object Login : Screen("login")
    object Home : Screen("home")
}

@Composable
fun AppNavigation(
    viewModel: AuthViewModel = viewModel()
) {
    val navController = rememberNavController()
    val authState by viewModel.authState.collectAsState()

    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route
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
                }
            )
        }

        composable(Screen.Enrollment.route) {
            EnrollmentScreen(
                viewModel = viewModel,
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Enrollment.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Login.route) {
            LoginScreen(
                viewModel = viewModel,
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToEnrollment = {
                    navController.navigate(Screen.Enrollment.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = viewModel,
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }
    }
}