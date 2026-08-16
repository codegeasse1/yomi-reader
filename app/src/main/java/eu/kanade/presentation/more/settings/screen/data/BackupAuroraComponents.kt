package eu.kanade.presentation.more.settings.screen.data

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.more.settings.LocalSettingsUiStyle
import eu.kanade.presentation.more.settings.SettingsUiStyle
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.presentation.core.i18n.stringResource

internal enum class BackupBannerTone { Info, Warning, Error }

private val BackupCardShape = RoundedCornerShape(20.dp)
private val BackupBannerShape = RoundedCornerShape(16.dp)

private fun frostColor(isDark: Boolean): Color =
    if (isDark) Color.White.copy(alpha = 0.07f) else Color.Black.copy(alpha = 0.05f)

private fun rimColor(isDark: Boolean): Color =
    if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f)

/**
 * Small step header used to structure the create/restore flows.
 */
@Composable
internal fun BackupStepHeader(titleRes: StringResource) {
    val isAurora = LocalSettingsUiStyle.current == SettingsUiStyle.Aurora
    val color = if (isAurora) AuroraTheme.colors.textSecondary else MaterialTheme.colorScheme.primary
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 4.dp),
    )
}

/**
 * Aurora glass section card with an M3 fallback for non-Aurora settings styles.
 */
@Composable
internal fun BackupSection(
    titleRes: StringResource? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val isAurora = LocalSettingsUiStyle.current == SettingsUiStyle.Aurora
    if (isAurora) {
        val colors = AuroraTheme.colors
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(BackupCardShape)
                .background(frostColor(colors.isDark))
                .border(1.dp, rimColor(colors.isDark), BackupCardShape)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            if (titleRes != null) {
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(4.dp))
            }
            content()
        }
    } else {
        ElevatedCard(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                if (titleRes != null) {
                    Text(
                        text = stringResource(titleRes),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                content()
            }
        }
    }
}

/**
 * Inline info/warning/error banner with an icon, styled for both Aurora and M3.
 */
@Composable
internal fun BackupStatusBanner(
    tone: BackupBannerTone,
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    val isAurora = LocalSettingsUiStyle.current == SettingsUiStyle.Aurora
    val accentColor = if (isAurora) {
        when (tone) {
            BackupBannerTone.Info -> AuroraTheme.colors.accent
            BackupBannerTone.Warning -> AuroraTheme.colors.warning
            BackupBannerTone.Error -> AuroraTheme.colors.error
        }
    } else {
        when (tone) {
            BackupBannerTone.Info -> MaterialTheme.colorScheme.primary
            BackupBannerTone.Warning -> MaterialTheme.colorScheme.tertiary
            BackupBannerTone.Error -> MaterialTheme.colorScheme.error
        }
    }
    val textColor = if (isAurora) AuroraTheme.colors.textPrimary else MaterialTheme.colorScheme.onSurface
    val icon: ImageVector = when (tone) {
        BackupBannerTone.Info -> Icons.Outlined.Info
        BackupBannerTone.Warning -> Icons.Outlined.WarningAmber
        BackupBannerTone.Error -> Icons.Outlined.ErrorOutline
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(BackupBannerShape)
            .background(accentColor.copy(alpha = 0.10f))
            .border(1.dp, accentColor.copy(alpha = 0.25f), BackupBannerShape)
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                )
                Spacer(Modifier.height(2.dp))
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = textColor,
            )
        }
    }
}

/**
 * Label/value row used in the backup contents preview.
 */
@Composable
internal fun BackupStatRow(label: String, value: String) {
    val isAurora = LocalSettingsUiStyle.current == SettingsUiStyle.Aurora
    val labelColor = if (isAurora) {
        AuroraTheme.colors.textSecondary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val valueColor = if (isAurora) AuroraTheme.colors.textPrimary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = labelColor,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
        )
    }
}

/**
 * Small pill showing the detected backup origin (Yomi / Mihon / legacy Aniyomi).
 */
@Composable
internal fun BackupOriginChip(text: String) {
    val isAurora = LocalSettingsUiStyle.current == SettingsUiStyle.Aurora
    val accent = if (isAurora) AuroraTheme.colors.accent else MaterialTheme.colorScheme.primary
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = accent,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = 0.12f))
            .border(1.dp, accent.copy(alpha = 0.30f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
