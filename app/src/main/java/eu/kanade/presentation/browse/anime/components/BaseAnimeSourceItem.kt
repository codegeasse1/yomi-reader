package eu.kanade.presentation.browse.anime.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import eu.kanade.presentation.browse.BaseBrowseItem
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.domain.source.anime.model.AnimeSource
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.util.secondaryItemAlpha

@Composable
fun BaseAnimeSourceItem(
    source: AnimeSource,
    modifier: Modifier = Modifier,
    showLanguageInContent: Boolean = true,
    onClickItem: () -> Unit = {},
    onLongClickItem: () -> Unit = {},
    icon: @Composable RowScope.(AnimeSource) -> Unit = defaultIcon,
    action: @Composable RowScope.(AnimeSource) -> Unit = {},
    content: @Composable RowScope.(AnimeSource, String?) -> Unit = defaultContent,
) {
    val sourceLangString = LocaleHelper.getSourceDisplayName(source.lang, LocalContext.current).takeIf {
        showLanguageInContent
    }
    BaseBrowseItem(
        modifier = modifier,
        onClickItem = onClickItem,
        onLongClickItem = onLongClickItem,
        icon = { icon.invoke(this, source) },
        action = { action.invoke(this, source) },
        content = { content.invoke(this, source, sourceLangString) },
    )
}

private val defaultIcon: @Composable RowScope.(AnimeSource) -> Unit = { source ->
    AnimeSourceIcon(source = source)
}

private val defaultContent: @Composable RowScope.(AnimeSource, String?) -> Unit = { source, sourceLangString ->
    Column(
        modifier = Modifier
            .padding(horizontal = MaterialTheme.padding.medium)
            .weight(1f),
    ) {
        Text(
            text = source.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (sourceLangString != null) {
            Text(
                modifier = Modifier.secondaryItemAlpha(),
                text = sourceLangString,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * Generates a unique, stable key for an anime browse item in a lazy grid/list.
 * Pattern matches [eu.kanade.presentation.browse.novel.novelBrowseItemKey].
 *
 * The [url] comes from [tachiyomi.domain.entries.anime.model.Anime.url].
 * It may be null when [androidx.paging.compose.LazyPagingItems] has a placeholder
 * at the given [index] (item not yet loaded).
 * The `#$index` suffix guarantees uniqueness even with duplicate or empty URLs.
 */
internal fun animeBrowseItemKey(url: String?, index: Int): String {
    return "anime/${url.orEmpty()}#$index"
}
