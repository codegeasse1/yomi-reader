package eu.kanade.presentation.reader.novel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
internal fun NovelReaderAutoScrollEndOverlay(
    visible: Boolean,
    nextChapterName: String?,
    remainingSeconds: Int,
    isEInkMode: Boolean,
    onGoNow: () -> Unit,
    onStop: () -> Unit,
    onStay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = if (isEInkMode) fadeIn(animationSpec = tween(0)) else fadeIn(),
        exit = if (isEInkMode) fadeOut(animationSpec = tween(0)) else fadeOut(),
        modifier = modifier,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp).copy(alpha = 0.96f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
            ),
            tonalElevation = 8.dp,
            shadowElevation = if (isEInkMode) 0.dp else 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = if (isEInkMode) {
                        stringResource(AYMR.strings.novel_reader_auto_scroll_next_static_eink)
                    } else {
                        stringResource(
                            AYMR.strings.novel_reader_auto_scroll_next_countdown,
                            remainingSeconds,
                        )
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
                if (!nextChapterName.isNullOrBlank()) {
                    Text(
                        text = stringResource(
                            AYMR.strings.novel_reader_auto_scroll_next_chapter_named,
                            nextChapterName,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onStay) {
                        Text(text = stringResource(AYMR.strings.novel_reader_auto_scroll_stay_here))
                    }
                    TextButton(onClick = onStop) {
                        Text(text = stringResource(AYMR.strings.novel_reader_auto_scroll_stop_here))
                    }
                    TextButton(onClick = onGoNow) {
                        Text(text = stringResource(AYMR.strings.novel_reader_auto_scroll_go_now))
                    }
                }
            }
        }
    }
}
