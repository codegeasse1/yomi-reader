package tachiyomi.domain.entries.anime.model

import tachiyomi.domain.shikimori.model.ShikimoriMetadata

/**
 * Get the appropriate cover URL based on Shikimori metadata and user preferences.
 */
fun Anime.getCoverUrl(
    shikimoriMetadata: ShikimoriMetadata?,
    useShikimoriCovers: Boolean,
): String {
    return when {
        !useShikimoriCovers -> thumbnailUrl ?: ""
        shikimoriMetadata?.coverUrl != null -> shikimoriMetadata.coverUrl
        else -> thumbnailUrl ?: ""
    }
}
