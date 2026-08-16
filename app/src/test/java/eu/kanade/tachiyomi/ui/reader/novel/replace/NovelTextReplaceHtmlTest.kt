package eu.kanade.tachiyomi.ui.reader.novel.replace

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelTextReplaceHtmlTest {

    private val contentRule = ReplaceRule(
        name = "Цифры прописью",
        pattern = "\\b12\\b",
        replacement = "XII",
        isRegex = true,
        scopeContent = true,
    )

    private val titleRule = ReplaceRule(
        name = "Заголовок",
        pattern = "Гл\\.",
        replacement = "Глава",
        isRegex = true,
        scopeTitle = true,
        scopeContent = false,
    )

    @Test
    fun `replaces text in paragraphs and preserves tags and attributes`() {
        val html = """
            <div id="content">
                <p style="text-align: center">Глава 12. Он прочитал 12 страниц.</p>
                <img src="img-12.png" alt="страница 12"/>
            </div>
        """.trimIndent()

        val replaced = applyReplaceRulesToHtml(html, listOf(contentRule))

        replaced shouldBe """
            <div id="content">
                <p style="text-align: center">Глава XII. Он прочитал XII страниц.</p>
                <img src="img-12.png" alt="страница 12">
            </div>
        """.trimIndent()
    }

    @Test
    fun `reader heading gets title rules and content rules`() {
        val html = """
            <h1 class="an-reader-chapter-title">Гл. 12</h1>
            <p>Текст 12.</p>
        """.trimIndent()

        val replaced = applyReplaceRulesToHtml(html, listOf(titleRule, contentRule))

        replaced shouldBe """
            <h1 class="an-reader-chapter-title">Глава XII</h1>
            <p>Текст XII.</p>
        """.trimIndent()
    }

    @Test
    fun `title rules do not touch body text`() {
        val html = "<h1 class=\"an-reader-chapter-title\">Гл. 1</h1><p>Гл. 2</p>"

        val replaced = applyReplaceRulesToHtml(html, listOf(titleRule))

        replaced shouldBe "<h1 class=\"an-reader-chapter-title\">Глава 1</h1><p>Гл. 2</p>"
    }

    @Test
    fun `plain heading without reader class gets content rules only`() {
        val html = "<h1>Гл. 12</h1><p>Текст 12.</p>"

        val replaced = applyReplaceRulesToHtml(html, listOf(titleRule, contentRule))

        replaced shouldBe "<h1>Гл. XII</h1><p>Текст XII.</p>"
    }

    @Test
    fun `html entities in text nodes are replaced and re-escaped`() {
        val rule = ReplaceRule(pattern = "&", replacement = "и", isRegex = false)
        val html = "<p>Rock &amp; Roll</p>"

        val replaced = applyReplaceRulesToHtml(html, listOf(rule))

        replaced shouldBe "<p>Rock и Roll</p>"
    }

    @Test
    fun `nested markup structure survives replacement`() {
        val rule = ReplaceRule(pattern = "(?iu)король", replacement = "герцог", isRegex = true)
        val html = "<p>Король <b>король</b> <i>король</i> и <a href=\"#король\">король</a></p>"

        val replaced = applyReplaceRulesToHtml(html, listOf(rule))

        replaced shouldBe "<p>герцог <b>герцог</b> <i>герцог</i> и <a href=\"#король\">герцог</a></p>"
    }

    @Test
    fun `both-scoped rule applies to heading exactly once`() {
        val bothRule = ReplaceRule(pattern = "a", replacement = "aa", isRegex = false)
        val html = "<h1 class=\"an-reader-chapter-title\">a</h1><p>a</p>"

        val replaced = applyReplaceRulesToHtml(html, listOf(bothRule))

        replaced shouldBe "<h1 class=\"an-reader-chapter-title\">aa</h1><p>aa</p>"
    }

    @Test
    fun `blank html or empty rules return input unchanged`() {
        applyReplaceRulesToHtml("", listOf(contentRule)) shouldBe ""
        applyReplaceRulesToHtml("<p>текст</p>", emptyList()) shouldBe "<p>текст</p>"
        applyReplaceRulesToHtml("   ", listOf(contentRule)) shouldBe "   "
    }

    @Test
    fun `only enabled valid rules are applied`() {
        val disabled = contentRule.copy(isEnabled = false)
        val invalid = ReplaceRule(pattern = "([x", replacement = "y")
        val html = "<p>12</p>"

        applyReplaceRulesToHtml(html, listOf(disabled, invalid)) shouldBe "<p>12</p>"
    }

    @Test
    fun `disabled rules do not short-circuit later enabled ones`() {
        val first = ReplaceRule(pattern = "a", replacement = "b", isRegex = false, isEnabled = false)
        val second = ReplaceRule(pattern = "b", replacement = "c", isRegex = false)
        val html = "<p>a b</p>"

        applyReplaceRulesToHtml(html, listOf(first, second)) shouldBe "<p>a c</p>"
    }
}
