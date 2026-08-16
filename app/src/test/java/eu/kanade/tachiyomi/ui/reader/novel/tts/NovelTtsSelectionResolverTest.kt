package eu.kanade.tachiyomi.ui.reader.novel.tts

import io.kotest.matchers.shouldBe
import org.junit.Test

class NovelTtsSelectionResolverTest {

    @Test
    fun `voice selection syncs locale from selected voice`() {
        val resolved = resolveNovelTtsVoiceSelection(
            availableVoices = listOf(
                NovelTtsVoiceDescriptor(
                    id = "voice.ru",
                    name = "Anna",
                    localeTag = "ru-RU",
                ),
                NovelTtsVoiceDescriptor(
                    id = "voice.en",
                    name = "Emma",
                    localeTag = "en-US",
                ),
            ),
            availableLocales = listOf("ru-RU", "en-US"),
            capabilities = NovelTtsEngineCapabilities(
                supportsExactWordOffsets = false,
                supportsReliablePauseResume = true,
                supportsVoiceEnumeration = true,
                supportsLocaleEnumeration = true,
            ),
            preferredVoiceId = "voice.en",
            preferredLocaleTag = "ru-RU",
        )

        resolved.selectedVoiceId shouldBe "voice.en"
        resolved.selectedLocaleTag shouldBe "en-US"
        resolved.showLocaleFallback shouldBe false
    }

    @Test
    fun `missing selected voice falls back to system language instead of arbitrary voice`() {
        val resolved = resolveNovelTtsVoiceSelection(
            availableVoices = listOf(
                NovelTtsVoiceDescriptor(
                    id = "voice.ar",
                    name = "Amira",
                    localeTag = "ar-XA",
                ),
                NovelTtsVoiceDescriptor(
                    id = "voice.en",
                    name = "Emma",
                    localeTag = "en-US",
                ),
            ),
            availableLocales = listOf("ar-XA", "en-US"),
            capabilities = NovelTtsEngineCapabilities(
                supportsExactWordOffsets = false,
                supportsReliablePauseResume = true,
                supportsVoiceEnumeration = true,
                supportsLocaleEnumeration = true,
            ),
            preferredVoiceId = "voice.missing",
            preferredLocaleTag = "",
            systemLocaleTag = "en-US",
        )

        resolved.selectedVoiceId shouldBe ""
        resolved.selectedLocaleTag shouldBe "en-US"
    }

    @Test
    fun `no preference and no system language match keeps the engine default`() {
        val resolved = resolveNovelTtsVoiceSelection(
            availableVoices = listOf(
                NovelTtsVoiceDescriptor(
                    id = "voice.ar",
                    name = "Amira",
                    localeTag = "ar-XA",
                ),
            ),
            availableLocales = listOf("ar-XA"),
            capabilities = NovelTtsEngineCapabilities(
                supportsExactWordOffsets = false,
                supportsReliablePauseResume = true,
                supportsVoiceEnumeration = true,
                supportsLocaleEnumeration = true,
            ),
            preferredVoiceId = "",
            preferredLocaleTag = "",
            systemLocaleTag = "en-US",
        )

        resolved.selectedVoiceId shouldBe ""
        resolved.selectedLocaleTag shouldBe ""
    }

    @Test
    fun `missing voice with stored language falls back to a voice of that language`() {
        val resolved = resolveNovelTtsVoiceSelection(
            availableVoices = listOf(
                NovelTtsVoiceDescriptor(
                    id = "voice.ar",
                    name = "Amira",
                    localeTag = "ar-XA",
                ),
                NovelTtsVoiceDescriptor(
                    id = "voice.en",
                    name = "Emma",
                    localeTag = "en-US",
                ),
            ),
            availableLocales = listOf("ar-XA", "en-US"),
            capabilities = NovelTtsEngineCapabilities(
                supportsExactWordOffsets = false,
                supportsReliablePauseResume = true,
                supportsVoiceEnumeration = true,
                supportsLocaleEnumeration = true,
            ),
            preferredVoiceId = "voice.missing",
            preferredLocaleTag = "en-US",
            systemLocaleTag = "ru-RU",
        )

        resolved.selectedVoiceId shouldBe "voice.en"
        resolved.selectedLocaleTag shouldBe "en-US"
    }

    @Test
    fun `locale fallback keeps locale selection when voice enumeration is unavailable`() {
        val resolved = resolveNovelTtsVoiceSelection(
            availableVoices = emptyList(),
            availableLocales = listOf("ru-RU", "en-US"),
            capabilities = NovelTtsEngineCapabilities(
                supportsExactWordOffsets = false,
                supportsReliablePauseResume = true,
                supportsVoiceEnumeration = false,
                supportsLocaleEnumeration = true,
            ),
            preferredVoiceId = "",
            preferredLocaleTag = "ru-RU",
        )

        resolved.selectedVoiceId shouldBe ""
        resolved.selectedLocaleTag shouldBe "ru-RU"
        resolved.showLocaleFallback shouldBe true
    }

    @Test
    fun `blank preferred voice keeps system default voice while preserving locale`() {
        val selection = resolveNovelTtsVoiceSelection(
            availableVoices = listOf(
                NovelTtsVoiceDescriptor(id = "en", name = "English", localeTag = "en-US"),
            ),
            availableLocales = listOf("en-US"),
            capabilities = NovelTtsEngineCapabilities(
                supportsExactWordOffsets = false,
                supportsReliablePauseResume = true,
                supportsVoiceEnumeration = true,
                supportsLocaleEnumeration = true,
            ),
            preferredVoiceId = "",
            preferredLocaleTag = "en-US",
        )

        selection.selectedVoice shouldBe null
        selection.selectedVoiceId shouldBe ""
        selection.selectedLocaleTag shouldBe "en-US"
    }
}
