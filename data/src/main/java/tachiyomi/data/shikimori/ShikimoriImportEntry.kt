package tachiyomi.data.shikimori

import tachiyomi.data.anixart.AnixartRow

/**
 * One Shikimori list entry prepared for catalogue-source matching.
 */
data class ShikimoriImportEntry(
    val mediaType: ShikimoriImportMediaType,
    val rateId: Long,
    val remoteId: Long,
    val name: String,
    val russian: String?,
    val status: String,
    val score: Int,
    val progress: Int,
    val totalCount: Long?,
    val thumbnailUrl: String?,
    val kind: String? = null,
) {
    fun candidateTitles(): List<String> {
        val raw = buildList {
            add(name)
            add(cleanTitle(name))
            russian?.let {
                add(it)
                add(cleanTitle(it))
            }
        }
        val seen = HashSet<String>()
        return raw
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filter { seen.add(it.lowercase()) }
    }

    fun searchQueries(): List<String> {
        val raw = buildList {
            add(name)
            russian?.let { add(it) }
            add(cleanTitle(name))
            russian?.let { add(cleanTitle(it)) }
        }
        val seen = HashSet<String>()
        return raw
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filter { seen.add(it.lowercase()) }
    }

    private fun cleanTitle(title: String): String {
        val base = AnixartRow.cleanAnimeTitle(title)
        if (mediaType == ShikimoriImportMediaType.ANIME) return base
        return base.replace(TRAILING_MANGA_SUFFIX, "").trim()
    }

    companion object {
        val RANOBE_KINDS = setOf("light_novel", "novel")

        fun isRanobeKind(kind: String?): Boolean = kind?.trim()?.lowercase() in RANOBE_KINDS

        private val TRAILING_MANGA_SUFFIX = Regex(
            """\s+(?:
                том\s+\d+|
                vol\.?\s*\d+|
                volume\s+\d+|
                глава\s+\d+|
                chapter\s+\d+|
                ch\.?\s*\d+
            )\s*$""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.COMMENTS),
        )
    }
}
