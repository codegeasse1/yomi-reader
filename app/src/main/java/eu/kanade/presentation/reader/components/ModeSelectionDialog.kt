package eu.kanade.presentation.reader.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics

/**
 * Aurora action footer for the reader mode sheets: optional "revert to default" ghost button plus
 * the accent "apply" pill. Same glass language as the quick settings sections.
 */
@Composable
fun ModeSelectionDialog(
    onApply: () -> Unit,
    onUseDefault: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column {
        content()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            onUseDefault?.let {
                AuroraGhostButton(
                    label = stringResource(MR.strings.action_revert_to_default),
                    onClick = it,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            AuroraAccentButton(
                label = stringResource(MR.strings.action_apply),
                onClick = onApply,
            )
        }
    }
}

@Composable
private fun AuroraGhostButton(
    label: String,
    onClick: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .border(
                width = 1.dp,
                color = colors.textSecondary.copy(alpha = 0.35f),
                shape = shape,
            )
            .clickable {
                appHaptics.tap()
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = colors.textPrimary,
        )
    }
}

@Composable
private fun AuroraAccentButton(
    label: String,
    onClick: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val shape = RoundedCornerShape(999.dp)
    val onAccent = if (colors.isEInk) colors.background else Color.White
    Row(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(colors.accent)
            .clickable {
                appHaptics.tap()
                onClick()
            }
            .padding(horizontal = 18.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Check,
            contentDescription = null,
            tint = onAccent,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = onAccent,
        )
    }
}
