package eu.kanade.presentation.more.onboarding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.more.resolveAuroraMoreCardBorderColor
import eu.kanade.presentation.more.resolveAuroraMoreCardContainerColor
import eu.kanade.presentation.theme.AuroraSurfaceLevel
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.resolveAuroraElevation
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

internal class MediaModesStep : OnboardingStep {

    private val uiPreferences: UiPreferences = Injekt.get()

    private var _isComplete by mutableStateOf(true)

    override val isComplete: Boolean
        get() = _isComplete

    @Composable
    override fun Content() {
        val showAnimePref = uiPreferences.showAnimeSection()
        val showMangaPref = uiPreferences.showMangaSection()
        val showNovelPref = uiPreferences.showNovelSection()

        val showAnime by showAnimePref.collectAsState()
        val showManga by showMangaPref.collectAsState()
        val showNovel by showNovelPref.collectAsState()

        val colors = AuroraTheme.colors

        // Enforce that at least one is complete
        LaunchedEffect(showAnime, showManga, showNovel) {
            _isComplete = showAnime || showManga || showNovel
        }

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(MR.strings.onboarding_media_modes_title),
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
            )

            Text(
                text = stringResource(MR.strings.onboarding_media_modes_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MediaModeCard(
                    title = stringResource(MR.strings.onboarding_media_modes_anime),
                    subtitle = stringResource(MR.strings.onboarding_media_modes_anime_subtitle),
                    icon = Icons.Default.PlayCircle,
                    selected = showAnime,
                    onClick = {
                        val nextVal = !showAnime
                        if (nextVal || showManga || showNovel) {
                            showAnimePref.set(nextVal)
                        }
                    },
                )

                MediaModeCard(
                    title = stringResource(MR.strings.onboarding_media_modes_manga),
                    subtitle = stringResource(MR.strings.onboarding_media_modes_manga_subtitle),
                    icon = Icons.Default.Book,
                    selected = showManga,
                    onClick = {
                        val nextVal = !showManga
                        if (showAnime || nextVal || showNovel) {
                            showMangaPref.set(nextVal)
                        }
                    },
                )

                MediaModeCard(
                    title = stringResource(MR.strings.onboarding_media_modes_novels),
                    subtitle = stringResource(MR.strings.onboarding_media_modes_novels_subtitle),
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    selected = showNovel,
                    onClick = {
                        val nextVal = !showNovel
                        if (showAnime || showManga || nextVal) {
                            showNovelPref.set(nextVal)
                        }
                    },
                )
            }

            if (!_isComplete) {
                Text(
                    text = stringResource(MR.strings.onboarding_media_modes_error),
                    color = colors.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    @Composable
    private fun MediaModeCard(
        title: String,
        subtitle: String,
        icon: ImageVector,
        selected: Boolean,
        onClick: () -> Unit,
    ) {
        val colors = AuroraTheme.colors
        val appHaptics = LocalAppHaptics.current

        // Interactive spring scale states
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.96f else 1f,
            animationSpec = androidx.compose.animation.core.spring(
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                stiffness = androidx.compose.animation.core.Spring.StiffnessLow,
            ),
            label = "media_mode_${title}_scale",
        )

        // Resolve card container and border colors
        val cardBgColor = when {
            colors.isEInk -> if (selected) {
                resolveAuroraMoreCardContainerColor(colors)
            } else {
                resolveAuroraMoreCardContainerColor(colors)
            }
            colors.isDark -> if (selected) {
                colors.accent.copy(alpha = 0.18f)
            } else {
                resolveAuroraMoreCardContainerColor(colors)
            }
            else -> Color.White
        }

        val cardBorderColor = when {
            colors.isEInk -> if (selected) {
                colors.accent
            } else {
                resolveAuroraMoreCardBorderColor(colors)
            }
            colors.isDark -> if (selected) {
                colors.accent.copy(alpha = 0.5f)
            } else {
                null
            }
            else -> if (selected) {
                colors.accent.copy(alpha = 0.35f)
            } else {
                null
            }
        }

        val cardBorder = if (colors.isEInk) {
            BorderStroke(1.dp, cardBorderColor!!)
        } else if (cardBorderColor != null) {
            BorderStroke(1.dp, cardBorderColor)
        } else {
            null
        }

        val cardElevation = if (!colors.isDark && !colors.isEInk && !selected) {
            resolveAuroraElevation(colors, AuroraSurfaceLevel.Glass)
        } else {
            0.dp
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clickable(
                    interactionSource = interactionSource,
                    indication = androidx.compose.foundation.LocalIndication.current,
                    onClick = {
                        appHaptics.tap()
                        onClick()
                    },
                ),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = cardBgColor,
            ),
            border = cardBorder,
            elevation = CardDefaults.cardElevation(
                defaultElevation = cardElevation,
            ),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (selected) colors.accent.copy(alpha = 0.2f) else colors.textPrimary.copy(alpha = 0.05f),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (selected) colors.accent else colors.textSecondary,
                        modifier = Modifier.size(28.dp),
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }

                Checkbox(
                    checked = selected,
                    onCheckedChange = {
                        appHaptics.tap()
                        onClick()
                    },
                    colors = CheckboxDefaults.colors(
                        checkedColor = colors.accent,
                        uncheckedColor = colors.textSecondary,
                        checkmarkColor = colors.textOnAccent,
                    ),
                )
            }
        }
    }
}
