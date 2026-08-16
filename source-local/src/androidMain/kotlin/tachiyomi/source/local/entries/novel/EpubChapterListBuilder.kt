package tachiyomi.source.local.entries.novel

import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import mihon.core.archive.EpubReader

internal fun buildEpubChaptersFromToc(
    mangaUrl: String,
    chapterFileName: String?,
    chapterFileNameWithoutExtension: String?,
    chapterLastModified: Long,
    tocChapters: List<EpubReader.EpubChapter>,
    spinePageHrefs: List<String> = emptyList(),
    hasMultipleEpubFiles: Boolean,
    chapterNumberOffset: Float = 0f,
): List<SNovelChapter> {
    if (tocChapters.isEmpty() && spinePageHrefs.isEmpty()) return emptyList()

    val emittedUrls = linkedSetOf<String>()
    var chapterNumber = 0
    val chapters = mutableListOf<SNovelChapter>()

    fun addChapter(chapterHref: String, title: String?) {
        val href = chapterHref.trim()
        if (href.isBlank() || !emittedUrls.add(href)) return

        chapterNumber += 1
        val resolvedTitle = title?.trim().orEmpty().ifBlank { "Chapter $chapterNumber" }
        val chapterDisplayName = if (hasMultipleEpubFiles) {
            "${chapterFileNameWithoutExtension.orEmpty()} - $resolvedTitle"
        } else {
            resolvedTitle
        }

        chapters += SNovelChapter.create().apply {
            url = "$mangaUrl/${chapterFileName.orEmpty()}#$href"
            name = chapterDisplayName
            date_upload = chapterLastModified
            chapter_number = chapterNumberOffset + chapterNumber.toFloat()
        }
    }

    if (spinePageHrefs.isEmpty()) {
        tocChapters.forEach { tocEntry ->
            addChapter(tocEntry.href, tocEntry.title)
        }
        return chapters
    }

    // Build a map of spine paths → TOC entries
    val tocByPath = linkedMapOf<String, MutableList<EpubReader.EpubChapter>>()
    tocChapters.forEach { tocEntry ->
        val href = tocEntry.href.trim()
        if (href.isBlank()) return@forEach
        val path = href.substringBefore('#').trim()
        if (path.isBlank()) return@forEach
        tocByPath.getOrPut(path) { mutableListOf() }.add(tocEntry)
    }

    spinePageHrefs.forEach { spineHref ->
        val spinePath = spineHref.substringBefore('#').trim()
        if (spinePath.isBlank()) return@forEach

        val pathTocEntries = tocByPath.remove(spinePath).orEmpty()
        if (pathTocEntries.isNotEmpty()) {
            pathTocEntries.forEach { tocEntry ->
                addChapter(tocEntry.href, tocEntry.title)
            }
            return@forEach
        }

        // Spine page without a matching TOC entry — generate fallback title
        val fallbackTitle = spinePath.substringAfterLast('/')
            .substringBeforeLast('.')
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()

        addChapter(spinePath, fallbackTitle)
    }

    // Any remaining TOC entries not matched to spine pages
    tocByPath.values.flatten().forEach { tocEntry ->
        addChapter(tocEntry.href, tocEntry.title)
    }

    return chapters
}
