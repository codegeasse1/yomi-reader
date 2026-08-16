package eu.kanade.presentation.reader.novel

import eu.kanade.tachiyomi.ui.reader.novel.NovelBookPageTurnResult
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelBookEngineResultParserTest {

    @Test
    fun `moved result preserves the renderer character offset`() {
        parseNovelBookPageTurnResult("""{"kind":"moved","charOffset":42}""") shouldBe
            NovelBookPageTurnResult.Moved(42)
    }

    @Test
    fun `android webview quoted json result is unwrapped`() {
        parseNovelBookPageTurnResult(""""{\"kind\":\"moved\",\"charOffset\":17}"""") shouldBe
            NovelBookPageTurnResult.Moved(17)
    }

    @Test
    fun `document boundaries map to explicit page turn results`() {
        parseNovelBookPageTurnResult("""{"kind":"start"}""") shouldBe
            NovelBookPageTurnResult.StartOfDocument
        parseNovelBookPageTurnResult("""{"kind":"end"}""") shouldBe
            NovelBookPageTurnResult.EndOfDocument
    }

    @Test
    fun `missing malformed and unknown results are rejected`() {
        parseNovelBookPageTurnResult(null) shouldBe null
        parseNovelBookPageTurnResult("") shouldBe null
        parseNovelBookPageTurnResult("null") shouldBe null
        parseNovelBookPageTurnResult("undefined") shouldBe null
        parseNovelBookPageTurnResult("{not json") shouldBe null
        parseNovelBookPageTurnResult("""{"kind":"ready","charOffset":1}""") shouldBe null
    }

    @Test
    fun `ready result exposes stabilized pagination and location`() {
        parseNovelBookRendererReady(
            """{"kind":"ready","pageCount":9,"currentPage":3,"charOffset":120,"charCount":900}""",
        ) shouldBe NovelBookRendererReady(
            pageCount = 9,
            currentPage = 3,
            charOffset = 120,
            charCount = 900,
        )
    }

    @Test
    fun `ready parser unwraps webview strings and rejects non ready payloads`() {
        parseNovelBookRendererReady(
            """"{\"kind\":\"ready\",\"pageCount\":4,\"currentPage\":1,\"charOffset\":30,\"charCount\":400}"""",
        ) shouldBe NovelBookRendererReady(
            pageCount = 4,
            currentPage = 1,
            charOffset = 30,
            charCount = 400,
        )
        parseNovelBookRendererReady("""{"kind":"moved","charOffset":30}""") shouldBe null
        parseNovelBookRendererReady("""{"kind":"ready","pageCount":0}""") shouldBe null
    }
}
