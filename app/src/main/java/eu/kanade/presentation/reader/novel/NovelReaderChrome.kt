package eu.kanade.presentation.reader.novel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelAutoScrollChapterEndBehavior
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.roundToInt

/**
 * Auto-scroll controls of the reader top chrome: the expandable settings panel plus its
 * expand/collapse chevron.
 *
 * Extracted out of NovelReaderScreen so the root reader composable stays small enough for ART to
 * JIT-compile it, and so slider/switch recompositions stay local to this subtree. Reader state is
 * passed in as plain values and callbacks; this file never reads reader state directly.
 */
@Composable
internal fun NovelReaderAutoScrollPanel(
    expanded: Boolean,
    usePageReader: Boolean,
    autoScrollIntervalSeconds: Int,
    autoScrollAdaptiveDelay: Boolean,
    adaptiveDelayCharacterCount: () -> Int,
    autoScrollSpeed: Int,
    chapterEndBehavior: NovelAutoScrollChapterEndBehavior,
    autoScrollEndPauseMs: Long,
    autoScrollEnabled: Boolean,
    showFloatingButton: Boolean,
    onHapticTap: () -> Unit,
    onIntervalChange: (Int) -> Unit,
    onAdaptiveDelayChange: (Boolean) -> Unit,
    onSpeedChange: (Int) -> Unit,
    onChapterEndBehaviorChange: (NovelAutoScrollChapterEndBehavior) -> Unit,
    onEndPauseMsChange: (Long) -> Unit,
    onToggleAutoScroll: () -> Unit,
    onShowFloatingButtonChange: (Boolean) -> Unit,
    onToggleExpanded: () -> Unit,
) {
    AnimatedVisibility(visible = expanded) {
        // Flat panel matching manga AutoScrollControlsPanel - no nested card.
        val scheme = MaterialTheme.colorScheme
        val isDark = isSystemInDarkTheme()
        val valuePillBg = if (isDark) {
            Color.White.copy(alpha = 0.10f)
        } else {
            Color.Black.copy(alpha = 0.06f)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (usePageReader) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(AYMR.strings.novel_reader_auto_scroll_page_delay),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.primary,
                    )
                    Text(
                        text = stringResource(
                            AYMR.strings.reader_auto_scroll_page_time_fixed,
                            autoScrollIntervalSeconds,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(valuePillBg)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                Slider(
                    value = autoScrollIntervalSeconds.toFloat().coerceIn(2f, 60f),
                    onValueChange = { onIntervalChange(it.roundToInt()) },
                    valueRange = 2f..60f,
                    steps = 58,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onHapticTap()
                            onAdaptiveDelayChange(!autoScrollAdaptiveDelay)
                        }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(AYMR.strings.novel_reader_auto_scroll_adaptive_delay),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = autoScrollAdaptiveDelay,
                        onCheckedChange = {
                            onHapticTap()
                            onAdaptiveDelayChange(it)
                        },
                    )
                }
                if (autoScrollAdaptiveDelay) {
                    Text(
                        text = stringResource(
                            AYMR.strings.reader_auto_scroll_page_time,
                            autoScrollPageDelayMsForCharacterCount(
                                intervalSeconds = autoScrollIntervalSeconds,
                                characterCount = adaptiveDelayCharacterCount(),
                                adaptiveEnabled = true,
                            ) / 1000,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(AYMR.strings.novel_reader_auto_scroll_speed),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.primary,
                    )
                    Text(
                        text = "$autoScrollSpeed",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(valuePillBg)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                Slider(
                    value = autoScrollSpeed.toFloat(),
                    onValueChange = { onSpeedChange(it.roundToInt().coerceIn(1, 100)) },
                    valueRange = 1f..100f,
                    steps = 98,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(
                        AYMR.strings.novel_reader_auto_scroll_chapter_end_behavior,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                )
                var dropdownExpanded by remember { mutableStateOf(false) }
                val behaviorEntries = novelAutoScrollChapterEndBehaviorEntries()
                Box {
                    Text(
                        text = behaviorEntries[chapterEndBehavior] ?: "",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.primary,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(valuePillBg)
                            .clickable { dropdownExpanded = true }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                    ) {
                        behaviorEntries.forEach { (behavior, label) ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                },
                                onClick = {
                                    dropdownExpanded = false
                                    onChapterEndBehaviorChange(behavior)
                                },
                            )
                        }
                    }
                }
            }

            if (chapterEndBehavior != NovelAutoScrollChapterEndBehavior.StopAtEnd) {
                val currentPauseSec = (autoScrollEndPauseMs / 1000L).toInt()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(AYMR.strings.novel_reader_auto_scroll_end_pause),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.primary,
                    )
                    Text(
                        text = stringResource(
                            AYMR.strings.novel_reader_auto_scroll_end_pause_value,
                            currentPauseSec,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(valuePillBg)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                Slider(
                    value = currentPauseSec.toFloat().coerceIn(0f, 10f),
                    onValueChange = {
                        onEndPauseMsChange(it.roundToInt().coerceIn(0, 10) * 1000L)
                    },
                    valueRange = 0f..10f,
                    steps = 10,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (autoScrollEnabled) {
                            scheme.primary
                        } else {
                            scheme.primary.copy(alpha = 0.18f)
                        },
                    )
                    .clickable {
                        onHapticTap()
                        onToggleAutoScroll()
                    }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = if (autoScrollEnabled) {
                        Icons.Outlined.Pause
                    } else {
                        Icons.Outlined.PlayArrow
                    },
                    contentDescription = stringResource(
                        if (autoScrollEnabled) {
                            AYMR.strings.novel_reader_auto_scroll_pause_description
                        } else {
                            AYMR.strings.novel_reader_auto_scroll_play_description
                        },
                    ),
                    tint = if (autoScrollEnabled) scheme.onPrimary else scheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(
                        if (autoScrollEnabled) {
                            MR.strings.action_pause
                        } else {
                            MR.strings.action_start
                        },
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (autoScrollEnabled) scheme.onPrimary else scheme.primary,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        onHapticTap()
                        onShowFloatingButtonChange(!showFloatingButton)
                    }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(AYMR.strings.reader_auto_scroll_floating_button),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = showFloatingButton,
                    onCheckedChange = {
                        onHapticTap()
                        onShowFloatingButtonChange(it)
                    },
                )
            }
        }
    }
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(
            onClick = {
                onHapticTap()
                onToggleExpanded()
            },
        ) {
            Icon(
                imageVector = if (expanded) {
                    Icons.Filled.KeyboardArrowUp
                } else {
                    Icons.Filled.KeyboardArrowDown
                },
                contentDescription = if (expanded) {
                    "Collapse auto-scroll"
                } else {
                    "Expand auto-scroll"
                },
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            )
        }
    }
}
