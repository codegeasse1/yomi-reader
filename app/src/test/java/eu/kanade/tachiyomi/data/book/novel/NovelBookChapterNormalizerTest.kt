package eu.kanade.tachiyomi.data.book.novel

import io.kotest.matchers.shouldBe
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test

class NovelBookChapterNormalizerTest {

    @Test
    fun `bakes the chapter title and stamps absolute offsets`() {
        val section = NovelBookChapterNormalizer.normalize(
            rawHtml = "<p>Hello</p><p>World</p>",
            chapterId = 7L,
            chapterName = "Chapter 1",
            startOffset = 100,
        )

        val document = Jsoup.parseBodyFragment(section.html)
        val root = document.selectFirst("section.nb-chapter")!!
        root.id() shouldBe "nb-ch-7"
        root.attr("data-cid") shouldBe "7"
        root.attr("data-start") shouldBe "100"

        val blocks = root.children()
        blocks.size shouldBe 3
        blocks[0].hasClass("nb-title") shouldBe true
        blocks[0].text() shouldBe "Chapter 1"
        blocks[0].attr("data-o") shouldBe "100"
        blocks[0].attr("data-l") shouldBe "9"
        blocks[1].text() shouldBe "Hello"
        blocks[1].attr("data-o") shouldBe "109"
        blocks[1].attr("data-l") shouldBe "5"
        blocks[2].text() shouldBe "World"
        blocks[2].attr("data-o") shouldBe "114"

        section.charCount shouldBe 19
        root.attr("data-len") shouldBe "19"
        section.blockCount shouldBe 3
    }

    @Test
    fun `resolves relative image urls against the chapter url`() {
        val section = NovelBookChapterNormalizer.normalize(
            rawHtml = """
                <p>Before</p>
                <img src="/images/books/1/cover.jpeg" alt="cover">
                <img src="https://cdn.example.org/a.png">
                <img src="//static.example.org/b.png">
                <img src="data:image/png;base64,AAAA" alt="inline">
                <img src="/images/fallback.jpeg" data-src="/images/lazy.jpeg" srcset="/images/x-1x.jpeg 1x, /images/x-2x.jpeg 2x" alt="pic">
                <p>After</p>
            """.trimIndent(),
            chapterId = 7L,
            chapterName = "Chapter 1",
            startOffset = 0,
            chapterWebUrl = "https://ranobe.example/books/4282/chapters/2106814",
        )

        val document = Jsoup.parseBodyFragment(section.html)
        document.selectFirst("img[alt=cover]")!!.attr("src")
            .shouldBe("https://ranobe.example/images/books/1/cover.jpeg")
        document.selectFirst("img[src*=\"cdn.example.org\"]")!!.attr("src")
            .shouldBe("https://cdn.example.org/a.png")
        document.selectFirst("img[src*=\"static.example.org\"]")!!.attr("src")
            .shouldBe("https://static.example.org/b.png")
        document.selectFirst("img[alt=inline]")!!.attr("src")
            .shouldBe("data:image/png;base64,AAAA")
        document.selectFirst("img[alt=pic]")!!.attr("srcset")
            .shouldBe(
                "https://ranobe.example/images/x-1x.jpeg 1x, https://ranobe.example/images/x-2x.jpeg 2x",
            )
        document.selectFirst("img[alt=pic]")!!.attr("src")
            .shouldBe("https://ranobe.example/images/fallback.jpeg")
        document.selectFirst("img[alt=pic]")!!.attr("data-src")
            .shouldBe("https://ranobe.example/images/lazy.jpeg")
    }

    @Test
    fun `offsets are contiguous across chapters`() {
        val first = NovelBookChapterNormalizer.normalize(
            rawHtml = "<p>abc</p>",
            chapterId = 1L,
            chapterName = "One",
            startOffset = 0,
        )
        val second = NovelBookChapterNormalizer.normalize(
            rawHtml = "<p>de</p>",
            chapterId = 2L,
            chapterName = "Two",
            startOffset = first.charCount,
        )

        first.charCount shouldBe 6
        val secondRoot = Jsoup.parseBodyFragment(second.html).selectFirst("section.nb-chapter")!!
        secondRoot.attr("data-start") shouldBe "6"
    }

    @Test
    fun `drops a title duplicated by the source`() {
        val section = NovelBookChapterNormalizer.normalize(
            rawHtml = "<h1>Chapter 12: Rain</h1><p>Body</p>",
            chapterId = 3L,
            chapterName = "Chapter 12: Rain",
            startOffset = 0,
        )

        val blocks = Jsoup.parseBodyFragment(section.html).selectFirst("section.nb-chapter")!!.children()
        blocks.size shouldBe 2
        blocks[0].hasClass("nb-title") shouldBe true
        blocks[1].text() shouldBe "Body"
    }

    @Test
    fun `drops empty paragraphs and trailing breaks so chapters do not leave gaps`() {
        val section = NovelBookChapterNormalizer.normalize(
            rawHtml = "<p>Text</p><p>&nbsp;</p><p><br></p><p><br>Tail<br><br></p><br><br>",
            chapterId = 4L,
            chapterName = "Gaps",
            startOffset = 0,
        )

        val blocks = Jsoup.parseBodyFragment(section.html).selectFirst("section.nb-chapter")!!.children()
        blocks.size shouldBe 3
        blocks[1].text() shouldBe "Text"
        blocks[2].text() shouldBe "Tail"
        blocks[2].html() shouldBe "Tail"
    }

    @Test
    fun `unwraps containers and splits loose text on breaks`() {
        val section = NovelBookChapterNormalizer.normalize(
            rawHtml = "<div><div>First line<br>Second line</div><p>Third</p></div>",
            chapterId = 5L,
            chapterName = "Wrapped",
            startOffset = 0,
        )

        val blocks = Jsoup.parseBodyFragment(section.html).selectFirst("section.nb-chapter")!!.children()
        blocks.drop(1).map { it.text() } shouldBe listOf("First line", "Second line", "Third")
        blocks.drop(1).all { it.tagName() == "p" } shouldBe true
    }

    @Test
    fun `keeps images and rules as one character blocks so offsets stay strictly increasing`() {
        val section = NovelBookChapterNormalizer.normalize(
            rawHtml = "<p><img src=\"a.png\"></p><hr><p>End</p>",
            chapterId = 6L,
            chapterName = "Art",
            startOffset = 0,
        )

        val blocks = Jsoup.parseBodyFragment(section.html).selectFirst("section.nb-chapter")!!.children()
        blocks.size shouldBe 4
        val offsets = blocks.map { it.attr("data-o").toInt() }
        offsets shouldBe offsets.sorted()
        offsets.distinct().size shouldBe offsets.size
        blocks[blocks.size - 1].text() shouldBe "End"
    }

    @Test
    fun `strips scripts and styles`() {
        val section = NovelBookChapterNormalizer.normalize(
            rawHtml = "<script>alert(1)</script><style>p{color:red}</style><p>Safe</p>",
            chapterId = 8L,
            chapterName = "Clean",
            startOffset = 0,
        )

        section.html.contains("alert") shouldBe false
        section.html.contains("<style") shouldBe false
        val root = Jsoup.parseBodyFragment(section.html).selectFirst("section.nb-chapter")!!
        val rootBlocks = root.children()
        rootBlocks[rootBlocks.size - 1].text() shouldBe "Safe"
    }
}
