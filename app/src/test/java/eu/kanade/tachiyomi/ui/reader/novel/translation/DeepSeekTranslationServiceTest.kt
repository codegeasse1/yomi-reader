package eu.kanade.tachiyomi.ui.reader.novel.translation

import eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DeepSeekTranslationServiceTest {

    private val server = MockWebServer()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        server.start()
    }

    @AfterEach
    fun teardown() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    @Test
    fun `parses xml translation from chat completions response`() = runTest {
        val logs = mutableListOf<String>()
        server.enqueue(
            MockResponse().setBody(
                """{"choices":[{"message":{"content":"<s i='0'>Privet</s><s i='1'>Mir</s>"}}]}""",
            ),
        )
        val service = DeepSeekTranslationService(
            client = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true },
            resolveSystemPrompt = { mode, _ ->
                when (mode) {
                    GeminiPromptMode.CLASSIC -> "classic_system"
                    GeminiPromptMode.ADULT_18 -> "adult_system"
                }
            },
        )

        val translated = service.translateBatch(
            segments = listOf("Hello", "World"),
            params = DeepSeekTranslationParams(
                baseUrl = server.url("/").toString().trimEnd('/'),
                apiKey = "test-key",
                model = "deepseek-chat",
                sourceLang = "English",
                targetLang = "Russian",
                promptMode = GeminiPromptMode.ADULT_18,
                promptModifiers = "",
                temperature = 0.7f,
                topP = 0.95f,
                reasoningEffort = "max",
            ),
            onLog = logs::add,
        )

        translated shouldBe listOf("Privet", "Mir")
        logs.joinToString("\n").shouldContain("DeepSeek request:")
        logs.joinToString("\n").shouldContain("reasoningEffort=max")
        logs.joinToString("\n").shouldContain("thinking=enabled")
        logs.joinToString("\n").shouldContain("\"thinking\":{\"type\":\"enabled\"}")
        logs.joinToString("\n").shouldContain("\"reasoning_effort\":\"max\"")
        val request = server.takeRequest()
        request.path shouldBe "/chat/completions"
        val body = request.body.readUtf8()
        body.shouldContain("\"stream\":false")
        body.shouldContain("adult_system")
        body.shouldContain("\"thinking\":{\"type\":\"enabled\"}")
        body.shouldContain("\"reasoning_effort\":\"max\"")
    }

    @Test
    fun `returns null when model is blank`() = runTest {
        val logs = mutableListOf<String>()
        val service = DeepSeekTranslationService(
            client = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true },
            resolveSystemPrompt = { _, _ -> "system" },
        )

        val translated = service.translateBatch(
            segments = listOf("Hello"),
            params = DeepSeekTranslationParams(
                baseUrl = server.url("/").toString().trimEnd('/'),
                apiKey = "test-key",
                model = "",
                sourceLang = "English",
                targetLang = "Russian",
                promptMode = GeminiPromptMode.CLASSIC,
                promptModifiers = "",
                temperature = 0.7f,
                topP = 0.95f,
                reasoningEffort = "none",
            ),
            onLog = logs::add,
        )

        translated.shouldBeNull()
        logs.joinToString("\n").shouldContain("DeepSeek translateBatch skipped: model is blank")
        server.requestCount shouldBe 0
    }

    @Test
    fun `disables thinking when reasoning effort is none`() = runTest {
        val logs = mutableListOf<String>()
        server.enqueue(
            MockResponse().setBody(
                """{"choices":[{"message":{"content":"<s i='0'>Privet</s>"}}]}""",
            ),
        )
        val service = DeepSeekTranslationService(
            client = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true },
            resolveSystemPrompt = { _, _ -> "system" },
        )

        val translated = service.translateBatch(
            segments = listOf("Hello"),
            params = DeepSeekTranslationParams(
                baseUrl = server.url("/").toString().trimEnd('/'),
                apiKey = "test-key",
                model = "deepseek-v4-pro",
                sourceLang = "English",
                targetLang = "Russian",
                promptMode = GeminiPromptMode.CLASSIC,
                promptModifiers = "",
                temperature = 0.7f,
                topP = 0.95f,
                reasoningEffort = "none",
            ),
            onLog = logs::add,
        )

        translated shouldBe listOf("Privet")
        logs.joinToString("\n").shouldContain("reasoningEffort=none")
        logs.joinToString("\n").shouldContain("thinking=disabled")
        val body = server.takeRequest().body.readUtf8()
        body.shouldContain("\"thinking\":{\"type\":\"disabled\"}")
    }

    @Test
    fun `uses larger max tokens for large thinking requests`() = runTest {
        val logs = mutableListOf<String>()
        val segments = List(40) { "x".repeat(100) }
        val translatedContent = segments.indices.joinToString("") { index ->
            "<s i='$index'>Privet</s>"
        }
        server.enqueue(
            MockResponse().setBody(
                """{"choices":[{"message":{"content":"$translatedContent"}}]}""",
            ),
        )
        val service = DeepSeekTranslationService(
            client = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true },
            resolveSystemPrompt = { _, _ -> "system" },
        )

        val translated = service.translateBatch(
            segments = segments,
            params = DeepSeekTranslationParams(
                baseUrl = server.url("/").toString().trimEnd('/'),
                apiKey = "test-key",
                model = "deepseek-v4-flash",
                sourceLang = "English",
                targetLang = "Russian",
                promptMode = GeminiPromptMode.CLASSIC,
                promptModifiers = "",
                temperature = 0.7f,
                topP = 0.95f,
                reasoningEffort = "max",
            ),
            onLog = logs::add,
        )

        translated shouldBe List(40) { "Privet" }
        logs.joinToString("\n").shouldContain("maxTokens=32768")
        val body = server.takeRequest().body.readUtf8()
        body.shouldContain("\"max_tokens\":32768")
    }

    @Test
    fun `logs a token limit hint when deepseek stops at length`() = runTest {
        val logs = mutableListOf<String>()
        server.enqueue(
            MockResponse().setBody(
                """{"choices":[{"finish_reason":"length","message":{"role":"assistant","content":"","reasoning_content":"Thinking..."}}]}""",
            ),
        )
        val service = DeepSeekTranslationService(
            client = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true },
            resolveSystemPrompt = { _, _ -> "system" },
        )

        val translated = service.translateBatch(
            segments = listOf("Hello"),
            params = DeepSeekTranslationParams(
                baseUrl = server.url("/").toString().trimEnd('/'),
                apiKey = "test-key",
                model = "deepseek-v4-flash",
                sourceLang = "English",
                targetLang = "Russian",
                promptMode = GeminiPromptMode.CLASSIC,
                promptModifiers = "",
                temperature = 0.7f,
                topP = 0.95f,
                reasoningEffort = "max",
            ),
            onLog = logs::add,
        )

        translated.shouldBeNull()
        logs.joinToString("\n").shouldContain("finish_reason=length")
        logs.joinToString("\n").shouldContain("hit the token limit before final content")
    }
}
