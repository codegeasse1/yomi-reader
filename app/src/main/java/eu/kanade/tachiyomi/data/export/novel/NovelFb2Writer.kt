package eu.kanade.tachiyomi.data.export.novel

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.io.File

/** Book level metadata for a generated FB2 document. */
data class NovelFb2Metadata(
    val title: String,
    val bookId: String,
    val author: String? = null,
    val description: String? = null,
    val language: String = "und",
    val genres: List<String> = emptyList(),
    val exportedOn: String? = null,
)

/** A single chapter of a generated FB2 document. */
data class NovelFb2Chapter(
    val id: String,
    val title: String,
    val html: String,
)

/** Summary of what was written into the FB2 file. */
data class NovelFb2WriteReport(
    val chapters: Int,
    val skippedImages: Int,
)

/** A generated FB2 document together with its write report. */
data class NovelFb2Document(
    val xml: String,
    val report: NovelFb2WriteReport,
)

/**
 * Minimal FB2 (FictionBook 2) writer used by the compiled book export flow.
 *
 * The output is deliberately conservative: only the block and inline tags that the
 * app's own FB2 parser understands are emitted, so an exported book can be imported
 * back without losing its structure. Images are dropped, because FB2 requires them to
 * be inlined as base64 `<binary>` payloads, and are reported as skipped instead.
 */
object NovelFb2Writer {

    private const val FB2_NAMESPACE = "http://www.gribuser.ru/xml/fictionbook/2.0"
    private const val XLINK_NAMESPACE = "http://www.w3.org/1999/xlink"
    private const val DEFAULT_GENRE = "prose_contemporary"

    /** Builds the whole FB2 document in memory. */
    fun build(
        metadata: NovelFb2Metadata,
        chapters: List<NovelFb2Chapter>,
    ): NovelFb2Document {
        val sections = StringBuilder()
        var skippedImages = 0
        chapters.forEach { chapter -> skippedImages += appendSection(sections, chapter) }
        if (sections.isEmpty()) {
            sections.append("<section><empty-line/></section>")
        }
        val xml = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append("<FictionBook xmlns=\"").append(FB2_NAMESPACE)
            append("\" xmlns:l=\"").append(XLINK_NAMESPACE).append("\">\n")
            append(buildDescription(metadata))
            append("<body>")
            append("<title><p>").append(escape(metadata.title)).append("</p></title>")
            append(sections)
            append("</body>\n")
            append("</FictionBook>\n")
        }
        return NovelFb2Document(
            xml = xml,
            report = NovelFb2WriteReport(chapters = chapters.size, skippedImages = skippedImages),
        )
    }

    /** Builds the document and stores it as UTF-8 in [file]. */
    fun writeTo(
        file: File,
        metadata: NovelFb2Metadata,
        chapters: List<NovelFb2Chapter>,
    ): NovelFb2WriteReport {
        val document = build(metadata, chapters)
        file.parentFile?.mkdirs()
        file.writeText(document.xml, Charsets.UTF_8)
        return document.report
    }

    private fun buildDescription(metadata: NovelFb2Metadata): String {
        val genres = metadata.genres
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf(DEFAULT_GENRE) }
            .joinToString(separator = "") { "<genre>${escape(it)}</genre>" }
        val author = metadata.author
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "Unknown"
        val annotation = metadata.description
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { "<annotation><p>${escape(it)}</p></annotation>" }
            .orEmpty()
        val date = metadata.exportedOn
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { "<date>${escape(it)}</date>" }
            .orEmpty()
        return buildString {
            append("<description>")
            append("<title-info>")
            append(genres)
            append("<author><nickname>").append(escape(author)).append("</nickname></author>")
            append("<book-title>").append(escape(metadata.title)).append("</book-title>")
            append(annotation)
            append("<lang>").append(escape(metadata.language)).append("</lang>")
            append("</title-info>")
            append("<document-info>")
            append("<author><nickname>").append(escape(author)).append("</nickname></author>")
            append(date)
            append("<id>").append(escape(metadata.bookId)).append("</id>")
            append("<version>1.0</version>")
            append("</document-info>")
            append("</description>\n")
        }
    }

    private fun appendSection(out: StringBuilder, chapter: NovelFb2Chapter): Int {
        val body = Jsoup.parseBodyFragment(chapter.html).body()
        val blocks = StringBuilder()
        var skippedImages = 0
        body.childNodes().forEach { node -> skippedImages += appendBlockNode(blocks, node) }
        out.append("<section id=\"").append(escape(chapter.id)).append("\">")
        val title = chapter.title.trim()
        if (title.isNotBlank()) {
            out.append("<title><p>").append(escape(title)).append("</p></title>")
        }
        if (blocks.isEmpty()) {
            out.append("<empty-line/>")
        } else {
            out.append(blocks)
        }
        out.append("</section>")
        return skippedImages
    }

    private fun appendBlockNode(out: StringBuilder, node: Node): Int {
        return when (node) {
            is TextNode -> {
                val text = node.text().trim()
                if (text.isNotBlank()) {
                    out.append("<p>").append(escape(text)).append("</p>")
                }
                0
            }
            is Element -> appendBlockElement(out, node)
            else -> 0
        }
    }

    private fun appendChildBlocks(out: StringBuilder, element: Element): Int {
        var skippedImages = 0
        element.childNodes().forEach { child -> skippedImages += appendBlockNode(out, child) }
        return skippedImages
    }

    private fun appendBlockElement(out: StringBuilder, element: Element): Int {
        var skippedImages = 0
        when (element.tagName().lowercase()) {
            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                out.append("<subtitle>")
                skippedImages += appendInlineChildren(out, element)
                out.append("</subtitle>")
            }
            "p" -> {
                out.append("<p>")
                skippedImages += appendInlineChildren(out, element)
                out.append("</p>")
            }
            "br", "hr" -> out.append("<empty-line/>")
            "blockquote" -> {
                out.append("<cite>")
                skippedImages += appendChildBlocks(out, element)
                out.append("</cite>")
            }
            "li", "td", "th" -> {
                out.append("<p>")
                skippedImages += appendInlineChildren(out, element)
                out.append("</p>")
            }
            "img" -> skippedImages += 1
            "script", "style", "noscript" -> Unit
            else -> skippedImages += appendMixedElement(out, element)
        }
        return skippedImages
    }

    private fun appendMixedElement(out: StringBuilder, element: Element): Int {
        val hasBlockChildren = element.children().any { child ->
            child.tagName().lowercase() in BLOCK_TAGS
        }
        if (hasBlockChildren) {
            return appendChildBlocks(out, element)
        }
        if (element.text().isBlank() && element.selectFirst("img") == null) {
            return 0
        }
        out.append("<p>")
        val skippedImages = if (element.tagName().lowercase() in INLINE_TAGS) {
            // A bare inline element (a link, emphasis, ...) sitting directly in the body:
            // render the element itself so attributes such as href are not lost.
            appendInlineNode(out, element)
        } else {
            appendInlineChildren(out, element)
        }
        out.append("</p>")
        return skippedImages
    }

    private fun appendInlineChildren(out: StringBuilder, element: Element): Int {
        var skippedImages = 0
        element.childNodes().forEach { child -> skippedImages += appendInlineNode(out, child) }
        return skippedImages
    }

    private fun appendInlineNode(out: StringBuilder, node: Node): Int {
        return when (node) {
            is TextNode -> {
                out.append(escape(node.text()))
                0
            }
            is Element -> when (node.tagName().lowercase()) {
                "em", "i" -> wrapInline(out, node, "emphasis")
                "strong", "b" -> wrapInline(out, node, "strong")
                "del", "s", "strike" -> wrapInline(out, node, "strikethrough")
                "sub" -> wrapInline(out, node, "sub")
                "sup" -> wrapInline(out, node, "sup")
                "code" -> wrapInline(out, node, "code")
                "br" -> {
                    out.append(' ')
                    0
                }
                "img" -> 1
                "a" -> appendLink(out, node)
                "script", "style", "noscript" -> 0
                else -> appendInlineChildren(out, node)
            }
            else -> 0
        }
    }

    private fun wrapInline(out: StringBuilder, element: Element, tag: String): Int {
        out.append('<').append(tag).append('>')
        val skippedImages = appendInlineChildren(out, element)
        out.append("</").append(tag).append('>')
        return skippedImages
    }

    private fun appendLink(out: StringBuilder, element: Element): Int {
        val href = element.attr("href").trim()
        if (href.isBlank() || href.startsWith("#")) {
            return appendInlineChildren(out, element)
        }
        out.append("<a l:href=\"").append(escape(href)).append("\">")
        val skippedImages = appendInlineChildren(out, element)
        out.append("</a>")
        return skippedImages
    }

    private fun escape(text: String): String {
        return text
            .filter { char -> char == '\n' || char == '\r' || char == '\t' || char.code >= 0x20 }
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private val BLOCK_TAGS = setOf(
        "p",
        "div",
        "section",
        "article",
        "h1",
        "h2",
        "h3",
        "h4",
        "h5",
        "h6",
        "blockquote",
        "ul",
        "ol",
        "li",
        "table",
        "thead",
        "tbody",
        "tr",
        "td",
        "th",
        "hr",
        "pre",
    )

    private val INLINE_TAGS = setOf(
        "a",
        "em",
        "i",
        "strong",
        "b",
        "del",
        "s",
        "strike",
        "sub",
        "sup",
        "code",
    )
}
