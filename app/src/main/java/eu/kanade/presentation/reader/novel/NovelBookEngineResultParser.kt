package eu.kanade.presentation.reader.novel

import eu.kanade.tachiyomi.ui.reader.novel.NovelBookPageTurnResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

private val novelBookEngineResultJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * Parses a synchronous result returned by the isolated book document renderer.
 *
 * Android WebView JSON-encodes JavaScript string return values, so valid payloads can arrive either
 * as a JSON object or as a quoted JSON string. Detached, reloading, or malformed documents return
 * null rather than leaking a renderer failure into reader navigation.
 */
internal fun parseNovelBookPageTurnResult(rawResult: String?): NovelBookPageTurnResult? {
    val raw = rawResult?.trim().orEmpty()
    if (raw.isEmpty() || raw == "null" || raw == "undefined") return null
    return runCatching {
        val outer = novelBookEngineResultJson.parseToJsonElement(raw)
        val payload = when (outer) {
            is JsonObject -> outer
            is JsonPrimitive -> {
                if (!outer.isString) return null
                novelBookEngineResultJson.parseToJsonElement(outer.content) as? JsonObject ?: return null
            }
            else -> return null
        }
        when ((payload["kind"] as? JsonPrimitive)?.content) {
            "moved" -> {
                val charOffset = (payload["charOffset"] as? JsonPrimitive)?.intOrNull ?: return null
                // A stitched scrolled document holds several chapters, so the position it reports is
                // only unambiguous together with the section it belongs to. Single-section documents
                // omit the field and keep the engine's current section.
                val sectionIndex = (payload["sectionIndex"] as? JsonPrimitive)?.intOrNull ?: -1
                NovelBookPageTurnResult.Moved(
                    charOffset = charOffset.coerceAtLeast(0),
                    sectionIndex = sectionIndex.coerceAtLeast(-1),
                )
            }
            "start" -> NovelBookPageTurnResult.StartOfDocument
            "end" -> NovelBookPageTurnResult.EndOfDocument
            else -> null
        }
    }.getOrNull()
}

internal data class NovelBookRendererReady(
    val pageCount: Int,
    val currentPage: Int,
    val charOffset: Int,
    val charCount: Int,
)

internal fun parseNovelBookRendererReady(rawResult: String?): NovelBookRendererReady? {
    val payload = parseNovelBookEnginePayload(rawResult) ?: return null
    if ((payload["kind"] as? JsonPrimitive)?.content != "ready") return null
    val pageCount = (payload["pageCount"] as? JsonPrimitive)?.intOrNull ?: return null
    val currentPage = (payload["currentPage"] as? JsonPrimitive)?.intOrNull ?: return null
    val charOffset = (payload["charOffset"] as? JsonPrimitive)?.intOrNull ?: return null
    val charCount = (payload["charCount"] as? JsonPrimitive)?.intOrNull ?: return null
    if (pageCount <= 0 || currentPage !in 0 until pageCount || charOffset < 0 || charCount <= 0) return null
    return NovelBookRendererReady(
        pageCount = pageCount,
        currentPage = currentPage,
        charOffset = charOffset.coerceAtMost(charCount - 1),
        charCount = charCount,
    )
}

private fun parseNovelBookEnginePayload(rawResult: String?): JsonObject? {
    val raw = rawResult?.trim().orEmpty()
    if (raw.isEmpty() || raw == "null" || raw == "undefined") return null
    return runCatching {
        when (val outer = novelBookEngineResultJson.parseToJsonElement(raw)) {
            is JsonObject -> outer
            is JsonPrimitive -> {
                if (!outer.isString) return null
                novelBookEngineResultJson.parseToJsonElement(outer.content) as? JsonObject
            }
            else -> null
        }
    }.getOrNull()
}
