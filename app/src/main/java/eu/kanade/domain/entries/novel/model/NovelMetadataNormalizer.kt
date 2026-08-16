package eu.kanade.domain.entries.novel.model

import org.jsoup.Jsoup

/**
 * Normalizes a raw novel description without destroying its structure: keeps line breaks and
 * blank-line paragraph separators (the previous `\s+ → " "` collapse turned every description
 * into an unreadable wall of text).
 */
fun normalizeNovelDescription(rawDescription: String?): String? {
    if (rawDescription.isNullOrBlank()) return null

    val sanitized = Jsoup.parse(rawDescription)
        // wholeText keeps whitespace/newlines between block elements (text() collapses them)
        .wholeText()
        .replace('\u00A0', ' ')
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .lines()
        .joinToString("\n") { it.trimEnd() }
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

    return sanitized.ifBlank { null }
}
