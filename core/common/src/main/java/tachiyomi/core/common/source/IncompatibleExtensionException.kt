package tachiyomi.core.common.source

/**
 * Raised when a dynamically loaded extension links against app APIs that the running build does
 * not provide, i.e. when the extension APK and the host APK disagree on a shared ABI
 * (coroutines, RxJava, the source API itself).
 *
 * The JVM reports those mismatches as [LinkageError] subclasses ([NoSuchMethodError],
 * [NoClassDefFoundError], [AbstractMethodError], ...), which are errors and not exceptions, so
 * without explicit handling a single broken extension terminates the whole process instead of
 * failing the screen that used it. Extensions are third party plugins, therefore the host has
 * to contain their linkage failures the same way it contains their network failures.
 */
class IncompatibleExtensionException(cause: LinkageError) : Exception(
    "This extension is not compatible with the installed app version " +
        "(${cause.javaClass.simpleName}: ${cause.message}). " +
        "Update the extension, or the app, so both use the same APIs.",
    cause,
)
