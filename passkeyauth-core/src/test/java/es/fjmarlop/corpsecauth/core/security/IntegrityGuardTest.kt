package es.fjmarlop.corpsecauth.core.security

import com.google.common.truth.Truth.assertThat
import es.fjmarlop.corpsecauth.EmulatorPolicy
import es.fjmarlop.corpsecauth.RootPolicy
import es.fjmarlop.corpsecauth.core.errors.IntegrityException
import org.junit.Test

internal class IntegrityGuardTest {

    private fun evaluate(
        rootPolicy: RootPolicy = RootPolicy.Block,
        emulatorPolicy: EmulatorPolicy = EmulatorPolicy.Block,
        isRooted: Boolean = false,
        isEmulator: Boolean = false,
        isHooked: Boolean = false,
        isDebuggerAttached: Boolean = false,
        isDebugBuild: Boolean = false,
    ) = IntegrityGuard.evaluate(
        rootPolicy = rootPolicy,
        emulatorPolicy = emulatorPolicy,
        isRooted = isRooted,
        isEmulator = isEmulator,
        isHooked = isHooked,
        isDebuggerAttached = isDebuggerAttached,
        isDebugBuild = isDebugBuild,
        logger = {},
    )

    @Test
    fun `entorno limpio pasa`() {
        assertThat(evaluate().isSuccess).isTrue()
    }

    // --- Root ---

    @Test
    fun `root con policy Block falla con RootDetected`() {
        val r = evaluate(isRooted = true, rootPolicy = RootPolicy.Block)
        assertThat(r.isFailure).isTrue()
        assertThat(r.exceptionOrNull()).isInstanceOf(IntegrityException.RootDetected::class.java)
    }

    @Test
    fun `root con policy Warn pasa`() {
        assertThat(evaluate(isRooted = true, rootPolicy = RootPolicy.Warn).isSuccess).isTrue()
    }

    @Test
    fun `root con policy Allow pasa`() {
        assertThat(evaluate(isRooted = true, rootPolicy = RootPolicy.Allow).isSuccess).isTrue()
    }

    // --- Hooking ---

    @Test
    fun `hooking con policy Block falla con HookingDetected`() {
        val r = evaluate(isHooked = true, rootPolicy = RootPolicy.Block)
        assertThat(r.isFailure).isTrue()
        assertThat(r.exceptionOrNull()).isInstanceOf(IntegrityException.HookingDetected::class.java)
    }

    @Test
    fun `hooking con policy Warn pasa`() {
        assertThat(evaluate(isHooked = true, rootPolicy = RootPolicy.Warn).isSuccess).isTrue()
    }

    // --- Emulador ---

    @Test
    fun `emulador con policy Block falla con EmulatorDetected`() {
        val r = evaluate(isEmulator = true, emulatorPolicy = EmulatorPolicy.Block)
        assertThat(r.isFailure).isTrue()
        assertThat(r.exceptionOrNull()).isInstanceOf(IntegrityException.EmulatorDetected::class.java)
    }

    @Test
    fun `emulador con policy Warn pasa`() {
        assertThat(evaluate(isEmulator = true, emulatorPolicy = EmulatorPolicy.Warn).isSuccess).isTrue()
    }

    @Test
    fun `emulador con policy Allow pasa`() {
        assertThat(evaluate(isEmulator = true, emulatorPolicy = EmulatorPolicy.Allow).isSuccess).isTrue()
    }

    // --- Anti-debug (invariante en release) ---

    @Test
    fun `depurador en release falla con DebuggerAttached`() {
        val r = evaluate(isDebuggerAttached = true, isDebugBuild = false)
        assertThat(r.isFailure).isTrue()
        assertThat(r.exceptionOrNull()).isInstanceOf(IntegrityException.DebuggerAttached::class.java)
    }

    @Test
    fun `depurador en debug build se ignora`() {
        assertThat(evaluate(isDebuggerAttached = true, isDebugBuild = true).isSuccess).isTrue()
    }

    // --- Precedencia ---

    @Test
    fun `depurador tiene precedencia sobre root`() {
        val r = evaluate(isDebuggerAttached = true, isRooted = true, isDebugBuild = false)
        assertThat(r.exceptionOrNull()).isInstanceOf(IntegrityException.DebuggerAttached::class.java)
    }

    @Test
    fun `root tiene precedencia sobre emulador`() {
        val r = evaluate(isRooted = true, isEmulator = true)
        assertThat(r.exceptionOrNull()).isInstanceOf(IntegrityException.RootDetected::class.java)
    }
}
