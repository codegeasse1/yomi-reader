package eu.kanade.tachiyomi.data.suggestions.util

import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

/**
 * Guards calls that cross the app <-> extension boundary.
 *
 * Extensions are separate APKs that link against the library versions shipped
 * inside the host app (kotlinx.coroutines, OkHttp, ...). When an extension is
 * compiled against a different library ABI, the first call into it can throw a
 * [LinkageError] subclass ([NoSuchMethodError], [NoClassDefFoundError],
 * [IncompatibleClassChangeError], ...). Those are [Error]s, not [Exception]s,
 * so a plain `catch (e: Exception)` does not stop them from killing the whole
 * process (see the Natsu `BuildersKt.runBlockingK` crash).
 *
 * [runInterop]:
 * - rethrows [CancellationException] to keep structured concurrency intact;
 * - converts [LinkageError] and [Exception] into a `null` result and logs it;
 * - deliberately leaves truly fatal errors (OutOfMemoryError, ...) alone.
 */
object ExtensionInterop {

    suspend fun <T> runInterop(
        tag: String,
        operation: String,
        block: suspend () -> T,
    ): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: LinkageError) {
        logcat(LogPriority.WARN) {
            "[$tag] $operation: extension is binary-incompatible with the app " +
                "(${e.javaClass.simpleName}: ${e.message}). Update the extension or the app."
        }
        null
    } catch (e: Exception) {
        logcat { "[$tag] $operation failed: ${e.message}" }
        null
    }
}
