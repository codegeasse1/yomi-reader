package eu.kanade.tachiyomi.data.book.novel

import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.security.MessageDigest
import java.util.Base64

/**
 * Moves inline base64 images out of chapter HTML and into files next to the book artifact.
 *
 * Local .epub/.fb2 readers hand us illustrations as `data:image/...;base64,...` URIs. Keeping them
 * inside `book.body.html` would inflate the merged book by megabytes and make every rendered block
 * carry image payload, so each image is written once into `<artifact>/images/<hash>.<ext>` and the
 * tag is rewired to a `file://` URL. Identical images are deduplicated by content hash, and
 * everything stays fully offline.
 */
object NovelBookImageExtractor {

    const val IMAGES_DIRECTORY_NAME = "images"

    private val DATA_URI_REGEX = Regex("^data:(image/[a-zA-Z0-9.+-]+);base64,(.+)$", RegexOption.DOT_MATCHES_ALL)

    private val IMAGE_ATTRIBUTES = listOf("src", "xlink:href", "href")

    /**
     * Returns [html] with every inline base64 image replaced by a `file://` reference.
     *
     * The original HTML is returned untouched when it holds no inline images, which is the common
     * case for text-only books and keeps the merge loop cheap.
     */
    fun externalize(html: String, artifactDirectory: File): String {
        if (!html.contains("data:image/", ignoreCase = true)) return html

        val imagesDirectory = File(artifactDirectory, IMAGES_DIRECTORY_NAME)
        val document = Jsoup.parse(html, "", Parser.xmlParser())
        var changed = false

        document.select("img, image").forEach { element ->
            val attribute = IMAGE_ATTRIBUTES.firstOrNull { element.hasAttr(it) } ?: return@forEach
            val value = element.attr(attribute)
            val match = DATA_URI_REGEX.matchEntire(value.trim()) ?: return@forEach
            val file = writeImage(
                mimeType = match.groupValues[1],
                base64 = match.groupValues[2],
                imagesDirectory = imagesDirectory,
            ) ?: return@forEach
            element.attr(attribute, file.toURI().toString())
            changed = true
        }

        // Serialize the whole document: chapter HTML arrives as an XML-parsed document, so
        // touching body() would let Jsoup synthesize a wrapper and drop the original structure.
        return if (changed) document.outerHtml() else html
    }

    private fun writeImage(mimeType: String, base64: String, imagesDirectory: File): File? {
        return try {
            val bytes = Base64.getDecoder().decode(base64.filterNot { it.isWhitespace() })
            if (bytes.isEmpty()) return null
            val digest = MessageDigest.getInstance("SHA-1").digest(bytes)
                .joinToString("") { "%02x".format(it) }
            val target = File(imagesDirectory, "$digest.${extensionOf(mimeType)}")
            if (!target.exists() || target.length() != bytes.size.toLong()) {
                imagesDirectory.mkdirs()
                target.writeBytes(bytes)
            }
            target
        } catch (_: Exception) {
            null
        }
    }

    private fun extensionOf(mimeType: String): String = when (mimeType.lowercase()) {
        "image/png" -> "png"
        "image/jpeg", "image/jpg" -> "jpg"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        "image/svg+xml" -> "svg"
        "image/bmp" -> "bmp"
        else -> "img"
    }
}
