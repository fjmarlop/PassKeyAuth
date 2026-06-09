package es.fjmarlop.corpsecauth.core.support

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Regla JUnit que sustituye `Dispatchers.Main` por un [TestDispatcher] para tests.
 *
 * Sin esto, cualquier llamada a `Dispatchers.Main` dentro del codigo bajo test
 * lanzaria `IllegalStateException` en JVM (no hay looper Android).
 *
 * **Uso:**
 * ```kotlin
 * class EnrollmentManagerTest {
 *     @get:Rule
 *     val mainDispatcherRule = MainDispatcherRule()
 *
 *     @Test
 *     fun `enrollment happy path`() = runTest {
 *         // El codigo bajo test puede usar Dispatchers.Main sin crashear
 *     }
 * }
 * ```
 *
 * Patron estandar de la comunidad Kotlin coroutines (ver `kotlinx-coroutines-test`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class MainDispatcherRule(
    private val testDispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
