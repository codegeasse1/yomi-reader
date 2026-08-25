package eu.kanade.tachiyomi.network.interceptor

import android.content.Context
import android.webkit.CookieManager
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.network.AndroidCookieJar
import eu.kanade.tachiyomi.util.system.isOutdated
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class CloudflareInterceptor(
    private val context: Context,
    private val cookieManager: AndroidCookieJar,
    defaultUserAgentProvider: () -> String,
    private val challengeResolver: CloudflareChallengeResolver? = null,
) : WebViewInterceptor(context, defaultUserAgentProvider) {

    private val challengeLockByHost = ConcurrentHashMap<String, Any>()
    private val webViewChallengeResolver = challengeResolver ?: WebViewCloudflareChallengeResolver(
        context = context,
        cookieManager = cookieManager,
        mainExecutor = ContextCompat.getMainExecutor(context),
        createWebView = this::createWebView,
        parseHeaders = this::parseHeaders,
        isWebViewOutdated = { it.isOutdated() },
    )

    override fun shouldIntercept(response: Response): Boolean {
        if (response.code !in ERROR_CODES ||
            !CloudflareChallengeDetector.isCloudflareServer(response.header("Server"))
        ) {
            return false
        }
        // The cf-mitigated header is authoritative for managed/interactive challenges.
        if (CloudflareChallengeDetector.isManagedChallenge(response.header("cf-mitigated"))) {
            return true
        }
        // Limit body inspection to a small prefix; challenge markup is at the top of the
        // document and full body parsing wastes memory on large pages. The detector covers
        // the full family of challenge markers (interstitial, interactive, error), not just
        // the two error markers the interceptor used to look for.
        val bodyPeek = response.peekBody(CHALLENGE_PEEK_BYTES).string()
        return CloudflareChallengeDetector.hasChallengeMarkers(bodyPeek)
    }

    override fun intercept(chain: Interceptor.Chain, request: Request, response: Response): Response {
        val host = request.url.host
        try {
            response.close()
            // One lock object per host is kept for the process lifetime so concurrent
            // requests to the same host coalesce onto a single WebView solve and then
            // reuse the resulting cf_clearance. Removing it eagerly (as before) reintroduced
            // a race: a late arrival would create a *fresh* lock and solve in parallel,
            // spinning up duplicate WebViews and racing on the cookie. Hosts are few, so the
            // retained lock objects are negligible.
            val hostLock = challengeLockByHost.getOrPut(host) { Any() }
            synchronized(hostLock) {
                val oldCookie = cookieManager.get(request.url)
                    .firstOrNull { it.name == "cf_clearance" }

                // Only pay for an immediate network retry when there is a clearance to try.
                // With no cookie this was just an extra blocked request before WebView.
                // For coalesced followers this is the fast path: the leader already solved
                // the challenge, so the fresh clearance usually clears them without WebView.
                if (oldCookie != null) {
                    val immediateRetry = chain.proceed(request)
                    if (!shouldIntercept(immediateRetry)) {
                        return immediateRetry
                    }
                    immediateRetry.close()
                    cookieManager.remove(request.url, COOKIE_NAMES, 0)
                }

                webViewChallengeResolver.resolve(request, oldCookie)

                // The fresh cf_clearance has to be visible to OkHttp before the retry: WebView
                // writes land in the shared Android CookieManager, so flush it and give Cloudflare
                // a beat to register the just-solved clearance. Without this the first retry can
                // still be challenged and the request "keeps loading" before failing - the exact
                // failure a manual re-tap used to paper over.
                CookieManager.getInstance().flush()

                var attempt = chain.proceed(request)
                var retries = 0
                while (shouldIntercept(attempt) && retries < MAX_RETRIES_AFTER_SOLVE) {
                    attempt.close()
                    retries++
                    try {
                        Thread.sleep(RETRY_DELAY_MS)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                    CookieManager.getInstance().flush()
                    attempt = chain.proceed(request)
                }
                return attempt
            }
        }
        // Because OkHttp's enqueue only handles IOExceptions, wrap the exception so that
        // we don't crash the entire app
        catch (e: CloudflareInteractiveChallengeException) {
            throw IOException(
                context.stringResource(MR.strings.information_cloudflare_interactive_challenge),
                e,
            )
        } catch (e: CloudflareBypassException) {
            throw IOException(context.stringResource(MR.strings.information_cloudflare_bypass_failure), e)
        } catch (e: Exception) {
            throw IOException(e)
        }
    }
}

internal val ERROR_CODES = listOf(403, 503)
private val COOKIE_NAMES = listOf("cf_clearance")

// Retries after a successful solve: Cloudflare sometimes needs a moment to accept a freshly
// minted clearance, so we try a few times with a short settle delay instead of giving up.
private const val MAX_RETRIES_AFTER_SOLVE = 2
private const val RETRY_DELAY_MS = 500L

// Just enough to capture the challenge headers/markers; the page body is larger
// but the challenge identifiers always appear near the top.
private const val CHALLENGE_PEEK_BYTES = 8L * 1024L
