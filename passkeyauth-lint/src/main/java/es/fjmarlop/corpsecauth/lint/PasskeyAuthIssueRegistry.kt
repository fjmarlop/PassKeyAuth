package es.fjmarlop.corpsecauth.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue

/**
 * Registry de Issues custom para enforcing del contrato del PasskeyAuth SDK.
 *
 * Ver ADR-012 para el catalogo completo de rules y justificacion.
 *
 * Rules registradas:
 * - [MissingFragmentActivityDetector.ISSUE] (ADR-007) — error si la Activity
 *   pasada a enrollDevice/authenticate no extiende FragmentActivity.
 * - [SkipBiometricNavigationDetector.ISSUE] (ADR-009) — warning del patron
 *   "if (isDeviceEnrolled) navigate" sin verificacion biometrica.
 * - [MissingLifecycleHooksDetector.ISSUE] — warning si una Activity llama
 *   initialize() pero no implementa los lifecycle hooks de session timeout.
 *
 * Registrado via `Lint-Registry-v2` en el JAR manifest (ver build.gradle.kts).
 */
class PasskeyAuthIssueRegistry : IssueRegistry() {
    override val issues: List<Issue> = listOf(
        MissingFragmentActivityDetector.ISSUE,
        SkipBiometricNavigationDetector.ISSUE,
        MissingLifecycleHooksDetector.ISSUE
    )

    override val api: Int = CURRENT_API

    // minApi 14 corresponde a AGP 9.0 / Lint 32.0. Si el consumer usa lint
    // mas antiguo, nuestras rules se ignoran (no crashea, solo no aplican).
    override val minApi: Int = 14

    override val vendor: Vendor = Vendor(
        vendorName = "PasskeyAuth SDK",
        identifier = "es.fjmarlop.passkeyauth",
        feedbackUrl = "https://github.com/fjmarlop/PassKeyAuth/issues",
        contact = "https://github.com/fjmarlop/PassKeyAuth"
    )
}
