package es.fjmarlop.corpsecauth.core.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Debug
import es.fjmarlop.corpsecauth.EmulatorPolicy
import es.fjmarlop.corpsecauth.RootPolicy
import es.fjmarlop.corpsecauth.core.errors.IntegrityException

/**
 * Orquesta las comprobaciones de integridad del entorno antes de que el SDK
 * opere (ADR-015). Se invoca desde `PasskeyAuth.initialize()`.
 *
 * La lógica de decisión ([evaluate]) es pura sobre sus parámetros para poder
 * testear cada combinación política × señal en JVM sin device. [check] cablea
 * los detectores reales al [Context].
 */
internal object IntegrityGuard {

    /**
     * Decide si el SDK puede inicializar según las políticas y las señales.
     *
     * Precedencia de fallo: depurador (solo release) → root → hooking → emulador.
     * Devuelve el primer fallo bloqueante; si ninguna política es `Block`, éxito.
     */
    fun evaluate(
        rootPolicy: RootPolicy,
        emulatorPolicy: EmulatorPolicy,
        isRooted: Boolean,
        isEmulator: Boolean,
        isHooked: Boolean,
        isDebuggerAttached: Boolean,
        isDebugBuild: Boolean,
        logger: (String) -> Unit = ::println,
    ): Result<Unit> {
        // Anti-debug: invariante en release (no configurable). En debug se ignora.
        if (!isDebugBuild && isDebuggerAttached) {
            logger("🚨 IntegrityGuard: depurador adjunto en build de release")
            return Result.failure(IntegrityException.DebuggerAttached())
        }

        // Root: gobernado por rootPolicy
        if (isRooted) {
            when (rootPolicy) {
                RootPolicy.Block -> {
                    logger("🚨 IntegrityGuard: device rooteado (policy=Block)")
                    return Result.failure(IntegrityException.RootDetected())
                }
                RootPolicy.Warn -> logger("⚠️ IntegrityGuard: device rooteado (policy=Warn, continuando)")
                RootPolicy.Allow -> { /* omitido */ }
            }
        }

        // Hooking (Frida/Xposed): gobernado por rootPolicy (mismo nivel de amenaza)
        if (isHooked) {
            when (rootPolicy) {
                RootPolicy.Block -> {
                    logger("🚨 IntegrityGuard: framework de hooking detectado (policy=Block)")
                    return Result.failure(IntegrityException.HookingDetected())
                }
                RootPolicy.Warn -> logger("⚠️ IntegrityGuard: hooking detectado (policy=Warn, continuando)")
                RootPolicy.Allow -> { /* omitido */ }
            }
        }

        // Emulador: gobernado por emulatorPolicy
        if (isEmulator) {
            when (emulatorPolicy) {
                EmulatorPolicy.Block -> {
                    logger("🚨 IntegrityGuard: emulador detectado (policy=Block)")
                    return Result.failure(IntegrityException.EmulatorDetected())
                }
                EmulatorPolicy.Warn -> logger("⚠️ IntegrityGuard: emulador detectado (policy=Warn, continuando)")
                EmulatorPolicy.Allow -> { /* omitido */ }
            }
        }

        return Result.success(Unit)
    }

    /**
     * Cablea los detectores reales al device y evalúa las políticas.
     */
    fun check(
        context: Context,
        rootPolicy: RootPolicy,
        emulatorPolicy: EmulatorPolicy,
        isDebugBuild: Boolean,
    ): Result<Unit> {
        val packageCheck: (String) -> Boolean = { pkg -> isPackageInstalled(context, pkg) }

        return evaluate(
            rootPolicy = rootPolicy,
            emulatorPolicy = emulatorPolicy,
            isRooted = RootDetector.isProbablyRooted(isPackageInstalled = packageCheck),
            isEmulator = EmulatorDetector.isProbablyEmulator(),
            isHooked = HookDetector.isHookingDetected(isPackageInstalled = packageCheck),
            isDebuggerAttached = Debug.isDebuggerConnected() || Debug.waitingForDebugger(),
            isDebugBuild = isDebugBuild,
        )
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        } catch (e: Exception) {
            false
        }
    }
}
