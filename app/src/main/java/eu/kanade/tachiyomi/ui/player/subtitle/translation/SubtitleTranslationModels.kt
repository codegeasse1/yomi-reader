package eu.kanade.tachiyomi.ui.player.subtitle.translation

import java.security.MessageDigest

// Bump when the translation pipeline changes its output shape (tag masking,
// segment cleaning, prompt schema). Old cache entries are then invalidated.
const val SUBTITLE_TRANSLATION_CACHE_SCHEMA_VERSION = 2

data class SubtitleCue(
    val index: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val settings: String? = null,
)

data class SubtitleDocument(
    val format: SubtitleFormat,
    val cues: List<SubtitleCue>,
    val headerLines: List<String> = emptyList(),
)

enum class SubtitleFormat(val extension: String) {
    Srt("srt"),
    Vtt("vtt"),
    Ass("ass"),
    Unknown("vtt"),
}

enum class SubtitleTranslationProviderId {
    Google,
    Ai,
}

data class SubtitleTranslationRequest(
    val document: SubtitleDocument,
    val sourceLanguage: String,
    val targetLanguage: String,
    val providerId: SubtitleTranslationProviderId,
    val sourceIdentity: String,
    val useCache: Boolean = true,
    val bilingual: Boolean = false,
    val title: String? = null,
    val genreHint: String? = null,
)

/** Extra signals passed to providers for higher-quality, consistent output. */
data class SubtitleTranslationContext(
    val glossaryTerms: List<String> = emptyList(),
    val title: String? = null,
    val genreHint: String? = null,
)

data class SubtitleTranslationProgress(
    val translated: Int,
    val total: Int,
    val stage: SubtitleTranslationStage,
) {
    val percent: Int = if (total <= 0) 0 else ((translated * 100) / total).coerceIn(0, 100)
}

enum class SubtitleTranslationStage {
    Downloading,
    Extracting,
    Parsing,
    CacheLookup,
    Translating,
    Retrying,
    Writing,
    Applying,
    Complete,
}

data class SubtitleTranslationResult(
    val document: SubtitleDocument,
    val translatedCueCount: Int,
    val cacheKey: String,
    val fromCache: Boolean,
    val requestedCueCount: Int = translatedCueCount,
    val untranslatedCount: Int = 0,
) {
    val partial: Boolean get() = untranslatedCount > 0
    val coverage: Float
        get() = if (requestedCueCount <= 0) 1f else translatedCueCount.toFloat() / requestedCueCount
}

sealed interface SubtitleTranslationProviderResult {
    data class Success(
        val translatedByIndex: Map<Int, String>,
        val detectedSourceLanguage: String? = null,
    ) : SubtitleTranslationProviderResult

    data class Failure(
        val message: String,
        val retryable: Boolean = true,
    ) : SubtitleTranslationProviderResult
}

interface SubtitleTranslationProvider {
    val id: SubtitleTranslationProviderId
    val fingerprint: String
    val supportsBatch: Boolean

    suspend fun translate(
        cues: List<SubtitleCue>,
        sourceLanguage: String,
        targetLanguage: String,
        onProgress: (SubtitleTranslationProgress) -> Unit = {},
    ): SubtitleTranslationProviderResult

    /**
     * Context-aware overload. Defaults to the legacy [translate] so existing
     * providers and tests keep compiling; richer providers (AI) override it.
     */
    suspend fun translate(
        cues: List<SubtitleCue>,
        sourceLanguage: String,
        targetLanguage: String,
        context: SubtitleTranslationContext,
        onProgress: (SubtitleTranslationProgress) -> Unit = {},
    ): SubtitleTranslationProviderResult =
        translate(cues, sourceLanguage, targetLanguage, onProgress)
}

fun buildSubtitleTranslationCacheKey(
    providerFingerprint: String,
    sourceIdentity: String,
    sourceLanguage: String,
    targetLanguage: String,
    document: SubtitleDocument,
    bilingual: Boolean = false,
): String {
    val digest = MessageDigest.getInstance("SHA-256")
    fun update(value: String) {
        digest.update(value.toByteArray(Charsets.UTF_8))
        digest.update(0)
    }
    update("schema=" + SUBTITLE_TRANSLATION_CACHE_SCHEMA_VERSION)
    update(providerFingerprint)
    update(sourceIdentity)
    update(sourceLanguage.trim().lowercase())
    update(targetLanguage.trim().lowercase())
    update("bilingual=" + bilingual)
    update(document.format.name)
    document.cues.forEach { cue ->
        update(cue.startMs.toString())
        update(cue.endMs.toString())
        update(cue.text)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
