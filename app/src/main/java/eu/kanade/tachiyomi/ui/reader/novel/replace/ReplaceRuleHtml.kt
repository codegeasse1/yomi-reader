package eu.kanade.tachiyomi.ui.reader.novel.replace

import org.jsoup.Jsoup
import org.jsoup.nodes.TextNode

/**
 * The class the reader gives the chapter heading it prepends ([eu.kanade.tachiyomi.ui.reader.novel.
 * prependChapterHeadingIfMissing]) — title-scoped rules are applied to its text nodes.
 */
private const val READER_CHAPTER_TITLE_CLASS = "an-reader-chapter-title"

/**
 * Applies [rules] to the text nodes of [rawHtml] and returns the re-serialized document.
 *
 * Only text nodes are touched: tags, attributes and structure are preserved, so replacement
 * patterns can never corrupt markup. The reader chapter heading (if present) receives both
 * title- and content-scoped rules; everything else receives content-scoped rules only.
 * Falls back to the input on any parse/replace failure.
 */
fun applyReplaceRulesToHtml(rawHtml: String, rules: List<ReplaceRule>): String {
    if (rawHtml.isBlank() || rules.isEmpty()) return rawHtml
    val enabled = rules.filter { it.isEnabled && it.isValid() }
    if (enabled.isEmpty()) return rawHtml
    val titleRules = enabled.filter { it.scopeTitle }
    val contentRules = enabled.filter { it.scopeContent }
    if (titleRules.isEmpty() && contentRules.isEmpty()) return rawHtml
    return runCatching {
        val document = Jsoup.parseBodyFragment(rawHtml)
        document.outputSettings().prettyPrint(false)
        document.body().getAllElements().forEach { element ->
            val isReaderHeading = element.tagName() == "h1" && element.hasClass(READER_CHAPTER_TITLE_CLASS)
            val elementRules = if (isReaderHeading) {
                (titleRules + contentRules).distinct()
            } else {
                contentRules
            }
            if (elementRules.isEmpty()) return@forEach
            element.childNodes().forEach { node ->
                if (node is TextNode) {
                    val original = node.getWholeText()
                    val replaced = applyReplaceRulesToText(original, elementRules)
                    if (replaced != original) {
                        node.text(replaced)
                    }
                }
            }
        }
        document.body().html()
    }.getOrDefault(rawHtml)
}
