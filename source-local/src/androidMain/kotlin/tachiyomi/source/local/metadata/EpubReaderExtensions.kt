package tachiyomi.source.local.metadata

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import mihon.core.archive.EpubReader
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Fills manga and chapter metadata using this epub file's metadata.
 */
fun EpubReader.fillMetadata(manga: SManga, chapter: SChapter) {
    val ref = getPackageHref()
    val doc = getPackageDocument(ref)

    // Multiple fallback strategies for dc:title
    var title = doc.getElementsByTag("dc:title").firstOrNull()?.text()
    if (title.isNullOrBlank()) {
        title = doc.select("docTitle").firstOrNull()?.text()
    }
    if (title.isNullOrBlank()) {
        title = doc.select("meta[name=title]").firstOrNull()?.attr("content")
    }

    val publisher = doc.getElementsByTag("dc:publisher").firstOrNull()
    val creator = doc.getElementsByTag("dc:creator").firstOrNull()

    // Multiple fallback strategies for dc:description
    var description = doc.getElementsByTag("dc:description").firstOrNull()?.text()
    if (description.isNullOrBlank()) {
        description = doc.select("dc\\:description").firstOrNull()?.text()
    }

    // Extract genres from dc:subject
    val subjects = doc.getElementsByTag("dc:subject").map { it.text() }
    val mappedSubjects = if (subjects.isEmpty()) {
        doc.select("dc\\:subject").map { it.text() }
    } else {
        subjects
    }

    // Extract collection name from EPUB 3 belongs-to-collection
    val collection = doc.select("meta[property=belongs-to-collection]").firstOrNull()?.text()

    var date = doc.getElementsByTag("dc:date").firstOrNull()
    if (date == null) {
        date = doc.select("meta[property=dcterms:modified]").firstOrNull()
    }

    // Set manga title: prefer collection (series name) if manga title is blank
    val currentTitle = runCatching { manga.title }.getOrNull()
    if (!collection.isNullOrBlank() && currentTitle.isNullOrBlank()) {
        manga.title = collection
    } else if (!title.isNullOrBlank() && currentTitle.isNullOrBlank()) {
        manga.title = title
    }

    creator?.text()?.let { manga.author = it }
    description?.let { manga.description = it }

    // Merge genres with existing ones
    if (mappedSubjects.isNotEmpty()) {
        val currentGenres = manga.genre?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
        val allGenres = (currentGenres + mappedSubjects).distinct()
        manga.genre = allGenres.joinToString(", ")
    }

    title?.let { if (it.isNotBlank()) chapter.name = it }

    if (publisher != null) {
        chapter.scanlator = publisher.text()
    } else if (creator != null) {
        chapter.scanlator = creator.text()
    }

    if (date != null) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault())
        try {
            val parsedDate = dateFormat.parse(date.text())
            if (parsedDate != null) {
                chapter.date_upload = parsedDate.time
            }
        } catch (e: ParseException) {
            // Empty
        }
    }
}
