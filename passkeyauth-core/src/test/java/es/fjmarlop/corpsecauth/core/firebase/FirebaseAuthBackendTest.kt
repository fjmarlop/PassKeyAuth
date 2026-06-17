package es.fjmarlop.corpsecauth.core.firebase

import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.common.truth.Truth.assertThat
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GetTokenResult
import es.fjmarlop.corpsecauth.core.errors.FirebaseException
import es.fjmarlop.corpsecauth.core.models.Credentials
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests unitarios de [FirebaseAuthBackend] con Robolectric.
 *
 * **Estrategia:** MockK intercepta los callbacks de Firebase Tasks
 * (addOnSuccessListener / addOnFailureListener) y los dispara sincrónicamente.
 * suspendCoroutine detecta la reanudación síncrona y devuelve el valor sin
 * suspender la coroutine — patrón estándar para testing de adapters Firebase.
 *
 * **Por qué Robolectric y no JVM puro:** Las clases del Firebase SDK
 * (FirebaseAuth, FirebaseUser) tienen dependencias en clases Android en su
 * inicialización estática. Robolectric proporciona el runtime mínimo necesario.
 *
 * **Lo que NO testeamos aquí:**
 * - Comunicación real con Firebase servers (eso es smoke test S7/S8).
 * - Refresh de tokens (delegado al SDK de Firebase internamente).
 *
 * Ver ADR-010 (backend interfaces), ADR-011 (testing stack).
 */
@RunWith(RobolectricTestRunner::class)
internal class FirebaseAuthBackendTest {

    private val mockAuth = mockk<FirebaseAuth>()
    private lateinit var backend: FirebaseAuthBackend

    @Before
    fun setup() {
        backend = FirebaseAuthBackend(mockAuth)
    }

    // ===========================================================
    // authenticate()
    // ===========================================================

    @Test
    fun `authenticate success maps FirebaseUser a AuthSession correctamente`() = runTest {
        val mockUser = fakeFirebaseUser(uid = "uid-001", email = "admin@corp.es", displayName = "Admin")
        setupSuccessfulLogin(user = mockUser, idToken = "firebase-jwt-abc")

        val result = backend.authenticate(Credentials.EmailPassword("admin@corp.es", "tempPass"))

        assertThat(result.isSuccess).isTrue()
        val session = result.getOrThrow()
        assertThat(session.user.uid).isEqualTo("uid-001")
        assertThat(session.user.email).isEqualTo("admin@corp.es")
        assertThat(session.user.displayName).isEqualTo("Admin")
        assertThat(session.idToken).isEqualTo("firebase-jwt-abc")
        // Firebase gestiona refresh internamente — no exponemos tokens de refresh
        assertThat(session.refreshToken).isNull()
        assertThat(session.expiresAt).isNull()
    }

    @Test
    fun `authenticate usuario nulo tras login devuelve AuthenticationFailed`() = runTest {
        val loginTask = mockk<Task<AuthResult>>()
        val mockAuthResult = mockk<AuthResult>()

        every { mockAuth.signInWithEmailAndPassword(any(), any()) } returns loginTask
        every { loginTask.addOnSuccessListener(any<OnSuccessListener<AuthResult>>()) } answers {
            firstArg<OnSuccessListener<AuthResult>>().onSuccess(mockAuthResult)
            loginTask
        }
        every { loginTask.addOnFailureListener(any()) } returns loginTask
        every { mockAuthResult.user } returns null

        val result = backend.authenticate(Credentials.EmailPassword("admin@corp.es", "pass"))

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(FirebaseException.AuthenticationFailed::class.java)
    }

    @Test
    fun `authenticate token nulo tras getIdToken devuelve AuthenticationFailed`() = runTest {
        val mockUser = fakeFirebaseUser("uid-001", "admin@corp.es")
        val mockAuthResult = mockk<AuthResult>()
        val loginTask = mockk<Task<AuthResult>>()
        val tokenTask = mockk<Task<GetTokenResult>>()
        val mockTokenResult = mockk<GetTokenResult>()

        every { mockAuth.signInWithEmailAndPassword(any(), any()) } returns loginTask
        every { loginTask.addOnSuccessListener(any<OnSuccessListener<AuthResult>>()) } answers {
            firstArg<OnSuccessListener<AuthResult>>().onSuccess(mockAuthResult)
            loginTask
        }
        every { loginTask.addOnFailureListener(any()) } returns loginTask
        every { mockAuthResult.user } returns mockUser
        every { mockUser.getIdToken(false) } returns tokenTask
        every { tokenTask.addOnSuccessListener(any<OnSuccessListener<GetTokenResult>>()) } answers {
            firstArg<OnSuccessListener<GetTokenResult>>().onSuccess(mockTokenResult)
            tokenTask
        }
        every { tokenTask.addOnFailureListener(any()) } returns tokenTask
        every { mockTokenResult.token } returns null

        val result = backend.authenticate(Credentials.EmailPassword("admin@corp.es", "pass"))

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(FirebaseException.AuthenticationFailed::class.java)
    }

    @Test
    fun `authenticate fallo en getIdToken devuelve AuthenticationFailed`() = runTest {
        val mockUser = fakeFirebaseUser("uid-001", "admin@corp.es")
        val mockAuthResult = mockk<AuthResult>()
        val loginTask = mockk<Task<AuthResult>>()
        val tokenTask = mockk<Task<GetTokenResult>>()

        every { mockAuth.signInWithEmailAndPassword(any(), any()) } returns loginTask
        every { loginTask.addOnSuccessListener(any<OnSuccessListener<AuthResult>>()) } answers {
            firstArg<OnSuccessListener<AuthResult>>().onSuccess(mockAuthResult)
            loginTask
        }
        every { loginTask.addOnFailureListener(any()) } returns loginTask
        every { mockAuthResult.user } returns mockUser
        every { mockUser.getIdToken(false) } returns tokenTask
        every { tokenTask.addOnSuccessListener(any<OnSuccessListener<GetTokenResult>>()) } returns tokenTask
        every { tokenTask.addOnFailureListener(any()) } answers {
            firstArg<OnFailureListener>().onFailure(RuntimeException("Token fetch failed"))
            tokenTask
        }

        val result = backend.authenticate(Credentials.EmailPassword("admin@corp.es", "pass"))

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(FirebaseException.AuthenticationFailed::class.java)
    }

    @Test
    fun `authenticate FirebaseAuthInvalidUserException devuelve UserNotFound`() = runTest {
        val loginTask = mockk<Task<AuthResult>>()
        val exception = FirebaseAuthInvalidUserException("ERROR_USER_NOT_FOUND", "User not found")

        every { mockAuth.signInWithEmailAndPassword(any(), any()) } returns loginTask
        every { loginTask.addOnSuccessListener(any<OnSuccessListener<AuthResult>>()) } returns loginTask
        every { loginTask.addOnFailureListener(any()) } answers {
            firstArg<OnFailureListener>().onFailure(exception)
            loginTask
        }

        val result = backend.authenticate(Credentials.EmailPassword("unknown@corp.es", "pass"))

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(FirebaseException.UserNotFound::class.java)
    }

    @Test
    fun `authenticate FirebaseAuthInvalidCredentialsException devuelve InvalidCredentials`() = runTest {
        val loginTask = mockk<Task<AuthResult>>()
        val exception = FirebaseAuthInvalidCredentialsException("ERROR_WRONG_PASSWORD", "Wrong password")

        every { mockAuth.signInWithEmailAndPassword(any(), any()) } returns loginTask
        every { loginTask.addOnSuccessListener(any<OnSuccessListener<AuthResult>>()) } returns loginTask
        every { loginTask.addOnFailureListener(any()) } answers {
            firstArg<OnFailureListener>().onFailure(exception)
            loginTask
        }

        val result = backend.authenticate(Credentials.EmailPassword("admin@corp.es", "wrongPass"))

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(FirebaseException.InvalidCredentials::class.java)
    }

    @Test
    fun `authenticate excepcion genérica devuelve AuthenticationFailed`() = runTest {
        val loginTask = mockk<Task<AuthResult>>()

        every { mockAuth.signInWithEmailAndPassword(any(), any()) } returns loginTask
        every { loginTask.addOnSuccessListener(any<OnSuccessListener<AuthResult>>()) } returns loginTask
        every { loginTask.addOnFailureListener(any()) } answers {
            firstArg<OnFailureListener>().onFailure(RuntimeException("Network timeout"))
            loginTask
        }

        val result = backend.authenticate(Credentials.EmailPassword("admin@corp.es", "pass"))

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(FirebaseException.AuthenticationFailed::class.java)
    }

    // ===========================================================
    // getCurrentUser()
    // ===========================================================

    @Test
    fun `getCurrentUser sin sesion activa devuelve null`() {
        every { mockAuth.currentUser } returns null

        assertThat(backend.getCurrentUser()).isNull()
    }

    @Test
    fun `getCurrentUser con sesion activa devuelve AuthUser mapeado`() {
        val mockUser = fakeFirebaseUser(
            uid = "uid-789",
            email = "user@corp.es",
            displayName = "Corp User",
            isEmailVerified = true
        )
        every { mockAuth.currentUser } returns mockUser

        val user = backend.getCurrentUser()

        assertThat(user).isNotNull()
        assertThat(user!!.uid).isEqualTo("uid-789")
        assertThat(user.email).isEqualTo("user@corp.es")
        assertThat(user.displayName).isEqualTo("Corp User")
        assertThat(user.isEmailVerified).isTrue()
    }

    // ===========================================================
    // signOut()
    // ===========================================================

    @Test
    fun `signOut delega al auth del backend`() {
        every { mockAuth.signOut() } just Runs

        backend.signOut()

        verify(exactly = 1) { mockAuth.signOut() }
    }

    // ===========================================================
    // invalidateTemporaryPassword()
    // ===========================================================

    @Test
    fun `invalidateTemporaryPassword sin usuario activo devuelve UserNotFound`() = runTest {
        every { mockAuth.currentUser } returns null

        val result = backend.invalidateTemporaryPassword()

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(FirebaseException.UserNotFound::class.java)
    }

    @Test
    fun `invalidateTemporaryPassword success llama updatePassword con 32 caracteres`() = runTest {
        val mockUser = mockk<FirebaseUser>()
        every { mockAuth.currentUser } returns mockUser

        val capturedPassword = slot<String>()
        val updateTask = mockk<Task<Void>>()
        every { mockUser.updatePassword(capture(capturedPassword)) } returns updateTask
        every { updateTask.addOnSuccessListener(any<OnSuccessListener<Void>>()) } answers {
            firstArg<OnSuccessListener<Void>>().onSuccess(null)
            updateTask
        }
        every { updateTask.addOnFailureListener(any()) } returns updateTask

        val result = backend.invalidateTemporaryPassword()

        assertThat(result.isSuccess).isTrue()
        assertThat(capturedPassword.captured).hasLength(32)
    }

    // ===========================================================
    // Helpers privados
    // ===========================================================

    private fun fakeFirebaseUser(
        uid: String,
        email: String,
        displayName: String? = null,
        isEmailVerified: Boolean = true
    ): FirebaseUser = mockk<FirebaseUser>().also {
        every { it.uid } returns uid
        every { it.email } returns email
        every { it.displayName } returns displayName
        every { it.isEmailVerified } returns isEmailVerified
    }

    /** Configura el mock de Auth para un login+token exitoso en dos pasos. */
    private fun setupSuccessfulLogin(user: FirebaseUser, idToken: String) {
        val mockAuthResult = mockk<AuthResult>()
        val loginTask = mockk<Task<AuthResult>>()
        val tokenTask = mockk<Task<GetTokenResult>>()
        val mockTokenResult = mockk<GetTokenResult>()

        every { mockAuth.signInWithEmailAndPassword(any(), any()) } returns loginTask
        every { loginTask.addOnSuccessListener(any<OnSuccessListener<AuthResult>>()) } answers {
            firstArg<OnSuccessListener<AuthResult>>().onSuccess(mockAuthResult)
            loginTask
        }
        every { loginTask.addOnFailureListener(any()) } returns loginTask

        every { mockAuthResult.user } returns user
        every { user.getIdToken(false) } returns tokenTask
        every { tokenTask.addOnSuccessListener(any<OnSuccessListener<GetTokenResult>>()) } answers {
            firstArg<OnSuccessListener<GetTokenResult>>().onSuccess(mockTokenResult)
            tokenTask
        }
        every { tokenTask.addOnFailureListener(any()) } returns tokenTask
        every { mockTokenResult.token } returns idToken
    }
}
