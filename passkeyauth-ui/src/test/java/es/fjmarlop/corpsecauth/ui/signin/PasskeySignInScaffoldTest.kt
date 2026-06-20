package es.fjmarlop.corpsecauth.ui.signin

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import es.fjmarlop.corpsecauth.ui.theme.PasskeyAuthTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class PasskeySignInScaffoldTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `estado Idle muestra el CTA primario de acceso`() {
        composeRule.setContent {
            MaterialTheme {
                PasskeyAuthTheme {
                    PasskeySignInScaffold(
                        state = PasskeyUiState.Idle,
                        allowHostFallback = false,
                        onPrimaryAction = {},
                        onHostFallback = {},
                    )
                }
            }
        }
        composeRule.onNodeWithText("Acceder").assertIsDisplayed()
    }

    @Test
    fun `estado NoHardware sin fallback NO muestra boton de otro metodo`() {
        composeRule.setContent {
            MaterialTheme {
                PasskeyAuthTheme {
                    PasskeySignInScaffold(
                        state = PasskeyUiState.NoHardware,
                        allowHostFallback = false,
                        onPrimaryAction = {},
                        onHostFallback = {},
                    )
                }
            }
        }
        composeRule.onNodeWithText("Usar otro método").assertDoesNotExist()
    }
}
