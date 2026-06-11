package es.fjmarlop.corpsecauth.lint

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.getParentOfType

/**
 * **L2 (WARNING):** detecta el antipatron del **SplashScreen** documentado en ADR-009:
 *
 * ```kotlin
 * // INSEGURO — bug original del SplashScreen del sample app
 * if (PasskeyAuth.isDeviceEnrolled()) {
 *     navigateToHome()  // ❌ sin verificacion biometrica
 * }
 * ```
 *
 * **Heuristica:** cualquier `if (isDeviceEnrolled())` cuyo cuerpo navega
 * (referencia a `navigate*` / `startActivity` / `intent`) pero NO contiene
 * llamada a `PasskeyAuth.authenticate()`.
 *
 * **Por que WARNING y no ERROR:** existen casos legitimos donde el desarrollador
 * sabe lo que hace (apps de baja seguridad, redes de confianza). La decision es
 * informada — solo avisamos. Ver ADR-009 ("client-side security responsibility").
 *
 * **Limitaciones del analisis estatico:**
 * - Solo detecta el patron textual dentro del then-branch (no analiza flujo de datos).
 * - Falsos positivos posibles: navegacion legitima en branches que SI tienen
 *   verificacion en una funcion auxiliar.
 * - Falsos negativos: navegacion via reflection, callbacks, etc.
 */
class SkipBiometricNavigationDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("isDeviceEnrolled")

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        // Solo nos interesa isDeviceEnrolled del singleton PasskeyAuth.
        if (!context.evaluator.isMemberInClass(method, PASSKEY_AUTH_FQN)) return

        // Buscamos el if expression que CONTIENE este call como (parte de la) condicion.
        val ifExpr = node.getParentOfType(UIfExpression::class.java) ?: return

        // La condicion del if debe contener nuestro call. Si esta en el body, ignorar
        // (el desarrollador esta usando isDeviceEnrolled como parte del cuerpo).
        val conditionText = ifExpr.condition.sourcePsi?.text ?: return
        if (!conditionText.contains("isDeviceEnrolled")) return

        // Cuerpo del then-branch.
        val thenExpr = ifExpr.thenExpression ?: return
        val thenText = thenExpr.sourcePsi?.text ?: return

        // Heuristica: hay navegacion en el then?
        val hasNavigation = NAVIGATION_KEYWORDS.any { keyword -> thenText.contains(keyword) }
        if (!hasNavigation) return

        // Hay llamada a authenticate en el then?
        if (thenText.contains("authenticate")) return

        // Reporte.
        context.report(
            issue = ISSUE,
            scope = ifExpr,
            location = context.getLocation(ifExpr.condition),
            message = "Navegar tras `isDeviceEnrolled()` sin `authenticate()` salta la verificacion biometrica " +
                    "(ADR-009). Este es el patron del bug del SplashScreen — cualquier usuario con el dispositivo " +
                    "podria acceder a la pantalla destino. Si la navegacion lleva a contenido sensible, llama a " +
                    "`PasskeyAuth.authenticate(activity)` primero."
        )
    }

    companion object {
        private const val PASSKEY_AUTH_FQN = "es.fjmarlop.corpsecauth.PasskeyAuth"

        /**
         * Keywords que sugieren navegacion. Heuristica deliberadamente amplia para
         * reducir falsos negativos a costa de algun falso positivo.
         */
        private val NAVIGATION_KEYWORDS = listOf(
            "navigate",
            "navController",
            "startActivity",
            "Intent",
            "composable"
        )

        val ISSUE: Issue = Issue.create(
            id = "PasskeyAuthSkipBiometricNavigation",
            briefDescription = "Navegar tras isDeviceEnrolled() sin authenticate() es inseguro",
            explanation = """
                Detectado el patron:

                ```kotlin
                if (PasskeyAuth.isDeviceEnrolled()) {
                    navigateToHome()  // ❌ INSEGURO
                }
                ```

                `isDeviceEnrolled()` SOLO verifica si existen claves biometricas guardadas. \
                NO verifica autenticacion reciente. Cualquier persona con el dispositivo \
                desbloqueado puede llegar a la pantalla destino sin haber pasado el prompt \
                biometrico.

                **Este es el bug del SplashScreen** documentado en ADR-009: tras autenticar \
                y cerrar la app, el `authState` permanece `Authenticated` en memoria. Al \
                reabrir, este patron navega directo a Home sin pedir biometria.

                **Fix:** siempre navegar a la pantalla de login para forzar `authenticate()`:

                ```kotlin
                // Seguro
                when {
                    !PasskeyAuth.isDeviceEnrolled() -> navigateToEnrollment()
                    else -> navigateToLogin()  // pide biometria
                }
                ```

                Si la navegacion a contenido sensible es intencional (apps de baja seguridad), \
                puedes suprimir este warning con `@Suppress("PasskeyAuthSkipBiometricNavigation")`.

                Ver ADR-009 para mas contexto sobre responsabilidad de seguridad cliente-side.
            """.trimIndent(),
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            implementation = Implementation(
                SkipBiometricNavigationDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
