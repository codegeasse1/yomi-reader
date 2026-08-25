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

    private val cookiePoller = Handler(Looper.getMainLooper())

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

    private fun reportSuccess() {
        if (reported) return
        reported = true
        host?.let { CloudflareWebviewSolveRegistry.report(it, true) }
        finish()
    }

    private fun closeManually() {
        if (!reported) {
            reported = true
            host?.let { CloudflareWebviewSolveRegistry.report(it, false) }
        }
        finish()
    }

    override fun onDestroy() {
        cookiePoller.removeCallbacksAndMessages(null)
        if (!reported) {
            reported = true
            host?.let { CloudflareWebviewSolveRegistry.report(it, false) }
        }
        super.onDestroy()
    }

    companion object {
        private const val URL_KEY = "url_key"
        private const val HOST_KEY = "host_key"
        private const val HEADERS_KEY = "headers_key"
        private const val OLD_COOKIE_KEY = "old_cookie_key"
        private const val COOKIE_POLL_INTERVAL_MS = 500L

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
