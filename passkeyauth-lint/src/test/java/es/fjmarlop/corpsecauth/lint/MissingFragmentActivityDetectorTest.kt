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
 * Tests de [MissingFragmentActivityDetector] (L1, ADR-012).
 *
 * Cubre los escenarios documentados en el detector:
 * - ✅ FragmentActivity directo → sin warnings
 * - ✅ AppCompatActivity (extiende FragmentActivity transitivamente) → sin warnings
 * - ❌ ComponentActivity → ERROR
 * - ❌ Custom Activity que no extiende FragmentActivity → ERROR
 */
@RunWith(JUnit4::class)
class MissingFragmentActivityDetectorTest : LintDetectorTest() {

    override fun getDetector(): Detector = MissingFragmentActivityDetector()
    override fun getIssues(): List<Issue> = listOf(MissingFragmentActivityDetector.ISSUE)

    @Test
    fun `dado FragmentActivity en enrollDevice cuando lint entonces sin warnings`() {
        lint().files(
            *STUBS,
            kotlin(
                """
                package com.example.app

                import androidx.fragment.app.FragmentActivity
                import es.fjmarlop.corpsecauth.PasskeyAuth

                class MainActivity : FragmentActivity() {
                    fun onEnroll() {
                        PasskeyAuth.enrollDevice(this, "user@test.com", "pass")
                    }
                }
                """
            ).indented()
        )
            .issues(MissingFragmentActivityDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expectClean()
    }

    @Test
    fun `dado AppCompatActivity en enrollDevice cuando lint entonces sin warnings`() {
        lint().files(
            *STUBS,
            kotlin(
                """
                package com.example.app

                import androidx.appcompat.app.AppCompatActivity
                import es.fjmarlop.corpsecauth.PasskeyAuth

                class MainActivity : AppCompatActivity() {
                    fun onEnroll() {
                        PasskeyAuth.enrollDevice(this, "user@test.com", "pass")
                    }
                }
                """
            ).indented()
        )
            .issues(MissingFragmentActivityDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expectClean()
    }

    @Test
    fun `dado ComponentActivity en enrollDevice cuando lint entonces error`() {
        lint().files(
            *STUBS,
            kotlin(
                """
                package com.example.app

                import androidx.activity.ComponentActivity
                import es.fjmarlop.corpsecauth.PasskeyAuth

                class MainActivity : ComponentActivity() {
                    fun onEnroll() {
                        PasskeyAuth.enrollDevice(this, "user@test.com", "pass")
                    }
                }
                """
            ).indented()
        )
            .issues(MissingFragmentActivityDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expectErrorCount(1)
            .expectContains("requiere FragmentActivity")
            .expectContains("ClassCastException")
    }

    @Test
    fun `dado ComponentActivity en authenticate cuando lint entonces error`() {
        lint().files(
            *STUBS,
            kotlin(
                """
                package com.example.app

                import androidx.activity.ComponentActivity
                import es.fjmarlop.corpsecauth.PasskeyAuth

                class MainActivity : ComponentActivity() {
                    suspend fun onLogin() {
                        PasskeyAuth.authenticate(this)
                    }
                }
                """
            ).indented()
        )
            .issues(MissingFragmentActivityDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expectErrorCount(1)
    }

    companion object {
        /**
         * Stubs minimos de las clases para que el lint runner las resuelva.
         *
         * **Nota:** el stub de `PasskeyAuth.enrollDevice` usa `Any` en el parametro
         * `activity` (no `FragmentActivity`) deliberadamente. Asi el test case puede
         * pasar tanto FragmentActivity como ComponentActivity sin error de compilacion
         * — es el detector quien valida el tipo en runtime, no el compilador.
         */
        private val PASSKEY_AUTH_STUB = kotlin(
            """
            package es.fjmarlop.corpsecauth

            object PasskeyAuth {
                fun enrollDevice(activity: Any, email: String, password: String) {}
                suspend fun authenticate(activity: Any): Result<Unit> = Result.success(Unit)
            }
            """
        ).indented()

        private val FRAGMENT_ACTIVITY_STUB = kotlin(
            """
            package androidx.fragment.app

            open class FragmentActivity
            """
        ).indented()

        private val APPCOMPAT_ACTIVITY_STUB = kotlin(
            """
            package androidx.appcompat.app

            import androidx.fragment.app.FragmentActivity

            open class AppCompatActivity : FragmentActivity()
            """
        ).indented()

        private val COMPONENT_ACTIVITY_STUB = kotlin(
            """
            package androidx.activity

            open class ComponentActivity
            """
        ).indented()

        /** Todos los stubs juntos para incluir en cada test. */
        private val STUBS = arrayOf(
            PASSKEY_AUTH_STUB,
            FRAGMENT_ACTIVITY_STUB,
            APPCOMPAT_ACTIVITY_STUB,
            COMPONENT_ACTIVITY_STUB
        )
    }
}
