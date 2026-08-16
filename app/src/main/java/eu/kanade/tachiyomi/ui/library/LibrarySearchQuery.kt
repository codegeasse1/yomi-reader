package eu.kanade.tachiyomi.ui.library

data class LibrarySearchQuery(
    val raw: String,
) {
    val id: Long? = raw
        .takeIf { it.startsWith(ID_PREFIX, ignoreCase = true) }
        ?.substringAfter(ID_PREFIX)
        ?.toLongOrNull()

    val terms: List<String> by lazy {
        raw
            .split(',')
            .map { it.trim() }
    }

    companion object {
        private const val ID_PREFIX = "id:"
    }
}
