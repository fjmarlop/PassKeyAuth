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
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.visitor.AbstractUastVisitor

/**
 * **L3 (WARNING):** detecta clases que llaman `PasskeyAuth.initialize()` pero
 * NO implementan los lifecycle hooks `onAppForeground()` y `onAppBackground()`
 * que el README documenta como obligatorios para que session timeout funcione.
 *
 * **Por que WARNING:** el SDK NO crashea si faltan los hooks. Simplemente el
 * session timeout queda silenciosamente roto (la sesion no se invalida en
 * background). Es un bug de seguridad facil de pasar por alto.
 *
 * **Heuristica:** la clase contiene una llamada a `PasskeyAuth.initialize` Y
 * NO contiene ninguna llamada a `PasskeyAuth.onAppForeground` o
 * `PasskeyAuth.onAppBackground` en ningun metodo.
 *
 * **Limitaciones:**
 * - Solo busca textualmente en la clase (no recorre clases padre).
 * - Falsos negativos si los hooks estan en un `Application` o `ProcessLifecycleOwner`.
 *   En ese caso, suprimir con `@Suppress("PasskeyAuthMissingLifecycleHooks")`.
 */
class MissingLifecycleHooksDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() = listOf(UClass::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler = object : UElementHandler() {
        override fun visitClass(node: UClass) {
            // AST traversal en vez de text matching: robusto a whitespace, comentarios
            // y otros cambios sintacticos que el text matching no toleraria.
            // Visitamos recursivamente todos los UCallExpression dentro de la clase
            // y verificamos su `resolve()` contra PasskeyAuth.
            var callsInitialize = false
            var callsForeground = false
            var callsBackground = false

            node.accept(object : AbstractUastVisitor() {
                override fun visitCallExpression(callNode: UCallExpression): Boolean {
                    val method = callNode.resolve()
                    val containingClassFqn = method?.containingClass?.qualifiedName
                    if (containingClassFqn == PASSKEY_AUTH_FQN) {
                        when (method.name) {
                            "initialize" -> callsInitialize = true
                            "onAppForeground" -> callsForeground = true
                            "onAppBackground" -> callsBackground = true
                        }
                    }
                    return false // sigue visitando hijos
                }
            })

            if (!callsInitialize) return
            if (callsForeground && callsBackground) return

            val missing = buildList {
                if (!callsForeground) add("`PasskeyAuth.onAppForeground()` en `onStart()`")
                if (!callsBackground) add("`PasskeyAuth.onAppBackground()` en `onStop()`")
            }.joinToString(" y ")

            context.report(
                issue = ISSUE,
                scope = node as UElement,
                location = context.getNameLocation(node),
                message = "La clase llama a `PasskeyAuth.initialize()` pero falta: $missing. " +
                        "Sin estos hooks, `sessionTimeoutMinutes` queda silenciosamente roto " +
                        "(la sesion no se invalida al background). Ver README seccion " +
                        "\"Implementar Lifecycle Hooks (CRITICO)\"."
            )
        }
    }

    companion object {
        private const val PASSKEY_AUTH_FQN = "es.fjmarlop.corpsecauth.PasskeyAuth"

        val ISSUE: Issue = Issue.create(
            id = "PasskeyAuthMissingLifecycleHooks",
            briefDescription = "Falta integracion con lifecycle para session timeout",
            explanation = """
                Tu clase llama a `PasskeyAuth.initialize()` pero no llama a los hooks de \
                lifecycle obligatorios `PasskeyAuth.onAppForeground()` y `PasskeyAuth.onAppBackground()`.

                Sin estos hooks, la session NO se invalida cuando la app va a background, \
                rompiendo silenciosamente el `sessionTimeoutMinutes` configurado.

                **Fix:** override `onStart()` y `onStop()` en tu Activity:

                ```kotlin
                class MainActivity : FragmentActivity() {
                    override fun onStart() {
                        super.onStart()
                        if (!isChangingConfigurations) {
                            PasskeyAuth.onAppForeground()
                        }
                    }

                    override fun onStop() {
                        super.onStop()
                        if (!isChangingConfigurations) {
                            PasskeyAuth.onAppBackground()
                        }
                    }
                }
                ```

                Si gestionas los hooks en un `Application` con `ProcessLifecycleOwner`, \
                puedes suprimir este warning con `@Suppress("PasskeyAuthMissingLifecycleHooks")`.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 7,
            severity = Severity.WARNING,
            implementation = Implementation(
                MissingLifecycleHooksDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
