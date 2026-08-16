package eu.kanade.tachiyomi.data.book.novel

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.util.Locale

/**
 * Compiles an already normalized book section into pre-parsed native blocks.
 *
 * The input is the exact `<section class="nb-chapter">` HTML that [NovelBookChapterNormalizer]
 * produced and that is stored in `book.body.html`. Compiling from the normalized HTML instead of
 * from the raw chapter HTML is what makes the two representations impossible to drift apart:
 *
 * - offsets are read from `data-o` / `data-l`, they are not recomputed, so the native blocks live in
 *   the same offset domain as the HTML body, the index and every saved reading position;
 * - the chapter heading the normalizer bakes in is kept as a block flagged [isChapterHeading], never
 *   dropped, because dropping it would shift every following offset;
 * - a book that was already built can be migrated offline from its own body file, with no need to
 *   re-download a single chapter.
 */
object NovelBookNativeCompiler {

    /** Compiles one normalized section. Returns an empty list when the section has no blocks. */
    fun compileSection(
        sectionHtml: String,
        chapterId: Long,
        imageReferer: String? = null,
    ): List<NovelBookNativeBlock> {
        if (sectionHtml.isBlank()) return emptyList()
        val document = Jsoup.parseBodyFragment(sectionHtml)
        document.outputSettings().prettyPrint(false)
        val sections = document.body().select("section.${NovelBookChapterNormalizer.CHAPTER_CLASS}")
        val roots = if (sections.isNotEmpty()) sections else listOf(document.body())
        val blocks = mutableListOf<NovelBookNativeBlock>()
        roots.forEach { root ->
            val resolvedChapterId = root
                .attr(NovelBookChapterNormalizer.CHAPTER_ID_ATTR)
                .toLongOrNull()
                ?: chapterId
            root.children().forEach { child ->
                compileBlock(child, resolvedChapterId, imageReferer)?.let(blocks::add)
            }
        }
        return blocks
    }

    private fun compileBlock(
        element: Element,
        chapterId: Long,
        imageReferer: String?,
    ): NovelBookNativeBlock? {
        val charStart = element.attr(NovelBookChapterNormalizer.OFFSET_ATTR).toIntOrNull() ?: return null
        val charLength = element.attr(NovelBookChapterNormalizer.LENGTH_ATTR).toIntOrNull()
            ?: element.text().length.coerceAtLeast(1)
        val tag = element.tagName().lowercase(Locale.US)
        val inlineStyle = element.attr("style")
        val align = parseAlign(inlineStyle)
        val isChapterHeading = element.hasClass(NovelBookChapterNormalizer.TITLE_CLASS)

        val imageElement = resolveImageElement(element)
        if (imageElement != null) {
            val url = resolveImageUrl(imageElement) ?: return null
            return NovelBookNativeBlock(
                chapterId = chapterId,
                charStart = charStart,
                charLength = charLength,
                kind = NovelBookNativeBlockKind.IMAGE,
                align = align,
                imageUrl = url,
                imageAlt = imageElement.attr("alt").trim().ifBlank { null },
                referer = imageReferer?.takeIf { !url.startsWith("file://") },
            )
        }

        if (tag == "hr") {
            return NovelBookNativeBlock(
                chapterId = chapterId,
                charStart = charStart,
                charLength = charLength,
                kind = NovelBookNativeBlockKind.RULE,
            )
        }

        val segments = collectSegments(element)
        if (segments.isEmpty()) return null

        val headingLevel = when {
            tag.length == 2 && tag[0] == 'h' && tag[1].isDigit() -> tag[1].digitToInt()
            else -> 0
        }
        val kind = when {
            headingLevel in 1..6 -> NovelBookNativeBlockKind.HEADING
            tag == "blockquote" -> NovelBookNativeBlockKind.QUOTE
            else -> NovelBookNativeBlockKind.PARAGRAPH
        }

        return NovelBookNativeBlock(
            chapterId = chapterId,
            charStart = charStart,
            charLength = charLength,
            kind = kind,
            level = if (kind == NovelBookNativeBlockKind.HEADING) headingLevel else 0,
            align = align,
            indentEm = if (kind == NovelBookNativeBlockKind.PARAGRAPH) parseIndentEm(inlineStyle) else null,
            isChapterHeading = isChapterHeading,
            segments = segments,
        )
    }

    private fun resolveImageElement(element: Element): Element? {
        val tag = element.tagName().lowercase(Locale.US)
        if (tag == "img") return element
        val image = element.selectFirst("img") ?: return null
        // A paragraph that mixes text and an image keeps its text; only image-only blocks become
        // image blocks, which is what the WebView renderer shows too.
        return if (element.text().isBlank()) image else null
    }

    private fun resolveImageUrl(element: Element): String? {
        listOf("src", "data-src", "data-original", "data-lazy-src", "data-url")
            .asSequence()
            .map { element.attr(it).trim() }
            .firstOrNull { it.isNotBlank() }
            ?.let { return it }
        val srcSet = element.attr("srcset").ifBlank { element.attr("data-srcset") }.trim()
        if (srcSet.isBlank()) return null
        return srcSet.split(',').firstOrNull()?.trim()?.substringBefore(' ')?.takeIf { it.isNotBlank() }
    }

    private fun collectSegments(element: Element): List<NovelBookNativeSegment> {
        val out = mutableListOf<NovelBookNativeSegment>()
        element.childNodes().forEach { node ->
            walkInline(node, NovelBookNativeSegment(t = ""), null, out)
        }
        return mergeAdjacent(out).filter { it.t.isNotEmpty() }
    }

    private fun walkInline(
        node: Node,
        inherited: NovelBookNativeSegment,
        href: String?,
        out: MutableList<NovelBookNativeSegment>,
    ) {
        when (node) {
            is TextNode -> {
                val text = node.wholeText
                if (text.isEmpty()) return
                out += inherited.copy(t = text, href = href)
            }
            is Element -> {
                val tag = node.tagName().lowercase(Locale.US)
                if (tag == "br") {
                    out += inherited.copy(t = "\n", href = href)
                    return
                }
                if (tag == "img" || tag == "picture" || tag == "source") return
                val styled = applyStyle(inherited, tag, node.attr("style"))
                val link = node.attr("href").trim().takeIf { tag == "a" && it.isNotBlank() } ?: href
                node.childNodes().forEach { child -> walkInline(child, styled, link, out) }
            }
            else -> Unit
        }
    }

    private fun applyStyle(
        base: NovelBookNativeSegment,
        tag: String,
        inlineStyle: String,
    ): NovelBookNativeSegment {
        var style = when (tag) {
            "b", "strong" -> base.copy(b = true)
            "i", "em" -> base.copy(i = true)
            "u", "ins" -> base.copy(u = true)
            "s", "strike", "del" -> base.copy(s = true)
            else -> base
        }
        if (inlineStyle.isBlank()) return style
        parseCssMap(inlineStyle).forEach { (key, value) ->
            when (key) {
                "color" -> style = style.copy(color = value)
                "background", "background-color" -> style = style.copy(background = value)
                "font-weight" -> if (value == "bold" || (value.toIntOrNull() ?: 0) >= 600) {
                    style = style.copy(b = true)
                }
                "font-style" -> if (value == "italic" || value == "oblique") style = style.copy(i = true)
                "text-decoration", "text-decoration-line" -> {
                    if (value.contains("underline")) style = style.copy(u = true)
                    if (value.contains("line-through")) style = style.copy(s = true)
                }
            }
        }
        return style
    }

    private fun mergeAdjacent(
        segments: List<NovelBookNativeSegment>,
    ): List<NovelBookNativeSegment> {
        if (segments.isEmpty()) return emptyList()
        val merged = ArrayList<NovelBookNativeSegment>(segments.size)
        segments.forEach { segment ->
            val last = merged.lastOrNull()
            if (last != null && last.copy(t = "") == segment.copy(t = "")) {
                merged[merged.lastIndex] = last.copy(t = last.t + segment.t)
            } else {
                merged += segment
            }
        }
        return merged
    }

    private fun parseAlign(inlineStyle: String): NovelBookNativeAlign? {
        if (inlineStyle.isBlank()) return null
        return when (parseCssMap(inlineStyle)["text-align"]) {
            "left", "start" -> NovelBookNativeAlign.LEFT
            "center" -> NovelBookNativeAlign.CENTER
            "justify" -> NovelBookNativeAlign.JUSTIFY
            "right", "end" -> NovelBookNativeAlign.RIGHT
            else -> null
        }
    }

    private fun parseIndentEm(inlineStyle: String): Float? {
        if (inlineStyle.isBlank()) return null
        val raw = parseCssMap(inlineStyle)["text-indent"] ?: return null
        return when {
            raw.endsWith("em") -> raw.removeSuffix("em").trim().toFloatOrNull()
            raw.endsWith("rem") -> raw.removeSuffix("rem").trim().toFloatOrNull()
            raw.endsWith("px") -> raw.removeSuffix("px").trim().toFloatOrNull()?.div(16f)
            raw.endsWith("pt") -> raw.removeSuffix("pt").trim().toFloatOrNull()?.div(12f)
            raw.endsWith("%") -> raw.removeSuffix("%").trim().toFloatOrNull()?.div(100f)
            else -> null
        }
    }

    private fun parseCssMap(raw: String): Map<String, String> = raw.split(';')
        .mapNotNull { entry ->
            val index = entry.indexOf(':')
            if (index <= 0) return@mapNotNull null
            val key = entry.substring(0, index).trim().lowercase(Locale.US)
            val value = entry.substring(index + 1).trim().lowercase(Locale.US)
            if (key.isBlank() || value.isBlank()) null else key to value
        }
        .toMap()
}
