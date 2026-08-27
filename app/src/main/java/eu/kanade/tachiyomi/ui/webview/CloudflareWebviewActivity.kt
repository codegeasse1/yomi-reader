package eu.kanade.tachiyomi.ui.webview

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.webview.shouldReloadMainFrameNavigation
import eu.kanade.tachiyomi.network.interceptor.CloudflareWebviewSolveRegistry
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.util.system.setDefaultSettings
import eu.kanade.tachiyomi.util.view.setComposeContent
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Automatically-opened WebView shown while a source is stuck on a Cloudflare challenge that the
 * hidden WebView couldn't auto-solve (e.g. a Turnstile captcha). It loads the challenge URL and
 * closes itself the moment a *fresh* cf_clearance cookie appears, letting the blocked network
 * request resume on its own. It only ever opens when a solve is actually required, and it only
 * closes when a new clearance is detected (or the user closes it manually).
 */
class CloudflareWebviewActivity : BaseActivity() {

    private var url: String? = null
    private var host: String? = null
    private var oldCookieValue: String? = null
    private var reported = false

    private var webView: WebView? = null

    private val cookiePoller = Handler(Looper.getMainLooper())

    // JS challenge-state polling (see pollChallengeState): detects the
    // challenge -> real-content transition so the screen closes itself the
    // moment verification completes, even on sites that don't set cf_clearance
    // as a cookie the CookieManager exposes. Mirrors the Hikari verify view.
    private var challengeSeen = false
    private var noChallengePolls = 0
    private var blockedCount = 0
    private val verifyHandler = Handler(Looper.getMainLooper())

    init {
        registerSecureActivity(this)
    }

    @Suppress("DEPRECATION", "UNCHECKED_CAST")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = intent.extras?.getString(URL_KEY)
            ?: run {
                finish()
                return
            }
        this.url = url
        host = intent.extras?.getString(HOST_KEY)
        oldCookieValue = intent.extras?.getString(OLD_COOKIE_KEY)
        val headers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(HEADERS_KEY, HashMap::class.java) as? HashMap<String, String>
        } else {
            intent.getSerializableExtra(HEADERS_KEY) as? HashMap<String, String>
        } ?: HashMap()

        startCookiePolling()
        startChallengeStatePolling()
        val webView = createWebView(url, headers)

        setComposeContent {
            CloudflareWebviewScreen(
                webView = webView,
                url = url,
                onClose = ::closeManually,
                onReload = { webView.loadUrl(url, headers) },
            )
        }
    }

    private fun createWebView(url: String, headers: Map<String, String>): WebView {
        return WebView(this).apply {
            webView = this
            setDefaultSettings()
            headers.entries
                .firstOrNull { it.key.equals("user-agent", ignoreCase = true) }
                ?.value
                ?.let { settings.userAgentString = it }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): Boolean {
                    val requestedUrl = request?.url?.toString() ?: return false
                    if (requestedUrl.startsWith("intent://")) return true
                    if (shouldReloadMainFrameNavigation(
                            requestUrl = requestedUrl,
                            currentUrl = view?.url,
                            isForMainFrame = request.isForMainFrame,
                            method = request.method,
                        )
                    ) {
                        view?.loadUrl(requestedUrl, headers)
                        return true
                    }
                    return false
                }

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    CookieManager.getInstance().flush()
                    if (hasFreshClearance()) reportSuccess()
                }

                override fun onReceivedHttpError(
                    view: WebView,
                    request: WebResourceRequest,
                    errorResponse: WebResourceResponse,
                ) {
                    super.onReceivedHttpError(view, request, errorResponse)
                    CookieManager.getInstance().flush()
                    if (hasFreshClearance()) reportSuccess()
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError,
                ) {
                    super.onReceivedError(view, request, error)
                    CookieManager.getInstance().flush()
                    if (hasFreshClearance()) reportSuccess()
                }
            }
            loadUrl(url, headers)
        }
    }

    private fun startCookiePolling() {
        cookiePoller.post(object : Runnable {
            override fun run() {
                if (reported) return
                if (hasFreshClearance()) {
                    reportSuccess()
                    return
                }
                cookiePoller.postDelayed(this, COOKIE_POLL_INTERVAL_MS)
            }
        })
    }

    /**
     * Polls the page's own DOM every ~1.2s to tell whether it still looks like
     * a WAF challenge. Closes the screen on the same transitions the Hikari
     * verify view uses:
     *  - a challenge we've seen turning into real content = verification done;
     *  - a hard WAF block that can never mint a clearance = close after 3 ticks;
     *  - no challenge ever appearing (page loads as ordinary content) = close
     *    after a short grace period instead of lingering forever.
     */
    private fun startChallengeStatePolling() {
        verifyHandler.post(object : Runnable {
            override fun run() {
                if (reported) return
                val v = webView
                if (v == null) {
                    verifyHandler.postDelayed(this, CHALLENGE_POLL_INTERVAL_MS)
                    return
                }
                v.evaluateJavascript(CHALLENGE_STATE_JS) { res ->
                    when (res?.trim()?.trim('"')) {
                        "1" -> {
                            challengeSeen = true
                            blockedCount = 0
                            noChallengePolls = 0
                        }
                        "2" -> {
                            blockedCount++
                            noChallengePolls = 0
                            if (blockedCount >= HARD_BLOCK_POLLS) {
                                finishSolve(false)
                                return@evaluateJavascript
                            }
                        }
                        else -> if (challengeSeen) {
                            finishSolve(true)
                            return@evaluateJavascript
                        } else {
                            noChallengePolls++
                            if (noChallengePolls >= NO_CHALLENGE_POLLS) {
                                finishSolve(false)
                                return@evaluateJavascript
                            }
                        }
                    }
                    if (!reported) verifyHandler.postDelayed(this, CHALLENGE_POLL_INTERVAL_MS)
                }
            }
        })
    }

    private fun hasFreshClearance(): Boolean {
        val url = url ?: return false
        val host = host ?: return false
        // CookieManager needs a full URL (scheme + host), not a bare hostname, or it
        // always returns no cookies and the screen never closes itself after a solve.
        return listOfNotNull(
            url,
            "https://$host",
            "http://$host",
        ).any {
            val value = cfClearanceValue(CookieManager.getInstance().getCookie(it))
            value != null && value != oldCookieValue
        }
    }

    private fun finishSolve(success: Boolean) {
        if (reported) return
        reported = true
        host?.let { CloudflareWebviewSolveRegistry.report(it, success) }
        finish()
    }

    private fun reportSuccess() = finishSolve(true)

    private fun closeManually() = finishSolve(false)

    override fun onDestroy() {
        cookiePoller.removeCallbacksAndMessages(null)
        verifyHandler.removeCallbacksAndMessages(null)
        finishSolve(false)
        super.onDestroy()
    }

    companion object {
        private const val URL_KEY = "url_key"
        private const val HOST_KEY = "host_key"
        private const val HEADERS_KEY = "headers_key"
        private const val OLD_COOKIE_KEY = "old_cookie_key"
        private const val COOKIE_POLL_INTERVAL_MS = 500L

        // JS poll cadence + close thresholds (matches the Hikari verify view).
        private const val CHALLENGE_POLL_INTERVAL_MS = 1_200L
        private const val HARD_BLOCK_POLLS = 3
        private const val NO_CHALLENGE_POLLS = 12

        /** Returns whether the page still looks like a WAF challenge:
         *  1 = challenge present, 2 = hard block, 0 = neither (real content). */
        private val CHALLENGE_STATE_JS = """
            (function(){
              var t=(document.title||'').toLowerCase();
              var h=location.href.toLowerCase();
              var b=document.body?document.body.innerText.slice(0,3000).toLowerCase():'';
              var chal=(t.indexOf('just a moment')>=0||t.indexOf('attention required')>=0||
              h.indexOf('cdn-cgi/challenge')>=0||h.indexOf('challenge-platform')>=0||
              b.indexOf('verify you are human')>=0||b.indexOf('performing security verification')>=0||
              b.indexOf('checking your browser')>=0||b.indexOf('cf-chl')>=0||
              b.indexOf('turnstile')>=0);
              var block=(t.indexOf('you have been blocked')>=0||t.indexOf('access denied')>=0||
              h.indexOf('cf-error')>=0||b.indexOf('you have been blocked')>=0||
              b.indexOf('access denied')>=0||b.indexOf('request blocked')>=0||
              b.indexOf('cf-error-details')>=0);
              return chal?1:(block?2:0);
            })();
        """.trimIndent()

        fun newIntent(
            context: Context,
            url: String,
            headers: Map<String, String>,
            host: String,
            oldCookie: String? = null,
        ): Intent {
            return Intent(context, CloudflareWebviewActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(URL_KEY, url)
                putExtra(HOST_KEY, host)
                putExtra(HEADERS_KEY, HashMap(headers))
                putExtra(OLD_COOKIE_KEY, oldCookie)
            }
        }

        private fun cfClearanceValue(cookieHeader: String?): String? {
            val header = cookieHeader ?: return null
            return Regex("(?:^|;)\\s*cf_clearance=([^;]+)")
                .find(header)
                ?.groupValues
                ?.getOrNull(1)
        }
    }
}

@Composable
private fun CloudflareWebviewScreen(
    webView: WebView,
    url: String,
    onClose: () -> Unit,
    onReload: () -> Unit,
) {
    BackHandler(onBack = onClose)
    Scaffold(
        topBar = {
            AppBar(
                title = stringResource(MR.strings.title_cloudflare_verification),
                subtitle = url,
                navigateUp = onClose,
                navigationIcon = Icons.Outlined.Close,
                actions = {
                    AppBarActions(
                        persistentListOf(
                            AppBar.Action(
                                title = stringResource(MR.strings.action_webview_refresh),
                                icon = Icons.Outlined.Refresh,
                                onClick = onReload,
                            ),
                        ),
                    )
                },
            )
        },
    ) { contentPadding ->
        AndroidView(
            factory = { webView },
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        )
    }
}
