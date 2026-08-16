package eu.kanade.presentation.browse.anime.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import eu.kanade.presentation.browse.BrowseSourceLoadingItem
import eu.kanade.presentation.browse.InLibraryBadge
import eu.kanade.presentation.components.rememberAuroraCoverPlaceholderPainter
import eu.kanade.presentation.library.components.CommonEntryItemDefaults
import eu.kanade.presentation.library.components.EntryCompactGridItem
import eu.kanade.presentation.theme.aurora.adaptive.auroraCenteredMaxWidth
import eu.kanade.presentation.theme.aurora.adaptive.rememberAuroraAdaptiveSpec
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeCover
import tachiyomi.presentation.core.util.plus

@Composable
fun BrowseAnimeSourceCompactGrid(
    animeList: LazyPagingItems<Anime>,
    favoriteAnimeUrls: Set<String>,
    columns: GridCells,
    contentPadding: PaddingValues,
    onAnimeClick: (Anime) -> Unit,
    onAnimeLongClick: (Anime) -> Unit,
) {
    val auroraAdaptiveSpecSpec = rememberAuroraAdaptiveSpec()
    LazyVerticalGrid(
        columns = columns,
        modifier = androidx.compose.ui.Modifier.auroraCenteredMaxWidth(
            auroraAdaptiveSpecSpec.updatesMaxWidthDp ?: auroraAdaptiveSpecSpec.entryMaxWidthDp,
        ),
        contentPadding = contentPadding + PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(CommonEntryItemDefaults.GridVerticalSpacer),
        horizontalArrangement = Arrangement.spacedBy(CommonEntryItemDefaults.GridHorizontalSpacer),
    ) {
        if (animeList.loadState.prepend is LoadState.Loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                BrowseSourceLoadingItem()
            }
        }

        items(
            count = animeList.itemCount,
            key = { index -> animeBrowseItemKey(animeList[index]?.url, index) },
        ) { index ->
            val anime = animeList[index] ?: return@items
            val isFavorite = androidx.compose.runtime.remember(anime.url, favoriteAnimeUrls) {
                anime.url in
                    favoriteAnimeUrls
            }
            BrowseAnimeSourceCompactGridItem(
                anime = anime,
                isFavorite = isFavorite,
                onClick = { onAnimeClick(anime) },
                onLongClick = { onAnimeLongClick(anime) },
            )
        }

        if (animeList.loadState.refresh is LoadState.Loading || animeList.loadState.append is LoadState.Loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                BrowseSourceLoadingItem()
            }
        }
    }
}

@Composable
private fun BrowseAnimeSourceCompactGridItem(
    anime: Anime,
    isFavorite: Boolean,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = onClick,
) {
    val placeholderPainter = rememberAuroraCoverPlaceholderPainter()
    EntryCompactGridItem(
        title = anime.title,
        coverData = AnimeCover(
            animeId = anime.id,
            sourceId = anime.source,
            isAnimeFavorite = isFavorite,
            url = anime.thumbnailUrl,
            lastModified = anime.coverLastModified,
        ),
        coverAlpha = if (isFavorite) CommonEntryItemDefaults.BrowseFavoriteCoverAlpha else 1f,
        coverBadgeStart = {
            InLibraryBadge(enabled = isFavorite)
        },
        errorPainter = placeholderPainter,
        onLongClick = onLongClick,
        onClick = onClick,
    )
}
