package eu.kanade.presentation.entries.anime.components.aurora

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.kanade.presentation.entries.components.aurora.AuroraCoverSectionHeader
import eu.kanade.tachiyomi.animesource.model.FetchType
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Header for episodes or seasons section in Aurora theme.
 */
@Composable
fun EpisodesHeader(
    itemCount: Int,
    fetchType: FetchType = FetchType.Episodes,
    modifier: Modifier = Modifier,
) {
    val isRussian =
        androidx.compose.ui.platform.LocalContext.current.resources.configuration.locales[0].language == "ru"
    val titleText = when (fetchType) {
        FetchType.Seasons -> if (isRussian) "Сезоны" else "Seasons"
        FetchType.Episodes -> stringResource(AYMR.strings.aurora_episodes_header)
    }

    AuroraCoverSectionHeader(
        title = titleText,
        icon = Icons.Default.Movie,
        count = itemCount.toString(),
        modifier = modifier,
    )
}
