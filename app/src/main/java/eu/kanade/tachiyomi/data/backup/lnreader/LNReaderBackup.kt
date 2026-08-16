package eu.kanade.tachiyomi.data.backup.lnreader

import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupNovel
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * Reader for backups produced by LNReader (https://github.com/LNReader/lnreader).
 *
 * LNReader is a novel-only app, so every entry of such a backup lands in Yomi's novel section.
 * Two container shapes exist in the wild and both are accepted:
 *
 * - LNReader 1.x: a single JSON document, either a bare array of novels or an object with a
 *   "novels" array. Chapters are nested inside each novel.
 * - LNReader 2.x: a ZIP archive holding one or more JSON documents (novels, chapters, categories).
 *   Chapters are stored in their own collection and joined back to novels by novel id.
 *
 * Field names differ between versions (novelName/name, novelUrl/path, sourceId/pluginId, ...), so
 * every value is read through a tolerant accessor that accepts all known spellings. Unknown fields
 * are ignored rather than treated as errors: a foreign backup must never fail to import because a
 * newer LNReader release added a column.
 */
object LNReaderBackup {

    /** ZIP local file header, "PK\u0003\u0004". */
    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** True when the payload looks like a JSON document or a ZIP archive. */
    fun isLNReaderContainer(bytes: ByteArray): Boolean = isZip(bytes) || isJson(bytes)

    fun isZip(bytes: ByteArray): Boolean =
        bytes.size >= 4 && ZIP_MAGIC.indices.all { bytes[it] == ZIP_MAGIC[it] }

    fun isJson(bytes: ByteArray): Boolean {
        val first = bytes.firstOrNull { !it.toInt().toChar().isWhitespace() } ?: return false
        return first.toInt().toChar() == '{' || first.toInt().toChar() == '['
    }

    /**
     * Decode an LNReader backup into Yomi's model.
     *
     * @throws IllegalArgumentException when the payload is not a readable LNReader backup.
     */
    fun decode(bytes: ByteArray): Backup {
        val isArchive = isZip(bytes)
        val documents = if (isArchive) readZipDocuments(bytes) else listOf(null to parse(bytes))
        require(documents.isNotEmpty()) { "No JSON documents found in LNReader backup" }

        val novelObjects = mutableListOf<JsonObject>()
        val chapterObjects = mutableListOf<JsonObject>()
        val categoryObjects = mutableListOf<JsonObject>()
        val novelCategoryLinks = mutableListOf<JsonObject>()

        documents.forEach { (fileName, document) ->
            when (document) {
                // 1.x: the whole file is the novel list.
                is JsonArray -> when {
                    // 2.x: the archive stores each collection as its own JSON array file.
                    fileName?.startsWith("chapter") == true -> chapterObjects += document.filterIsInstance<JsonObject>()
                    fileName?.startsWith("categor") == true ->
                        categoryObjects +=
                            document.filterIsInstance<JsonObject>()
                    fileName?.contains("categor", ignoreCase = true) == true ->
                        novelCategoryLinks += document.filterIsInstance<JsonObject>()
                    else -> novelObjects += document.filterIsInstance<JsonObject>()
                }
                is JsonObject -> {
                    novelObjects += document.collection("novels", "novel", "library")
                    chapterObjects += document.collection("chapters", "chapter")
                    categoryObjects += document.collection("categories", "category")
                    novelCategoryLinks += document.collection("novelCategories", "novelCategory")
                    // A 2.x export can also ship one file per novel.
                    if (document.looksLikeNovel()) novelObjects += document
                }
                else -> Unit
            }
        }

        require(novelObjects.isNotEmpty()) { "LNReader backup contains no novels" }

        val categories = categoryObjects.toBackupCategories()
        val categoryOrderById = categoryObjects.associate { category ->
            category.long("id", "categoryId") to category.string("name", "categoryName").orEmpty()
        }
        val categoryOrderByName = categories.associate { it.name to it.order }

        val chaptersByNovelId = chapterObjects.groupBy { it.long("novelId", "novel_id", "novelID") }
        val linksByNovelId = novelCategoryLinks.groupBy { it.long("novelId", "novel_id") }

        val novels = novelObjects.mapNotNull { novelObject ->
            novelObject.toBackupNovel(
                chaptersByNovelId = chaptersByNovelId,
                linksByNovelId = linksByNovelId,
                categoryOrderById = categoryOrderById,
                categoryOrderByName = categoryOrderByName,
            )
        }

        require(novels.isNotEmpty()) { "LNReader backup contains no readable novels" }

        val sources = novelObjects
            .mapNotNull { novelObject ->
                val plugin = novelObject.pluginKey() ?: return@mapNotNull null
                BackupSource(name = plugin, sourceId = sourceIdFor(plugin))
            }
            .distinctBy { it.sourceId }

        return Backup(
            backupNovel = novels,
            backupNovelCategories = categories,
            backupNovelSources = sources,
            isLegacy = false,
        )
    }

    /**
     * Stable Yomi source id for an LNReader plugin.
     *
     * LNReader identifies plugins by a string id, Yomi by a 64 bit number, so the id is derived
     * from the plugin name the same way extension sources derive theirs: the top 8 bytes of an MD5
     * digest with the sign bit cleared. The mapping is pure and stable, so re-importing the same
     * backup twice targets the same source and stays idempotent.
     */
    fun sourceIdFor(pluginId: String): Long {
        val key = "lnreader/${pluginId.lowercase()}"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        return (0..7)
            .map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }
            .reduce(Long::or) and Long.MAX_VALUE
    }

    /**
     * Reads every JSON document of a 2.x archive, remembering the file name so a bare array can be
     * routed to the right collection (novels.json, chapters.json, categories.json, ...).
     */
    private fun readZipDocuments(bytes: ByteArray): List<Pair<String?, JsonElement>> {
        val documents = mutableListOf<Pair<String?, JsonElement>>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val name = entry.name.substringAfterLast('/')
                if (!name.endsWith(".json", ignoreCase = true)) continue
                val content = zip.readBytes()
                if (content.isEmpty()) continue
                runCatching { parse(content) }.getOrNull()?.let { documents += name to it }
            }
        }
        return documents
    }

    private fun parse(bytes: ByteArray): JsonElement = json.parseToJsonElement(bytes.decodeToString())

    private fun JsonObject.collection(vararg names: String): List<JsonObject> {
        names.forEach { name ->
            val element = this[name]
            if (element is JsonArray) return element.filterIsInstance<JsonObject>()
        }
        return emptyList()
    }

    private fun JsonObject.looksLikeNovel(): Boolean =
        (containsKey("novelName") || containsKey("name")) &&
            (containsKey("novelUrl") || containsKey("path") || containsKey("url"))

    private fun JsonObject.pluginKey(): String? =
        string("pluginId", "plugin_id", "source", "sourceName")
            ?: long("sourceId", "source_id").takeIf { it != 0L }?.toString()

    private fun List<JsonObject>.toBackupCategories(): List<BackupCategory> {
        return mapIndexedNotNull { index, category ->
            val name = category.string("name", "categoryName")?.takeIf { it.isNotBlank() }
                ?: return@mapIndexedNotNull null
            BackupCategory(
                name = name,
                order = category.long("sort", "order").takeIf { it != 0L } ?: index.toLong(),
            )
        }.distinctBy { it.name }
    }

    private fun JsonObject.toBackupNovel(
        chaptersByNovelId: Map<Long, List<JsonObject>>,
        linksByNovelId: Map<Long, List<JsonObject>>,
        categoryOrderById: Map<Long, String>,
        categoryOrderByName: Map<String, Long>,
    ): BackupNovel? {
        val url = string("novelUrl", "path", "url")?.takeIf { it.isNotBlank() } ?: return null
        val plugin = pluginKey() ?: return null
        val novelId = long("novelId", "id")

        // 2.x keeps chapters in a separate collection; 1.x nests them in the novel.
        val nestedChapters = collection("chapters", "chapter")
        val chapters = nestedChapters.ifEmpty { chaptersByNovelId[novelId].orEmpty() }

        val categoryNames = linksByNovelId[novelId]
            .orEmpty()
            .mapNotNull { categoryOrderById[it.long("categoryId", "category_id")] }
            .ifEmpty { string("categoryIds")?.split(",").orEmpty().mapNotNull { it.trim().takeIf(String::isNotEmpty) } }
        val categories = categoryNames.mapNotNull { categoryOrderByName[it] }.distinct()

        return BackupNovel(
            source = sourceIdFor(plugin),
            url = url,
            title = string("novelName", "name", "title").orEmpty(),
            author = string("author"),
            description = string("novelSummary", "summary", "description"),
            genre = string("genre", "genres")
                .orEmpty()
                .split(",")
                .mapNotNull { it.trim().takeIf(String::isNotEmpty) },
            status = 0,
            thumbnailUrl = string("novelCover", "cover", "thumbnailUrl"),
            // LNReader has no "date added" column; leaving it at 0 lets the restorer keep the value
            // an existing local entry already has instead of inventing one.
            dateAdded = 0,
            chapters = chapters.mapIndexed { index, chapter -> chapter.toBackupChapter(index) },
            categories = categories,
            favorite = bool("inLibrary", "followed", "favorite") ?: true,
        )
    }

    private fun JsonObject.toBackupChapter(index: Int): BackupChapter {
        val read = bool("read")
            // 2.x stores the inverse flag.
            ?: bool("unread")?.not()
            ?: false
        return BackupChapter(
            url = string("chapterUrl", "path", "url").orEmpty(),
            name = string("chapterName", "name", "title").orEmpty(),
            read = read,
            bookmark = bool("bookmark") ?: false,
            lastPageRead = long("progress", "position", "lastPageRead"),
            dateUpload = long("releaseTime", "dateUpload"),
            chapterNumber = double("chapterNumber", "number")?.toFloat() ?: (index + 1).toFloat(),
            sourceOrder = long("position", "sourceOrder").takeIf { it != 0L } ?: index.toLong(),
            // LNReader release dates are free-form strings; keep the raw value so nothing is lost.
            dateUploadRaw = string("releaseDate", "releaseTime"),
        )
    }

    private fun JsonObject.string(vararg names: String): String? {
        names.forEach { name ->
            val primitive = this[name] as? JsonPrimitive ?: return@forEach
            if (primitive.isString) {
                return primitive.content.takeIf { it.isNotBlank() && it != "null" }
            }
            primitive.longOrNull?.let { return it.toString() }
        }
        return null
    }

    private fun JsonObject.long(vararg names: String): Long {
        names.forEach { name ->
            val primitive = this[name] as? JsonPrimitive ?: return@forEach
            primitive.longOrNull?.let { return it }
            if (primitive.isString) primitive.content.toLongOrNull()?.let { return it }
        }
        return 0
    }

    private fun JsonObject.double(vararg names: String): Double? {
        names.forEach { name ->
            val primitive = this[name] as? JsonPrimitive ?: return@forEach
            primitive.doubleOrNull?.let { return it }
            if (primitive.isString) primitive.content.toDoubleOrNull()?.let { return it }
        }
        return null
    }

    private fun JsonObject.bool(vararg names: String): Boolean? {
        names.forEach { name ->
            val primitive = this[name] as? JsonPrimitive ?: return@forEach
            primitive.booleanOrNull?.let { return it }
            // SQLite booleans arrive as 0/1.
            primitive.longOrNull?.let { return it != 0L }
        }
        return null
    }
}
