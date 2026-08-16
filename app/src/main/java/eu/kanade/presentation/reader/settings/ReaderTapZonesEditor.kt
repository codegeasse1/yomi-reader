package eu.kanade.presentation.reader.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.cycleCustomTapZoneToken
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.parseCustomTapZoneTokens
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.serializeCustomTapZoneTokens
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * 3x3 grid editor for the custom tap zones navigation layout. Tapping a cell
 * cycles through the available actions.
 */
@Composable
fun ReaderTapZonesEditor(
    serializedActions: String,
    onSerializedActionsChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = parseCustomTapZoneTokens(serializedActions)
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(MR.strings.custom_tap_zones_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
        ) {
            repeat(3) { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    repeat(3) { column ->
                        val index = row * 3 + column
                        val token = tokens[index]
                        val accent = tapZoneTokenColor(token)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(accent.copy(alpha = 0.1f))
                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                .clickable {
                                    val updated = tokens.toMutableList()
                                    updated[index] = cycleCustomTapZoneToken(token)
                                    onSerializedActionsChange(serializeCustomTapZoneTokens(updated))
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(tapZoneTokenLabel(token)),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = accent,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

fun tapZoneTokenLabel(token: String): StringResource = when (token) {
    "MENU" -> MR.strings.action_menu
    "PREV" -> MR.strings.nav_zone_prev
    "NEXT" -> MR.strings.nav_zone_next
    "LEFT" -> MR.strings.nav_zone_left
    "RIGHT" -> MR.strings.nav_zone_right
    else -> MR.strings.nav_zone_none
}

@Composable
private fun tapZoneTokenColor(token: String): Color = when (token) {
    "MENU" -> MaterialTheme.colorScheme.primary
    "NEXT", "RIGHT" -> MaterialTheme.colorScheme.tertiary
    "PREV", "LEFT" -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
