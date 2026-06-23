package es.fjmarlop.corpsecauth.sample.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import es.fjmarlop.corpsecauth.PasskeyAuthConfig
import es.fjmarlop.corpsecauth.sample.ui.screens.credentials.CredentialsScreen
import es.fjmarlop.corpsecauth.sample.ui.screens.home.HomeScreen
import es.fjmarlop.corpsecauth.sample.ui.screens.splash.SplashScreen
import es.fjmarlop.corpsecauth.sample.ui.viewmodel.AuthViewModel
import es.fjmarlop.corpsecauth.ui.enroll.PasskeyEnrollScreen
import es.fjmarlop.corpsecauth.ui.signin.PasskeySignInScreen
import es.fjmarlop.corpsecauth.ui.theme.PasskeyAuthTheme

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Credentials : Screen("credentials")
    object Enrollment : Screen("enrollment/{email}/{password}") {
        fun createRoute(email: String, password: String) =
            "enrollment/${Uri.encode(email)}/${Uri.encode(password)}"
    }
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
                    navController.navigate(Screen.Credentials.route) {
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

        composable(Screen.Credentials.route) {
            CredentialsScreen(
                onContinue = { email, password ->
                    navController.navigate(Screen.Enrollment.createRoute(email, password))
                },
            )
        }

        composable(
            route = Screen.Enrollment.route,
            arguments = listOf(
                navArgument("email") { type = NavType.StringType },
                navArgument("password") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val email = backStackEntry.arguments?.getString("email") ?: ""
            val password = backStackEntry.arguments?.getString("password") ?: ""
            val activity = LocalContext.current as FragmentActivity
            PasskeyAuthTheme {
                PasskeyEnrollScreen(
                    activity = activity,
                    email = email,
                    temporaryPassword = password,
                    onEnrolled = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Credentials.route) { inclusive = true }
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
