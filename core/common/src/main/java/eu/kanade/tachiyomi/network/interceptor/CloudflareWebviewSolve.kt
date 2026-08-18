package eu.kanade.tachiyomi.network.interceptor

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * Launches the *visible* Cloudflare verification screen (an Activity that lives in the app
 * module). The network interceptor runs on a background thread and can't start activities
 * itself, so the app module registers a launcher at startup (see App.onCreate).
 */
fun interface CloudflareWebviewLauncher {
    fun launch(url: String, headers: Map<String, String>, host: String)
}

object CloudflareWebviewLauncherHolder {
    @Volatile
    var launcher: CloudflareWebviewLauncher? = null
}

/**
 * Tracks in-flight visible Cloudflare solves so the blocked network thread is woken up the
 * moment the user finishes (or abandons) the captcha. Keyed by host: the interceptor already
 * serializes solves per host, and the visible screen reports under the same host.
 */
object CloudflareWebviewSolveRegistry {

    private val pendingSolves = ConcurrentHashMap<String, CompletableFuture<Boolean>>()

    fun register(host: String): CompletableFuture<Boolean> =
        pendingSolves.computeIfAbsent(host) { CompletableFuture() }

    fun report(host: String, success: Boolean) {
        pendingSolves.remove(host)?.complete(success)
    }

    fun isPending(host: String): Boolean = pendingSolves.containsKey(host)
}
