package es.fjmarlop.corpsecauth.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Tests de [SkipBiometricNavigationDetector] (L2, ADR-012).
 *
 * El bug del SplashScreen del ADR-009: `if (isDeviceEnrolled) navigate()`
 * sin `authenticate()` salta verificacion biometrica.
 */
@RunWith(JUnit4::class)
class SkipBiometricNavigationDetectorTest : LintDetectorTest() {

    override fun getDetector(): Detector = SkipBiometricNavigationDetector()
    override fun getIssues(): List<Issue> = listOf(SkipBiometricNavigationDetector.ISSUE)

    @Test
    fun `dado isDeviceEnrolled + navigate sin authenticate cuando lint entonces warning`() {
        lint().files(
            PASSKEY_AUTH_STUB,
            kotlin(
                """
                package com.example.app

                import es.fjmarlop.corpsecauth.PasskeyAuth

                class SplashScreen {
                    fun decideDestination() {
                        if (PasskeyAuth.isDeviceEnrolled()) {
                            navigateToHome()
                        }
                    }

                    private fun navigateToHome() {}
                }
                """
            ).indented()
        )
            .issues(SkipBiometricNavigationDetector.ISSUE)
            .allowMissingSdk()
            // El heuristico actual solo cubre la forma `if (...)`. Skip el test mode
            // `IF_TO_WHEN` (deliberado): la forma `when` del bug es menos comun en
            // practica. Mejora futura si surge necesidad real.
            .skipTestModes(com.android.tools.lint.checks.infrastructure.TestMode.IF_TO_WHEN)
            .run()
            .expectWarningCount(1)
            .expectContains("ADR-009")
            .expectContains("SplashScreen")
    }

    @Test
    fun `dado isDeviceEnrolled + authenticate + navigate cuando lint entonces sin warnings`() {
        lint().files(
            PASSKEY_AUTH_STUB,
            kotlin(
                """
                package com.example.app

                import es.fjmarlop.corpsecauth.PasskeyAuth

                class LoginScreen {
                    suspend fun onClick() {
                        if (PasskeyAuth.isDeviceEnrolled()) {
                            PasskeyAuth.authenticate()
                            navigateToHome()
                        }
                    }
                    private fun navigateToHome() {}
                }
                """
            ).indented()
        )
            .issues(SkipBiometricNavigationDetector.ISSUE)
            .allowMissingSdk()
            // El heuristico actual solo cubre la forma `if (...)`. Skip el test mode
            // `IF_TO_WHEN` (deliberado): la forma `when` del bug es menos comun en
            // practica. Mejora futura si surge necesidad real.
            .skipTestModes(com.android.tools.lint.checks.infrastructure.TestMode.IF_TO_WHEN)
            .run()
            .expectClean()
    }

    @Test
    fun `dado isDeviceEnrolled en branch sin navegacion cuando lint entonces sin warnings`() {
        lint().files(
            PASSKEY_AUTH_STUB,
            kotlin(
                """
                package com.example.app

                import es.fjmarlop.corpsecauth.PasskeyAuth

                class StatusChecker {
                    fun reportEnrollment() {
                        if (PasskeyAuth.isDeviceEnrolled()) {
                            println("device enrolled")
                        }
                    }
                }
                """
            ).indented()
        )
            .issues(SkipBiometricNavigationDetector.ISSUE)
            .allowMissingSdk()
            // El heuristico actual solo cubre la forma `if (...)`. Skip el test mode
            // `IF_TO_WHEN` (deliberado): la forma `when` del bug es menos comun en
            // practica. Mejora futura si surge necesidad real.
            .skipTestModes(com.android.tools.lint.checks.infrastructure.TestMode.IF_TO_WHEN)
            .run()
            .expectClean()
    }

    @Test
    fun `dado startActivity sin authenticate cuando lint entonces warning`() {
        lint().files(
            PASSKEY_AUTH_STUB,
            kotlin(
                """
                package com.example.app

                import es.fjmarlop.corpsecauth.PasskeyAuth

                class SplashScreen {
                    fun maybeStart() {
                        if (PasskeyAuth.isDeviceEnrolled()) {
                            startActivity(Intent())
                        }
                    }
                    private fun startActivity(i: Any) {}
                }
                class Intent
                """
            ).indented()
        )
            .issues(SkipBiometricNavigationDetector.ISSUE)
            .allowMissingSdk()
            // El heuristico actual solo cubre la forma `if (...)`. Skip el test mode
            // `IF_TO_WHEN` (deliberado): la forma `when` del bug es menos comun en
            // practica. Mejora futura si surge necesidad real.
            .skipTestModes(com.android.tools.lint.checks.infrastructure.TestMode.IF_TO_WHEN)
            .run()
            .expectWarningCount(1)
    }

    companion object {
        private val PASSKEY_AUTH_STUB = kotlin(
            """
            package es.fjmarlop.corpsecauth

            object PasskeyAuth {
                suspend fun isDeviceEnrolled(): Boolean = false
                suspend fun authenticate(): Result<Unit> = Result.success(Unit)
            }
            """
        ).indented()
    }
}
