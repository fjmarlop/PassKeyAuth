package es.fjmarlop.corpsecauth.core.fakes

import es.fjmarlop.corpsecauth.core.firebase.PasswordManagementBackend

/**
 * Fake JVM de [PasswordManagementBackend].
 *
 * Cuando se descomente el paso 2 del enrollment (ver ADR-006), este fake
 * permitira testear los escenarios de exito y fallo de la invalidacion
 * de password temporal sin tocar Firebase.
 */
internal class FakePasswordManagementBackend : PasswordManagementBackend {

    /** Resultado que devolvera [invalidateTemporaryPassword]. */
    var invalidateResult: Result<Unit> = Result.success(Unit)

    var invalidateCallCount = 0
        private set

    override suspend fun invalidateTemporaryPassword(): Result<Unit> {
        invalidateCallCount++
        return invalidateResult
    }
}
