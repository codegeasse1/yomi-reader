package eu.kanade.tachiyomi.network.interceptor

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import eu.kanade.tachiyomi.network.AndroidCookieJar
import eu.kanade.tachiyomi.util.system.toast
import okhttp3.Cookie
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import tachiyomi.i18n.MR
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

interface CloudflareChallengeResolver {
    fun resolve(originalRequest: Request, oldCookie: Cookie?)
}

internal class WebViewCloudflareChallengeResolver(
    private val context: Context,
    private val cookieManager: AndroidCookieJar,
    private val mainExecutor: Executor,
    private val createWebView: (Request) -> WebView,
    private val parseHeaders: (Headers) -> Map<String, String>,
    private val isWebViewOutdated: (WebView) -> Boolean,
) : CloudflareChallengeResolver {

    /**
     * Two-stage solve:
     *
     * 1. A hidden WebView auto-solves non-interactive challenges ("Just a moment…"). The
     *    cookie jar is polled so a slow solve is caught the instant it lands, and the DOM is
     *    probed for a Turnstile widget so interactive challenges are recognised early.
     *
     * 2. If the hidden WebView could not produce a fresh cf_clearance, the challenge is handed
     *    to a *visible* WebView screen that opens automatically and closes itself the moment a
     *    fresh cf_clearance appears (see CloudflareWebviewActivity). It is only opened when the
     *    hidden WebView genuinely got stuck - never for challenges that auto-solve.
     */
    @SuppressLint("SetJavaScriptEnabled")
    override fun resolve(originalRequest: Request, oldCookie: Cookie?) {
        val headless = solveHeadless(originalRequest, oldCookie)

        val outdated = if (!headless.bypassed) {
            headless.webview?.let(isWebViewOutdated) == true
        } else {
            false
        }

        destroyWebView(headless.webview)

        if (headless.bypassed) return

        if (solveVisibleChallenge(originalRequest, oldCookie)) return

        when {
            outdated -> context.toast(MR.strings.information_webview_outdated, Toast.LENGTH_LONG)
            headless.interactive -> context.toast(
                MR.strings.information_cloudflare_interactive_challenge,
                Toast.LENGTH_LONG,
            )
            else -> context.toast(MR.strings.information_cloudflare_bypass_failure, Toast.LENGTH_LONG)
        }
        throw CloudflareBypassException()
    }

    private class HeadlessSolve {
        @Volatile var bypassed = false

        @Volatile var interactive = false

        @Volatile var webview: WebView? = null
    }

    private fun solveHeadless(originalRequest: Request, oldCookie: Cookie?): HeadlessSolve {
        val result = HeadlessSolve()
        val latch = CountDownLatch(1)
        var challengeFound = false

        val origRequestUrl = originalRequest.url.toString()
        val headers = parseHeaders(originalRequest.headers)
        val startedAt = System.currentTimeMillis()

        mainExecutor.execute {
            val createdWebView = createWebView(originalRequest)
            result.webview = createdWebView

            createdWebView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    if (hasNewCloudflareClearance(originalRequest, url, oldCookie)) {
                        result.bypassed = true
                        CookieManager.getInstance().flush()
                        latch.countDown()
                        return
                    }
                    if (challengeFound || url == origRequestUrl) {
                        detectInteractiveWidget(view) { detected ->
                            if (detected && !result.bypassed) {
                                result.interactive = true
                                latch.countDown()
                            }
                        }
                    }
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) {
                        latch.countDown()
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView,
                    request: WebResourceRequest,
                    errorResponse: WebResourceResponse,
                ) {
                    if (request.isForMainFrame) {
                        if (errorResponse.statusCode in ERROR_CODES) {
                            challengeFound = true
                        } else {
                            latch.countDown()
                        }
                    }
                }
            }

            createdWebView.loadUrl(origRequestUrl, headers)

            // Cloudflare's managed challenges sometimes mount the Turnstile iframe a moment
            // after the page loads, so keep probing for the widget while the page is busy.
            val probeHandler = Handler(Looper.getMainLooper())
            var probeCount = 0
            val probe = object : Runnable {
                override fun run() {
                    if (result.bypassed || result.interactive) return
                    if (probeCount++ >= MAX_WIDGET_PROBES) return
                    detectInteractiveWidget(createdWebView) { detected ->
                        if (detected && !result.bypassed) {
                            result.interactive = true
                            latch.countDown()
                        }
                    }
                    if (!result.bypassed && !result.interactive) {
                        probeHandler.postDelayed(this, WIDGET_PROBE_INTERVAL_MS)
                    }
                }
            }
            probe.run()
        }

        // Wait for the hidden WebView, polling the cookie jar so a clearance that lands
        // without triggering a page callback is still caught immediately.
        while (true) {
            if (latch.await(COOKIE_POLL_INTERVAL_MS, TimeUnit.MILLISECONDS)) break
            if (result.bypassed || result.interactive) break
            if (hasNewCloudflareClearance(originalRequest, origRequestUrl, oldCookie)) {
                result.bypassed = true
                break
            }
            if (System.currentTimeMillis() - startedAt >= HEADLESS_SOLVE_TIMEOUT_MS) break
        }

        if (!result.bypassed && result.webview != null) {
            result.interactive = detectInteractiveWidgetSync(result.webview) || result.interactive
        }

        return result
    }

    /**
     * Hands the challenge to the visible Cloudflare verification screen. The screen reports
     * back (success/failure) through [CloudflareWebviewSolveRegistry]; this call blocks until
     * it does, the user finishes the captcha, or a timeout elapses.
     */
    private fun solveVisibleChallenge(originalRequest: Request, oldCookie: Cookie?): Boolean {
        val launcher = CloudflareWebviewLauncherHolder.launcher ?: return false
        val host = originalRequest.url.host

        // If a visible solve for this host is already pending (a parallel request coalesced
        // here), reuse it instead of stacking another screen on top of the first.
        val wasPending = CloudflareWebviewSolveRegistry.isPending(host)
        val future = CloudflareWebviewSolveRegistry.register(host)

        if (!wasPending) {
            val headers = parseHeaders(originalRequest.headers)
            try {
                mainExecutor.execute {
                    try {
                        launcher.launch(
                            url = originalRequest.url.toString(),
                            headers = headers,
                            host = host,
                            oldCookie = oldCookie?.value,
                        )
                    } catch (_: Throwable) {
                        CloudflareWebviewSolveRegistry.report(host, false)
                    }
                }
            } catch (_: Throwable) {
                CloudflareWebviewSolveRegistry.report(host, false)
            }
        }

        return try {
            val solved = future.get(VISIBLE_SOLVE_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            // Even if the screen reported failure (e.g. the user closed it at the exact moment
            // the solve landed), a fresh clearance in the jar means the challenge actually passed.
            solved || hasNewCloudflareClearance(originalRequest, originalRequest.url.toString(), oldCookie)
        } catch (_: Exception) {
            CloudflareWebviewSolveRegistry.report(host, false)
            hasNewCloudflareClearance(originalRequest, originalRequest.url.toString(), oldCookie)
        }
    }

    private fun destroyWebView(webview: WebView?) {
        if (webview == null) return
        mainExecutor.execute {
            webview.run {
                stopLoading()
                destroy()
            }
        }
    }

    private fun hasNewCloudflareClearance(originalRequest: Request, currentUrl: String, oldCookie: Cookie?): Boolean {
        return listOfNotNull(originalRequest.url, currentUrl.toHttpUrlOrNull())
            .distinctBy { it.host }
            .any { url ->
                val cookie = cookieManager.get(url).firstOrNull { it.name == "cf_clearance" }
                cookie != null && (url.host != originalRequest.url.host || cookie != oldCookie)
            }
    }

    private fun detectInteractiveWidget(webview: WebView, onResult: (Boolean) -> Unit) {
        try {
            webview.evaluateJavascript(INTERACTIVE_WIDGET_PROBE) { result ->
                onResult(result == "true")
            }
        } catch (_: Throwable) {
            onResult(false)
        }
    }

    private fun detectInteractiveWidgetSync(webview: WebView): Boolean {
        val checkLatch = CountDownLatch(1)
        var detected = false
        mainExecutor.execute {
            try {
                webview.evaluateJavascript(INTERACTIVE_WIDGET_PROBE) { result ->
                    detected = result == "true"
                    checkLatch.countDown()
                }
            } catch (_: Throwable) {
                checkLatch.countDown()
            }
        }
        checkLatch.await(2, TimeUnit.SECONDS)
        return detected
    }
}

internal val INTERACTIVE_WIDGET_PROBE = """
    (function() {
        try {
            return document.querySelector(
                '.cf-turnstile, [data-sitekey], iframe[src*="challenges.cloudflare.com"]'
            ) != null;
        } catch (_) {
            return false;
        }
    })();
""".trimIndent()

// How long the hidden WebView may spend auto-solving before we escalate to the visible screen.
private const val HEADLESS_SOLVE_TIMEOUT_MS = 20_000L

// How often the cookie jar is polled for a freshly-landed cf_clearance.
private const val COOKIE_POLL_INTERVAL_MS = 250L

// Widget-probe cadence while the hidden WebView is busy (Turnstile renders late sometimes).
private const val WIDGET_PROBE_INTERVAL_MS = 1_500L
private const val MAX_WIDGET_PROBES = 8

// How long the visible verification screen may stay open for a human to solve a captcha.
private const val VISIBLE_SOLVE_TIMEOUT_MINUTES = 5L

internal open class CloudflareBypassException : Exception()
internal class CloudflareInteractiveChallengeException : CloudflareBypassException()
