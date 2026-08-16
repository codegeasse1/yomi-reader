package eu.kanade.tachiyomi.data.translation

import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class TranslationBatchModelsTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun `profile snapshot survives json roundtrip`() {
        val snapshot = TranslationQueueProfileSnapshot(
            translationProvider = "DEEPSEEK",
            deepSeekModel = "deepseek-v4-flash",
            deepSeekBaseUrl = "https://api.deepseek.com",
            geminiTargetLang = "Russian",
            geminiReasoningEffort = "max",
            geminiTemperature = 1.3f,
        )

        json.decodeFromString<TranslationQueueProfileSnapshot>(
            json.encodeToString(snapshot),
        ) shouldBe snapshot
    }
}
