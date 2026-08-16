package eu.kanade.tachiyomi.ui.library.anime

import androidx.compose.runtime.Immutable
import eu.kanade.tachiyomi.source.anime.getNameForAnimeInfo
import eu.kanade.tachiyomi.ui.library.LibrarySearchQuery
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.source.anime.service.AnimeSourceManager

@Immutable
data class AnimeLibraryItem(
    val libraryAnime: LibraryAnime,
    val downloadCount: Long = -1,
    val unseenCount: Long = -1,
    val isLocal: Boolean = false,
    val sourceLanguage: String = "",
) {
    val pinned: Boolean
        get() = libraryAnime.pinned

    /**
     * Checks if a query matches the anime
     *
     * @param query the query to check.
     * @param sourceManager source manager used to resolve the source language name.
     * @return true if the anime matches the query, false otherwise.
     */
    fun matches(query: LibrarySearchQuery, sourceManager: AnimeSourceManager): Boolean {
        val sourceName by lazy { sourceManager.getOrStub(libraryAnime.anime.source).getNameForAnimeInfo() }
        query.id?.let { id -> return libraryAnime.id == id }
        return libraryAnime.anime.title.contains(query.raw, true) ||
            libraryAnime.anime.displayTitle.contains(query.raw, true) ||
            (libraryAnime.anime.author?.contains(query.raw, true) ?: false) ||
            (libraryAnime.anime.artist?.contains(query.raw, true) ?: false) ||
            (libraryAnime.anime.description?.contains(query.raw, true) ?: false) ||
            query.terms.all { subconstraint ->
                checkNegatableConstraint(subconstraint) {
                    sourceName.contains(it, true) ||
                        (libraryAnime.anime.genre?.any { genre -> genre.equals(it, true) } ?: false)
                }
            }
    }

    /**
     * Checks a predicate on a negatable constraint. If the constraint starts with a minus character,
     * the minus is stripped and the result of the predicate is inverted.
     *
     * @param constraint the argument to the predicate. Inverts the predicate if it starts with '-'.
     * @param predicate the check to be run against the constraint.
     * @return !predicate(x) if constraint = "-x", otherwise predicate(constraint)
     */
    private fun checkNegatableConstraint(
        constraint: String,
        predicate: (String) -> Boolean,
    ): Boolean {
        return if (constraint.startsWith("-")) {
            !predicate(constraint.substringAfter("-").trimStart())
        } else {
            predicate(constraint)
        }
    }
}
