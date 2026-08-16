package eu.kanade.tachiyomi.ui.player.subtitle.translation

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SubtitleTranslationCoordinatorCoverageTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun cue(index: Int, text: String) =
        SubtitleCue(index = index, startMs = index * 1000L, endMs = index * 1000L + 900, text = text)

    private fun documentOf(vararg cues: SubtitleCue) =
        SubtitleDocument(format = SubtitleFormat.Vtt, cues = cues.toList())

    private fun coordinatorWith(provider: SubtitleTranslationProvider): SubtitleTranslationCoordinator =
        SubtitleTranslationCoordinator(
            providers = mapOf(provider.id to provider),
            cache = SubtitleTranslationDiskCache(tempFolder.newFolder()),
        )

    private fun fakeProvider(
        onSeen: (List<SubtitleCue>) -> Unit = {},
        translate: (SubtitleCue) -> String? = { "${it.text} [ru]" },
    ) = object : SubtitleTranslationProvider {
        override val id = SubtitleTranslationProviderId.Google
        override val fingerprint = "fake-v1"
        override val supportsBatch = true
        override suspend fun translate(
            cues: List<SubtitleCue>,
            sourceLanguage: String,
            targetLanguage: String,
            onProgress: (SubtitleTranslationProgress) -> Unit,
        ): SubtitleTranslationProviderResult {
            onSeen(cues)
            val map = cues.mapNotNull { cue -> translate(cue)?.let { cue.index to it } }.toMap()
            return SubtitleTranslationProviderResult.Success(map)
        }
    }

    @Test
    fun `deduplicates identical lines before translating`() = runTest {
        var seen = 0
        val provider = fakeProvider(onSeen = { seen = it.size })
        val result = coordinatorWith(provider).translate(
            SubtitleTranslationRequest(
                document = documentOf(cue(0, "Hi"), cue(1, "Hi"), cue(2, "Bye")),
                sourceLanguage = "en",
                targetLanguage = "ru",
                providerId = SubtitleTranslationProviderId.Google,
                sourceIdentity = "dedup",
            ),
        )
        assertEquals("only unique lines are sent", 2, seen)
        assertEquals(3, result.translatedCueCount)
        assertEquals("Hi [ru]", result.document.cues[0].text)
        assertEquals("Hi [ru]", result.document.cues[1].text)
        assertFalse(result.partial)
    }

    @Test
    fun `reports partial coverage and keeps originals when cues are missing`() = runTest {
        // Only translate even indexes.
        val provider = fakeProvider(translate = { if (it.index % 2 == 0) "${it.text} [ru]" else null })
        val result = coordinatorWith(provider).translate(
            SubtitleTranslationRequest(
                document = documentOf(cue(0, "A"), cue(1, "B"), cue(2, "C")),
                sourceLanguage = "en",
                targetLanguage = "ru",
                providerId = SubtitleTranslationProviderId.Google,
                sourceIdentity = "partial",
            ),
        )
        assertTrue(result.partial)
        assertEquals(1, result.untranslatedCount)
        assertEquals("A [ru]", result.document.cues[0].text)
        assertEquals("B", result.document.cues[1].text)
        assertEquals("C [ru]", result.document.cues[2].text)
    }
}
