package eu.kanade.presentation.entries.anime.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import aniyomi.domain.anime.SeasonAnime
import eu.kanade.presentation.entries.components.aurora.auroraSpringClick
import eu.kanade.presentation.theme.AuroraTheme

data class AnimeSeasonSwitcherItem(
    val animeId: Long,
    val label: String,
    val selected: Boolean,
    val seasonAnime: SeasonAnime,
)

fun resolveAnimeSeasonSwitcherItems(
    currentAnimeId: Long?,
    seasons: List<SeasonAnime>,
): List<AnimeSeasonSwitcherItem> {
    val sorted = seasons.sortedBy { it.anime.seasonNumber }
    return sorted.map { season ->
        AnimeSeasonSwitcherItem(
            animeId = season.anime.id,
            label = buildSeasonChipLabel(season),
            selected = season.anime.id == currentAnimeId,
            seasonAnime = season,
        )
    }
}

private fun buildSeasonChipLabel(season: SeasonAnime): String {
    val sn = season.anime.seasonNumber
    return if (sn > 0.0) {
        "S${sn.toInt()}"
    } else {
        "S1"
    }
}

@Composable
fun AnimeSeasonSwitcher(
    items: List<AnimeSeasonSwitcherItem>,
    onSeasonClicked: (SeasonAnime) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.size <= 1) return

    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
    ) {
        items.forEach { item ->
            FilterChip(
                selected = item.selected,
                onClick = { onSeasonClicked(item.seasonAnime) },
                label = { Text(item.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
    }
}

@Composable
fun AnimeSeasonSwitcherAurora(
    items: List<AnimeSeasonSwitcherItem>,
    onSeasonClicked: (SeasonAnime) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.size <= 1) return

    val colors = AuroraTheme.colors
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier.horizontalScroll(scrollState),
    ) {
        items.forEach { item ->
            val activeBgColors = listOf(
                colors.accent.copy(alpha = 0.24f),
                colors.accent.copy(alpha = 0.12f),
            )
            val inactiveBgColors = listOf(
                colors.surface.copy(alpha = 0.4f),
                colors.surface.copy(alpha = 0.15f),
            )
            val currentBgColors = if (item.selected) activeBgColors else inactiveBgColors

            val activeBorderColor = colors.accent.copy(alpha = 0.6f)
            val inactiveBorderColor = colors.divider.copy(alpha = 0.25f)
            val currentBorderColor = if (item.selected) activeBorderColor else inactiveBorderColor

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(brush = Brush.linearGradient(colors = currentBgColors))
                    .border(
                        width = 1.dp,
                        color = currentBorderColor,
                        shape = RoundedCornerShape(100.dp),
                    )
                    .auroraSpringClick { onSeasonClicked(item.seasonAnime) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = item.label,
                    color = if (item.selected) colors.textPrimary else colors.textSecondary,
                    fontSize = 14.sp,
                    fontWeight = if (item.selected) FontWeight.Bold else FontWeight.Medium,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
    }
}
