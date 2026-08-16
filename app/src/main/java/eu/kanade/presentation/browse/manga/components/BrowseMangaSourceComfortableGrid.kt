package eu.kanade.presentation.browse.manga.components

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
import eu.kanade.presentation.library.components.EntryComfortableGridItem
import eu.kanade.presentation.theme.aurora.adaptive.auroraCenteredMaxWidth
import eu.kanade.presentation.theme.aurora.adaptive.rememberAuroraAdaptiveSpec
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.MangaCover
import tachiyomi.presentation.core.util.plus

@Composable
fun BrowseMangaSourceComfortableGrid(
    mangaList: LazyPagingItems<Manga>,
    favoriteMangaUrls: Set<String>,
    columns: GridCells,
    contentPadding: PaddingValues,
    onMangaClick: (Manga) -> Unit,
    onMangaLongClick: (Manga) -> Unit,
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
        if (mangaList.loadState.prepend is LoadState.Loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                BrowseSourceLoadingItem()
            }
        }

        items(
            count = mangaList.itemCount,
            key = { index -> mangaBrowseItemKey(mangaList[index]?.url, index) },
        ) { index ->
            val manga = mangaList[index] ?: return@items
            val isFavorite = androidx.compose.runtime.remember(manga.url, favoriteMangaUrls) {
                manga.url in
                    favoriteMangaUrls
            }
            BrowseMangaSourceComfortableGridItem(
                manga = manga,
                isFavorite = isFavorite,
                onClick = { onMangaClick(manga) },
                onLongClick = { onMangaLongClick(manga) },
            )
        }

        if (mangaList.loadState.refresh is LoadState.Loading || mangaList.loadState.append is LoadState.Loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                BrowseSourceLoadingItem()
            }
        }
    }
}

@Composable
private fun BrowseMangaSourceComfortableGridItem(
    manga: Manga,
    isFavorite: Boolean,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = onClick,
) {
    val placeholderPainter = rememberAuroraCoverPlaceholderPainter()
    EntryComfortableGridItem(
        title = manga.title,
        coverData = MangaCover(
            mangaId = manga.id,
            sourceId = manga.source,
            isMangaFavorite = isFavorite,
            url = manga.thumbnailUrl,
            lastModified = manga.coverLastModified,
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
