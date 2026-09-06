package eu.kanade.tachiyomi.network.interceptor

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

/**
 * Launches the *visible* Cloudflare verification screen (an Activity that lives in the app
 * module). The network interceptor runs on a background thread and can't start activities
 * itself, so the app module registers a launcher at startup (see App.onCreate).
 */
fun interface CloudflareWebviewLauncher {
    fun launch(url: String, headers: Map<String, String>, host: String, oldCookie: String?)
}

object CloudflareWebviewLauncherHolder {
    @Volatile
    var launcher: CloudflareWebviewLauncher? = null
}

/** Everything needed to present the visible verification screen for one host. */
data class VisibleSolveParams(
    val url: String,
    val headers: Map<String, String>,
    val oldCookie: String?,
)

/**
 * Tracks visible Cloudflare solves as a *global* single-flight queue. Only one verification
 * screen is ever shown at a time; hosts that need verification while another screen is already
 * up wait in the queue and are presented one after another (each screen closes itself on
 * success, then the next host opens). This stops global searches across many Cloudflare-gated
 * sources from stacking a dozen full-screen verification pages on top of each other.
 *
 * Solves are also coalesced per host: concurrent requests for the same host share one future
 * and one screen, exactly as before.
 */
object CloudflareWebviewSolveRegistry {

    private val lock = Any()
    private val pendingByHost = HashMap<String, CompletableFuture<Boolean>>()
    private val paramsByHost = HashMap<String, VisibleSolveParams>()
    private val queuedHosts = ArrayDeque<String>()
    @Volatile
    private var activeHost: String? = null

    /** Must be set at startup (App.onCreate) so screens can be launched on the main thread. */
    @Volatile
    var mainExecutor: Executor? = null

    /**
     * Registers a host that needs the visible screen. The blocked network thread should then
     * await the returned future; it completes with true the moment a fresh clearance appears
     * (or false if the user skips/cancels/times out).
     */
    fun registerSolve(host: String, params: VisibleSolveParams): CompletableFuture<Boolean> {
        synchronized(lock) {
            paramsByHost[host] = params
            pendingByHost[host]?.let { return it }
            val future = CompletableFuture<Boolean>()
            pendingByHost[host] = future
            queuedHosts.addLast(host)
            startNextIfIdleLocked()
            return future
        }
    }

    /**
     * The visible screen (or the launcher) reports that the solve for [host] finished. If it was
     * the active screen, the next queued host is shown. If it was still waiting in the queue
     * (e.g. its network thread timed out), it is dropped so it never pops up later.
     */
    fun report(host: String, success: Boolean) {
        var advance = false
        synchronized(lock) {
            if (activeHost == host) {
                activeHost = null
                pendingByHost.remove(host)?.complete(success)
                paramsByHost.remove(host)
                advance = true
            } else {
                pendingByHost.remove(host)?.complete(success)
                paramsByHost.remove(host)
                queuedHosts.removeAll { it == host }
            }
        }
        if (advance) {
            synchronized(lock) { startNextIfIdleLocked() }
        }
    }

    /** Completes every pending solve (active + queued) with false and stops the queue. */
    fun cancelAll() {
        synchronized(lock) {
            activeHost = null
            pendingByHost.forEach { (_, future) -> future.complete(false) }
            pendingByHost.clear()
            paramsByHost.clear()
            queuedHosts.clear()
        }
    }

    /** Skips the currently-shown screen (reports false), which then opens the next queued host. */
    fun skipActive() {
        activeHost?.let { report(it, false) }
    }

    fun isPending(host: String): Boolean = synchronized(lock) { pendingByHost.containsKey(host) }

    fun isActive(): Boolean = synchronized(lock) { activeHost != null }

    /** Number of hosts waiting in the queue behind the currently-shown screen. */
    fun pendingCount(): Int = synchronized(lock) { queuedHosts.size }

    fun queuedHosts(): List<String> = synchronized(lock) { queuedHosts.toList() }

    private fun startNextIfIdleLocked() {
        if (activeHost != null) return
        val next = queuedHosts.removeFirstOrNull() ?: return
        val params = paramsByHost[next]
        if (params == null) {
            pendingByHost.remove(next)?.complete(false)
            startNextIfIdleLocked()
            return
        }
        activeHost = next
        val launcher = CloudflareWebviewLauncherHolder.launcher
        val executor = mainExecutor
        if (launcher == null || executor == null) {
            activeHost = null
            pendingByHost.remove(next)?.complete(false)
            paramsByHost.remove(next)
            startNextIfIdleLocked()
            return
        }
        executor.execute {
            try {
                launcher.launch(params.url, params.headers, next, params.oldCookie)
            } catch (t: Throwable) {
                report(next, false)
            }
        }
    }
}
