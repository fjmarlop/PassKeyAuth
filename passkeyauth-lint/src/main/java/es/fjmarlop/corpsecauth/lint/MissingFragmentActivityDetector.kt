package es.fjmarlop.corpsecauth.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

/**
 * **L1 (ERROR):** detecta llamadas a `PasskeyAuth.enrollDevice(...)` o
 * `PasskeyAuth.authenticate(...)` pasando una Activity que NO extiende
 * `androidx.fragment.app.FragmentActivity`.
 *
 * **Por que:** `BiometricPrompt` requiere `FragmentActivity` internamente.
 * Pasar un `ComponentActivity` causa `ClassCastException` en runtime al
 * mostrar el prompt — bug clasico documentado en ADR-007 y bugfixes.md.
 *
 * **Severidad ERROR:** runtime crash garantizado, no es un edge case.
 *
 * **Casos cubiertos:**
 * - PasskeyAuth.enrollDevice(activity, ...) — activity es ComponentActivity → ERROR
 * - PasskeyAuth.enrollDevice(activity, ...) — activity es FragmentActivity → OK
 * - PasskeyAuth.enrollDevice(activity, ...) — activity es AppCompatActivity → OK
 *   (AppCompatActivity extiende FragmentActivity)
 *
 * **Casos NO cubiertos (limitaciones del analisis estatico):**
 * - El tipo del argumento se infiere via Smart Cast a un tipo desconocido.
 */
class MissingFragmentActivityDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("enrollDevice", "authenticate")

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        // Solo nos interesan llamadas al singleton PasskeyAuth.
        if (!context.evaluator.isMemberInClass(method, PASSKEY_AUTH_FQN)) return

        // Buscamos el parametro llamado "activity" — esto funciona con named
        // arguments en cualquier orden. Lint testa REORDER_ARGUMENTS modes;
        // sin esto fallariamos al asumir posicion 0.
        val parameters = method.parameterList.parameters
        val activityParamIndex = parameters.indexOfFirst { it.name == "activity" }
        if (activityParamIndex < 0) return

        val activityArg = node.getArgumentForParameter(activityParamIndex) ?: return
        val argType = activityArg.getExpressionType() as? PsiClassType ?: return

        val resolved = argType.resolve() ?: return
        val qualifiedName = resolved.qualifiedName ?: return

        // OK si es FragmentActivity directo o cualquier subclase
        // (AppCompatActivity, FragmentActivity, custom subclasses).
        if (qualifiedName == FRAGMENT_ACTIVITY_FQN) return
        if (resolved.supersExtendFragmentActivity()) return

        // Mensaje de error explicito, accionable.
        context.report(
            issue = ISSUE,
            scope = node,
            location = context.getLocation(activityArg),
            message = "`PasskeyAuth` requiere `FragmentActivity` (ADR-007). " +
                    "Pasar `$qualifiedName` provoca `ClassCastException` en runtime al mostrar `BiometricPrompt`. " +
                    "Cambia tu Activity para extender `androidx.fragment.app.FragmentActivity` o `androidx.appcompat.app.AppCompatActivity`."
        )
    }

    /**
     * Recorre la jerarquia de superclases buscando FragmentActivity.
     * Necesario porque AppCompatActivity → FragmentActivity (transitivo).
     */
    private fun com.intellij.psi.PsiClass.supersExtendFragmentActivity(): Boolean {
        var current: com.intellij.psi.PsiClass? = superClass
        while (current != null) {
            if (current.qualifiedName == FRAGMENT_ACTIVITY_FQN) return true
            current = current.superClass
        }
        return false
    }

    companion object {
        private const val PASSKEY_AUTH_FQN = "es.fjmarlop.corpsecauth.PasskeyAuth"
        private const val FRAGMENT_ACTIVITY_FQN = "androidx.fragment.app.FragmentActivity"

        val ISSUE: Issue = Issue.create(
            id = "PasskeyAuthMissingFragmentActivity",
            briefDescription = "Activity debe extender FragmentActivity",
            explanation = """
                `PasskeyAuth.enrollDevice()` y `PasskeyAuth.authenticate()` requieren una \
                `FragmentActivity` (o subclase como `AppCompatActivity`) porque internamente \
                usan `androidx.biometric.BiometricPrompt`, que necesita transacciones de \
                Fragment para mostrar el dialogo biometrico.

                Pasar un `ComponentActivity` (default de Jetpack Compose) o cualquier otra \
                Activity causa `ClassCastException` en runtime al mostrar el prompt.

                **Fix:** cambia la herencia de tu Activity:
                ```kotlin
                // Antes
                class MainActivity : ComponentActivity()

                // Despues
                class MainActivity : FragmentActivity()
                ```

                Ver ADR-007 para mas contexto.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 10,
            severity = Severity.ERROR,
            implementation = Implementation(
                MissingFragmentActivityDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
