package eu.kanade.presentation.reader.novel

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderTapZoneAction
import eu.kanade.tachiyomi.ui.reader.novel.setting.cycleNovelReaderTapZoneAction
import eu.kanade.tachiyomi.ui.reader.novel.setting.parseNovelReaderTapZoneActions
import eu.kanade.tachiyomi.ui.reader.novel.setting.serializeNovelReaderTapZoneActions
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * 3x3 grid editor for the novel reader custom tap zones.
 *
 * Tapping a cell cycles it to the next available action.
 */
@Composable
fun NovelReaderTapZonesEditor(
    serializedActions: String,
    onSerializedActionsChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val actions = remember(serializedActions) { parseNovelReaderTapZoneActions(serializedActions) }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(AYMR.strings.novel_reader_tap_zones_editor_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(12.dp))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp),
                ),
        ) {
            repeat(3) { row ->
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    repeat(3) { col ->
                        val index = row * 3 + col
                        val action = actions[index]
                        val accent = novelReaderTapZoneActionColor(action)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(accent.copy(alpha = 0.10f))
                                .border(
                                    width = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                                )
                                .clickable(enabled = enabled) {
                                    val updated = actions.toMutableList()
                                    updated[index] = cycleNovelReaderTapZoneAction(updated[index])
                                    onSerializedActionsChange(
                                        serializeNovelReaderTapZoneActions(updated),
                                    )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = novelReaderTapZoneActionLabel(action),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                lineHeight = 12.sp,
                                textAlign = TextAlign.Center,
                                color = accent,
                                modifier = Modifier.padding(4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun novelReaderTapZoneActionLabel(action: NovelReaderTapZoneAction): String {
    return when (action) {
        NovelReaderTapZoneAction.NONE ->
            stringResource(AYMR.strings.novel_reader_tap_zone_action_none)
        NovelReaderTapZoneAction.TOGGLE_UI ->
            stringResource(AYMR.strings.novel_reader_tap_zone_action_toggle_ui)
        NovelReaderTapZoneAction.FORWARD ->
            stringResource(AYMR.strings.novel_reader_tap_zone_action_forward)
        NovelReaderTapZoneAction.BACKWARD ->
            stringResource(AYMR.strings.novel_reader_tap_zone_action_backward)
        NovelReaderTapZoneAction.NEXT_CHAPTER ->
            stringResource(AYMR.strings.novel_reader_tap_zone_action_next_chapter)
        NovelReaderTapZoneAction.PREV_CHAPTER ->
            stringResource(AYMR.strings.novel_reader_tap_zone_action_prev_chapter)
    }
}

@Composable
private fun novelReaderTapZoneActionColor(action: NovelReaderTapZoneAction): Color {
    return when (action) {
        NovelReaderTapZoneAction.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
        NovelReaderTapZoneAction.TOGGLE_UI -> MaterialTheme.colorScheme.primary
        NovelReaderTapZoneAction.FORWARD, NovelReaderTapZoneAction.NEXT_CHAPTER ->
            MaterialTheme.colorScheme.tertiary
        NovelReaderTapZoneAction.BACKWARD, NovelReaderTapZoneAction.PREV_CHAPTER ->
            MaterialTheme.colorScheme.secondary
    }
}
