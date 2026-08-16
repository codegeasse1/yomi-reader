package eu.kanade.presentation.browse.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import eu.kanade.presentation.theme.AuroraTheme

/** Tone of an extension status banner. */
enum class ExtensionBannerTone { Info, Warning, Error }

@Composable
private fun auroraFrostColor(): Color {
    return if (AuroraTheme.colors.isDark) {
        Color.White.copy(alpha = 0.07f)
    } else {
        Color.Black.copy(alpha = 0.05f)
    }
}

@Composable
private fun auroraRimColor(): Color {
    return if (AuroraTheme.colors.isDark) {
        Color.White.copy(alpha = 0.10f)
    } else {
        Color.Black.copy(alpha = 0.08f)
    }
}

/** Aurora glass container used by the extension details screens. */
@Composable
fun ExtensionDetailsGlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(auroraFrostColor(), shape)
            .border(1.dp, auroraRimColor(), shape),
        content = content,
    )
}

@Composable
private fun ExtensionBannerTone.color(): Color {
    val colors = AuroraTheme.colors
    return when (this) {
        ExtensionBannerTone.Info -> colors.accent
        ExtensionBannerTone.Warning -> colors.warning
        ExtensionBannerTone.Error -> colors.error
    }
}

/**
 * Aurora status banner for a problematic extension (obsolete, requires
 * reinstall from another store, update available) with an optional action.
 */
@Composable
fun ExtensionStatusBanner(
    icon: ImageVector,
    title: String,
    message: String,
    tone: ExtensionBannerTone,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    actionEnabled: Boolean = true,
    onAction: (() -> Unit)? = null,
) {
    val toneColor = tone.color()
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(toneColor.copy(alpha = 0.12f), shape)
            .border(1.dp, toneColor.copy(alpha = 0.35f), shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = toneColor,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = AuroraTheme.colors.textPrimary,
            )
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = AuroraTheme.colors.textSecondary,
        )
        if (actionLabel != null && onAction != null) {
            ExtensionAuroraButton(
                text = actionLabel,
                onClick = onAction,
                enabled = actionEnabled,
                accent = toneColor,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Pill glass button used on the extension details screens. */
@Composable
fun ExtensionAuroraButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color = AuroraTheme.colors.accent,
) {
    val shape = RoundedCornerShape(21.dp)
    val background = if (enabled) accent.copy(alpha = 0.16f) else auroraFrostColor()
    val border = if (enabled) accent.copy(alpha = 0.4f) else auroraRimColor()
    val textColor = if (enabled) accent else AuroraTheme.colors.textSecondary
    Row(
        modifier = modifier
            .height(42.dp)
            .clip(shape)
            .background(background, shape)
            .border(1.dp, border, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
        )
    }
}
