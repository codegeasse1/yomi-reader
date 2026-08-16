package eu.kanade.presentation.reader.novel

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import eu.kanade.tachiyomi.data.book.novel.NovelBookImageExtractor
import eu.kanade.tachiyomi.source.novel.NovelPluginImage
import eu.kanade.tachiyomi.source.novel.NovelPluginImageResolver
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookDocument
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookEngineFlow
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookEngineRenderer
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookLocation
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookPageTurnResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume

/**
 * Dedicated Android WebView adapter for the book engine.
 *
 * It never shares a document with the chapter reader: every spine section is loaded into this
 * WebView as an isolated document, waits for the document's image-stabilized ready event, and only
 * then restores its exact text offset. Renderer events are generation-scoped so a late callback from
 * a replaced document cannot overwrite the current book location.
 */
internal class NovelBookWebViewRenderer(
    private val webView: WebView,
    private val onRelocated: (Int, Int) -> Unit = { _, _ -> },
    private val onDocumentMeasured: (Int, Long, Int) -> Unit = { _, _, _ -> },
    private val onScrollBoundary: (Boolean) -> Unit = {},
    private val readerCss: () -> String = { "" },
    private val resolveResource: (String) -> WebResourceResponse? = { null },
    private val operationTimeoutMillis: Long = DEFAULT_OPERATION_TIMEOUT_MILLIS,
) : NovelBookEngineRenderer {

    private val generations = AtomicLong(0L)

    @Volatile
    private var activeGeneration = 0L

    @Volatile
    private var pendingReady: CompletableDeferred<NovelBookRendererReady>? = null

    @Volatile
    private var disposed = false

    private val nativeBridge = NativeBridge()

    init {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = false
        webView.addJavascriptInterface(nativeBridge, NATIVE_BRIDGE_NAME)
        // Book-mode layout problems are invisible from Kotlin, so the document mirrors its own
        // measurements into logcat: adb logcat -s NovelBookWebView.
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                val message = consoleMessage?.message() ?: return false
                Log.i(CONSOLE_LOG_TAG, message)
                return true
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
            ): Boolean {
                val url = request?.url ?: return false
                // Only the about:blank shell and the document's own data/base URL are app-side
                // navigation; anything else belongs to the outer world.
                if (url.scheme == "about" || url.scheme == "data") return false
                return openExternally(url)
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?,
            ): WebResourceResponse? {
                val requestUrl = request?.url?.toString().orEmpty()
                resolveResource(requestUrl)?.let { return it }
                if (requestUrl.startsWith("file://")) {
                    // The book only needs the images the builder externalized next to the artifact.
                    // Serving arbitrary file:// URLs would let a crafted chapter read any app file.
                    runCatching {
                        val file = java.io.File(java.net.URI.create(requestUrl))
                        if (file.isFile && file.isUnderBookImagesDirectory()) {
                            val mimeType = when (file.extension.lowercase()) {
                                "png" -> "image/png"
                                "jpg", "jpeg" -> "image/jpeg"
                                "gif" -> "image/gif"
                                "webp" -> "image/webp"
                                "svg" -> "image/svg+xml"
                                "css" -> "text/css"
                                "js" -> "application/javascript"
                                else -> "application/octet-stream"
                            }
                            return WebResourceResponse(mimeType, null, file.inputStream())
                        }
                    }
                }
                if (!NovelPluginImage.isSupported(requestUrl)) {
                    return super.shouldInterceptRequest(view, request)
                }
                val image = NovelPluginImageResolver.resolveBlocking(requestUrl)
                    ?: return super.shouldInterceptRequest(view, request)
                return WebResourceResponse(
                    image.mimeType,
                    null,
                    ByteArrayInputStream(image.bytes),
                )
            }
        }
    }

    /** True when [file] lives inside the artifact's `images/` directory. */
    private fun File.isUnderBookImagesDirectory(): Boolean {
        if (!exists() || !isFile) return false
        return runCatching {
            canonicalFile.parentFile?.name == NovelBookImageExtractor.IMAGES_DIRECTORY_NAME
        }.getOrDefault(false)
    }

    /** Hands a URL to an external viewer, returning true when the request was consumed. */
    private fun openExternally(url: Uri): Boolean {
        return runCatching {
            val context = webView.context
            val intent = Intent(Intent.ACTION_VIEW, url).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    override suspend fun open(
        document: NovelBookDocument,
        location: NovelBookLocation,
        flow: NovelBookEngineFlow,
        restoreFraction: Float?,
    ) {
        val generation = generations.incrementAndGet()
        val ready = CompletableDeferred<NovelBookRendererReady>()
        activeGeneration = generation
        pendingReady?.cancel()
        pendingReady = ready

        val html = buildNovelBookEngineDocumentHtml(
            document = document,
            flow = flow,
            documentGeneration = generation,
            readerCss = readerCss(),
        )
        withContext(Dispatchers.Main.immediate) {
            webView.loadDataWithBaseURL(
                document.baseUrl?.takeIf { it.isNotBlank() } ?: FALLBACK_BASE_URL,
                html,
                HTML_MIME_TYPE,
                HTML_ENCODING,
                null,
            )
        }
        val rendererReady = withTimeout(operationTimeoutMillis) {
            ready.await()
        }
        if (generation != activeGeneration) return
        onDocumentMeasured(
            document.sectionIndex,
            document.chapterId,
            rendererReady.charCount,
        )
        if (generation != activeGeneration) return

        // Restoring by fraction is what makes reopening the book land on the same line: the stored
        // position was a fraction of an estimated chapter length, and only the document knows the
        // real one.
        val restoreScript = if (restoreFraction != null) {
            "window.__anBookEngine && window.__anBookEngine.goToFraction(${restoreFraction.coerceIn(0f, 1f)})"
        } else {
            "window.__anBookEngine && window.__anBookEngine.goTo(${location.charOffset})"
        }
        val restored = evaluatePageTurn(restoreScript)
        if (generation != activeGeneration) return
        val moved = restored as? NovelBookPageTurnResult.Moved
            ?: error("Book renderer failed to restore section ${document.sectionIndex}")
        onRelocated(moved.charOffset, document.sectionIndex)
    }

    override suspend fun next(transitionStyleName: String): NovelBookPageTurnResult =
        evaluatePageTurn("window.__anBookEngine && window.__anBookEngine.next('$transitionStyleName')")

    override suspend fun previous(transitionStyleName: String): NovelBookPageTurnResult =
        evaluatePageTurn("window.__anBookEngine && window.__anBookEngine.previous('$transitionStyleName')")

    override suspend fun relocate(): NovelBookPageTurnResult =
        evaluatePageTurn("window.__anBookEngine && window.__anBookEngine.relocate()")

    override suspend fun goTo(location: NovelBookLocation): NovelBookPageTurnResult =
        evaluatePageTurn(
            "window.__anBookEngine && window.__anBookEngine.goTo(${location.charOffset}, ${location.sectionIndex})",
        )

    /**
     * Adds a chapter to the live scrolled document instead of replacing it, which is what removes
     * the jump between chapters: the reader simply keeps scrolling into the next section.
     */
    override suspend fun appendSection(document: NovelBookDocument): Boolean =
        evaluateSectionMutation("appendSection", document)

    override suspend fun prependSection(document: NovelBookDocument): Boolean =
        evaluateSectionMutation("prependSection", document)

    override suspend fun replaceSection(document: NovelBookDocument): Boolean =
        evaluateSectionMutation("replaceSection", document)

    override suspend fun removeSection(sectionIndex: Int): Boolean =
        evaluateBoolean("window.__anBookEngine && window.__anBookEngine.removeSection($sectionIndex)")

    /**
     * Restyles the live document instead of reloading it.
     *
     * The stylesheet is shipped as a JSON string literal, the same way chapter markup is, so quotes
     * and newlines in user CSS cannot break the script. The location travels with it because new
     * type metrics move the text: the document restores the reader's position itself once the new
     * styles laid out.
     */
    override suspend fun applyReaderCss(
        css: String,
        location: NovelBookLocation,
    ): NovelBookPageTurnResult = evaluatePageTurn(
        "window.__anBookEngine && window.__anBookEngine.applyReaderCss(" +
            "${JsonPrimitive(css)}, ${location.sectionIndex}, ${location.charOffset})",
    )

    suspend fun close() {
        activeGeneration = generations.incrementAndGet()
        pendingReady?.cancel()
        pendingReady = null
        withContext(Dispatchers.Main.immediate) {
            webView.stopLoading()
            webView.removeJavascriptInterface(NATIVE_BRIDGE_NAME)
            webView.loadUrl("about:blank")
        }
    }

    /** Synchronous lifecycle entry point for Compose disposal. */
    fun dispose() {
        if (disposed) return
        disposed = true
        val disposeGeneration = activeGeneration
        pendingReady?.cancel()
        pendingReady = null
        webView.post {
            webView.stopLoading()
            webView.evaluateJavascript(
                "window.__anBookEngine && window.__anBookEngine.relocate()",
            ) { rawResult ->
                if (disposeGeneration == activeGeneration) {
                    val moved = parseNovelBookPageTurnResult(rawResult) as? NovelBookPageTurnResult.Moved
                    if (moved != null) {
                        onRelocated(moved.charOffset, moved.sectionIndex)
                    }
                }
                activeGeneration = generations.incrementAndGet()
                webView.removeJavascriptInterface(NATIVE_BRIDGE_NAME)
                webView.loadUrl("about:blank")
                webView.destroy()
            }
        }
    }

    private suspend fun evaluatePageTurn(script: String): NovelBookPageTurnResult =
        parseNovelBookPageTurnResult(evaluateRaw(script))
            ?: error("Book renderer returned an invalid navigation result")

    /** Ships a prepared chapter into the live document as a JSON-encoded string literal. */
    private suspend fun evaluateSectionMutation(
        method: String,
        document: NovelBookDocument,
    ): Boolean {
        val html = JsonPrimitive(document.html).toString()
        return evaluateBoolean(
            "window.__anBookEngine && window.__anBookEngine.$method(" +
                "${document.sectionIndex}, ${document.chapterId}, $html)",
        )
    }

    private suspend fun evaluateBoolean(script: String): Boolean =
        evaluateRaw(script)?.trim()?.trim('"') == "true"

    private suspend fun evaluateRaw(script: String): String? = withTimeout(operationTimeoutMillis) {
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine<String?> { continuation ->
                webView.evaluateJavascript(script) { result ->
                    if (continuation.isActive) {
                        continuation.resume(result)
                    }
                }
            }
        }
    }

    private inner class NativeBridge {
        @JavascriptInterface
        fun onReady(generation: Long, payload: String) {
            if (generation != activeGeneration) return
            val ready = parseNovelBookRendererReady(payload) ?: return
            pendingReady?.complete(ready)
        }

        @JavascriptInterface
        fun onRelocated(generation: Long, payload: String) {
            if (generation != activeGeneration) return
            val moved = parseNovelBookPageTurnResult(payload) as? NovelBookPageTurnResult.Moved ?: return
            webView.post {
                if (generation == activeGeneration) {
                    onRelocated(moved.charOffset, moved.sectionIndex)
                }
            }
        }

        /**
         * Real text length of a section the stitched document holds. Every resident chapter reports,
         * not only the one on screen, so whole-book progress runs on measured lengths.
         */
        @JavascriptInterface
        fun onSectionMeasured(
            generation: Long,
            sectionIndex: Int,
            chapterId: Long,
            charCount: Int,
        ) {
            if (generation != activeGeneration || charCount <= 0) return
            webView.post {
                if (generation == activeGeneration) {
                    onDocumentMeasured(sectionIndex, chapterId, charCount)
                }
            }
        }

        /**
         * Reported by the scrolled document when the reader runs out of section: the engine turns
         * that into the adjacent spine section, which is what makes chapters continuous again.
         */
        @JavascriptInterface
        fun onBoundary(generation: Long, direction: String) {
            if (generation != activeGeneration) return
            val forward = when (direction) {
                "end" -> true
                "start" -> false
                else -> return
            }
            webView.post {
                if (generation == activeGeneration) {
                    onScrollBoundary(forward)
                }
            }
        }
    }

    companion object {
        private const val NATIVE_BRIDGE_NAME = "AnBookNative"
        private const val CONSOLE_LOG_TAG = "NovelBookWebView"
        private const val FALLBACK_BASE_URL = "https://localhost/"
        private const val HTML_MIME_TYPE = "text/html"
        private const val HTML_ENCODING = "UTF-8"
        private const val DEFAULT_OPERATION_TIMEOUT_MILLIS = 15_000L
    }
}
