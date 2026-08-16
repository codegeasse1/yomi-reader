package eu.kanade.presentation.entries.novel.components.aurora

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.kanade.presentation.entries.components.aurora.AuroraCoverSectionHeader
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Header for the chapters section with title and count badge.
 */
@Composable
fun ChaptersHeader(
    chapterCount: Int,
    isBookToc: Boolean = false,
    modifier: Modifier = Modifier,
) {
    AuroraCoverSectionHeader(
        title = if (isBookToc) {
            stringResource(AYMR.strings.novel_book_toc_title)
        } else {
            stringResource(AYMR.strings.aurora_chapters_header)
        },
        icon = Icons.Default.Book,
        count = chapterCount.toString(),
        modifier = modifier,
    )
}
