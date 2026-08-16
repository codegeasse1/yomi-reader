@file:Suppress("ktlint:standard:filename")

package eu.kanade.tachiyomi.ui.reader.novel.tts

import java.util.Locale

data class NovelTtsResolvedVoiceSelection(
    val selectedVoice: NovelTtsVoiceDescriptor?,
    val selectedVoiceId: String,
    val selectedLocaleTag: String,
    val showLocaleFallback: Boolean,
)

/**
 * Resolves the effective voice/locale selection for the TTS engine.
 *
 * The resolver never invents a selection: when the user has no explicit
 * preference, the system language is used if the engine offers it, otherwise
 * a blank tag is returned and the engine default is kept. A missing preferred
 * voice falls back to the preferred language instead of an arbitrary voice
 * from the list (voice lists are name-sorted, so a blind `first()` used to
 * land on Arabic voices and randomly switch the spoken language).
 */
fun resolveNovelTtsVoiceSelection(
    availableVoices: List<NovelTtsVoiceDescriptor>,
    availableLocales: List<String>,
    capabilities: NovelTtsEngineCapabilities,
    preferredVoiceId: String,
    preferredLocaleTag: String,
    systemLocaleTag: String = Locale.getDefault().toLanguageTag(),
): NovelTtsResolvedVoiceSelection {
    val showLocaleFallback = !capabilities.supportsVoiceEnumeration || availableVoices.isEmpty()
    val normalizedPreferredVoiceId = preferredVoiceId.takeIf { it.isNotBlank() }
    val normalizedPreferredLocaleTag = preferredLocaleTag.takeIf { it.isNotBlank() }

    val selectedVoice = if (showLocaleFallback || normalizedPreferredVoiceId == null) {
        null
    } else {
        availableVoices.firstOrNull { it.id == normalizedPreferredVoiceId }
            ?: availableVoices.firstOrNull { voice ->
                normalizedPreferredLocaleTag != null &&
                    voice.localeTag.equals(normalizedPreferredLocaleTag, ignoreCase = true)
            }
    }

    val selectedLocaleTag = when {
        selectedVoice != null -> selectedVoice.localeTag
        normalizedPreferredLocaleTag != null -> normalizedPreferredLocaleTag
        else -> resolveDefaultNovelTtsLocaleTag(availableLocales, systemLocaleTag)
    }

    return NovelTtsResolvedVoiceSelection(
        selectedVoice = selectedVoice,
        selectedVoiceId = selectedVoice?.id.orEmpty(),
        selectedLocaleTag = selectedLocaleTag,
        showLocaleFallback = showLocaleFallback,
    )
}

private fun resolveDefaultNovelTtsLocaleTag(
    availableLocales: List<String>,
    systemLocaleTag: String,
): String {
    if (systemLocaleTag.isBlank()) return ""
    availableLocales.firstOrNull { it.equals(systemLocaleTag, ignoreCase = true) }?.let { return it }
    val systemLanguage = systemLocaleTag.substringBefore('-')
    availableLocales
        .firstOrNull { it.substringBefore('-').equals(systemLanguage, ignoreCase = true) }
        ?.let { return it }
    // No match: keep the engine/system default instead of picking an arbitrary language.
    return ""
}
