package eu.kanade.tachiyomi.ui.player.subtitle.translation

class SubtitleTranslationCoordinator(
    private val providers: Map<SubtitleTranslationProviderId, SubtitleTranslationProvider>,
    private val cache: SubtitleTranslationDiskCache,
    private val maxCoverageRetries: Int = 2,
) {
    suspend fun translate(
        request: SubtitleTranslationRequest,
        onProgress: (SubtitleTranslationProgress) -> Unit = {},
    ): SubtitleTranslationResult {
        val provider = providers[request.providerId]
            ?: error("Subtitle translation provider is not available: ${request.providerId}")

        val translatableCues = request.document.cues.filter { it.text.isNotBlank() }
        onProgress(SubtitleTranslationProgress(0, translatableCues.size, SubtitleTranslationStage.CacheLookup))

        val cacheKey = buildSubtitleTranslationCacheKey(
            providerFingerprint = provider.fingerprint,
            sourceIdentity = request.sourceIdentity,
            sourceLanguage = request.sourceLanguage,
            targetLanguage = request.targetLanguage,
            document = request.document,
            bilingual = request.bilingual,
        )
        if (request.useCache) {
            cache.read(cacheKey)?.let { cached ->
                onProgress(
                    SubtitleTranslationProgress(cached.cues.size, cached.cues.size, SubtitleTranslationStage.Complete),
                )
                return SubtitleTranslationResult(
                    document = cached,
                    translatedCueCount = cached.cues.size,
                    cacheKey = cacheKey,
                    fromCache = true,
                    requestedCueCount = cached.cues.size,
                    untranslatedCount = 0,
                )
            }
        }

        // Mask inline styling so the provider never sees / corrupts ASS or HTML tags.
        val maskedByIndex: Map<Int, SubtitleInlineTagMasker.Masked> =
            translatableCues.associate { it.index to SubtitleInlineTagMasker.mask(it.text) }

        // Deduplicate identical masked lines: translate each unique string once.
        val representativeByText = LinkedHashMap<String, SubtitleCue>()
        translatableCues.forEach { cue ->
            val masked = maskedByIndex.getValue(cue.index)
            representativeByText.getOrPut(masked.text) {
                cue.copy(text = masked.text)
            }
        }
        val representatives = representativeByText.values.toList()

        val context = SubtitleTranslationContext(
            glossaryTerms = SubtitleGlossaryExtractor.extract(translatableCues),
            title = request.title,
            genreHint = request.genreHint,
        )

        onProgress(SubtitleTranslationProgress(0, representatives.size, SubtitleTranslationStage.Translating))

        // Translated text keyed by masked source string.
        val translatedByText = HashMap<String, String>()
        var pending = representatives
        var attempt = 0
        while (pending.isNotEmpty() && attempt <= maxCoverageRetries) {
            if (attempt > 0) {
                onProgress(
                    SubtitleTranslationProgress(
                        translatedByText.size,
                        representatives.size,
                        SubtitleTranslationStage.Retrying,
                    ),
                )
            }
            val providerResult = provider.translate(
                cues = pending,
                sourceLanguage = request.sourceLanguage,
                targetLanguage = request.targetLanguage,
                context = context,
                onProgress = onProgress,
            )
            val translatedByIndex = when (providerResult) {
                is SubtitleTranslationProviderResult.Success -> providerResult.translatedByIndex
                is SubtitleTranslationProviderResult.Failure -> {
                    // Keep whatever we already have; stop only if nothing succeeded yet.
                    if (translatedByText.isEmpty()) error(providerResult.message)
                    break
                }
            }
            pending.forEach { rep ->
                translatedByIndex[rep.index]?.takeIf { it.isNotBlank() }?.let { translatedByText[rep.text] = it }
            }
            pending = pending.filter { !translatedByText.containsKey(it.text) }
            attempt++
        }

        var coveredCount = 0
        var markupFallbackCount = 0
        val translatedCues = request.document.cues.map { cue ->
            if (cue.text.isBlank()) return@map cue
            val masked = maskedByIndex[cue.index] ?: return@map cue
            val translatedMasked = translatedByText[masked.text] ?: return@map cue
            // Restore protected tags. If the model mangled a placeholder, fall back to
            // the translated text with placeholders stripped rather than discarding the
            // whole translation ΓÇö a single broken-markup line must not block caching.
            val restored = SubtitleInlineTagMasker.restore(translatedMasked, masked) ?: run {
                markupFallbackCount++
                SubtitleInlineTagMasker.stripPlaceholders(translatedMasked)
            }
            coveredCount++
            if (request.bilingual) {
                cue.copy(text = cue.text + "\n" + restored)
            } else {
                cue.copy(text = restored)
            }
        }
        val translatedDocument = request.document.copy(cues = translatedCues)
        val untranslatedCount = (translatableCues.size - coveredCount).coerceAtLeast(0)

        onProgress(SubtitleTranslationProgress(coveredCount, translatableCues.size, SubtitleTranslationStage.Writing))
        if (request.useCache && untranslatedCount == 0) {
            // Only cache fully-translated results so partial runs can be retried later.
            cache.write(cacheKey, translatedDocument)
        }
        onProgress(
            SubtitleTranslationProgress(
                translated = translatableCues.size,
                total = translatableCues.size,
                stage = SubtitleTranslationStage.Complete,
            ),
        )

        return SubtitleTranslationResult(
            document = translatedDocument,
            translatedCueCount = coveredCount,
            cacheKey = cacheKey,
            fromCache = false,
            requestedCueCount = translatableCues.size,
            untranslatedCount = untranslatedCount,
        )
    }
}
