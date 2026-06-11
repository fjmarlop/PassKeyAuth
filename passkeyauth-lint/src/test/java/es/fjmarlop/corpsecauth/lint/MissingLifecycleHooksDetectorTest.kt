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
 * Tests de [MissingLifecycleHooksDetector] (L3, ADR-012).
 *
 * Detecta clases que llaman a `PasskeyAuth.initialize()` pero no implementan
 * los hooks `onAppForeground` / `onAppBackground` que el README marca como
 * obligatorios para session timeout.
 */
@RunWith(JUnit4::class)
class MissingLifecycleHooksDetectorTest : LintDetectorTest() {

    override fun getDetector(): Detector = MissingLifecycleHooksDetector()
    override fun getIssues(): List<Issue> = listOf(MissingLifecycleHooksDetector.ISSUE)

    @Test
    fun `dado initialize sin hooks cuando lint entonces warning`() {
        lint().files(
            PASSKEY_AUTH_STUB,
            kotlin(
                """
                package com.example.app

                import es.fjmarlop.corpsecauth.PasskeyAuth

                class MainActivity {
                    suspend fun onCreate() {
                        PasskeyAuth.initialize()
                    }
                }
                """
            ).indented()
        )
            .issues(MissingLifecycleHooksDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expectWarningCount(1)
            .expectContains("session")
    }

    @Test
    fun `dado initialize con ambos hooks cuando lint entonces sin warnings`() {
        lint().files(
            PASSKEY_AUTH_STUB,
            kotlin(
                """
                package com.example.app

                import es.fjmarlop.corpsecauth.PasskeyAuth

                class MainActivity {
                    suspend fun onCreate() {
                        PasskeyAuth.initialize()
                    }
                    fun onStart() {
                        PasskeyAuth.onAppForeground()
                    }
                    fun onStop() {
                        PasskeyAuth.onAppBackground()
                    }
                }
                """
            ).indented()
        )
            .issues(MissingLifecycleHooksDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expectClean()
    }

    @Test
    fun `dado initialize con solo onAppForeground cuando lint entonces warning sobre background`() {
        lint().files(
            PASSKEY_AUTH_STUB,
            kotlin(
                """
                package com.example.app

                import es.fjmarlop.corpsecauth.PasskeyAuth

                class MainActivity {
                    suspend fun onCreate() {
                        PasskeyAuth.initialize()
                    }
                    fun onStart() {
                        PasskeyAuth.onAppForeground()
                    }
                }
                """
            ).indented()
        )
            .issues(MissingLifecycleHooksDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expectWarningCount(1)
            .expectContains("onAppBackground")
    }

    @Test
    fun `dado clase SIN initialize cuando lint entonces sin warnings`() {
        lint().files(
            PASSKEY_AUTH_STUB,
            kotlin(
                """
                package com.example.app

                import es.fjmarlop.corpsecauth.PasskeyAuth

                class HomeScreen {
                    fun show() {
                        // No llama a initialize; otro componente lo hizo.
                        PasskeyAuth.isAuthenticated()
                    }
                }
                """
            ).indented()
        )
            .issues(MissingLifecycleHooksDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expectClean()
    }

    companion object {
        private val PASSKEY_AUTH_STUB = kotlin(
            """
            package es.fjmarlop.corpsecauth

            object PasskeyAuth {
                suspend fun initialize(): Result<Unit> = Result.success(Unit)
                fun onAppForeground() {}
                fun onAppBackground() {}
                fun isAuthenticated(): Boolean = false
            }
            """
        ).indented()
    }
}
