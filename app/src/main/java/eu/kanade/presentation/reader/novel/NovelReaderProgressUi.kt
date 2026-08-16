package eu.kanade.presentation.reader.novel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp

internal fun resolveVerticalSeekbarDragProgress(
    currentProgress: Float,
    deltaPx: Float,
    trackHeightPx: Float,
): Float {
    if (trackHeightPx <= 0f) return currentProgress.coerceIn(0f, 1f)
    return (currentProgress + (deltaPx / trackHeightPx)).coerceIn(0f, 1f)
}

@Composable
internal fun LnReaderVerticalSeekbar(
    progress: Float,
    topLabel: String?,
    bottomLabel: String?,
    tickFractions: List<Float> = emptyList(),
    onProgressChange: (Float) -> Unit,
    onProgressChangeFinished: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var containerHeightPx by remember { mutableFloatStateOf(0f) }
    var scrubProgress by remember { mutableFloatStateOf(progress) }
    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()
    val thumbProgress = if (isDragged) scrubProgress else progress
    val trackColor = MaterialTheme.colorScheme.outline
    val progressColor = MaterialTheme.colorScheme.primary
    val containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)

    LaunchedEffect(progress, isDragged) {
        if (!isDragged) {
            scrubProgress = progress
        }
    }

    fun normalizeProgressFromY(y: Float): Float {
        if (containerHeightPx <= 0f) return progress
        val trackTop = containerHeightPx * 0.125f
        val trackBottom = containerHeightPx * 0.875f
        val trackHeight = (trackBottom - trackTop).coerceAtLeast(1f)
        return ((y - trackTop) / trackHeight).coerceIn(0f, 1f)
    }

    Box(
        modifier = modifier
            .background(
                color = containerColor,
                shape = MaterialTheme.shapes.extraLarge,
            )
            .onSizeChanged { containerHeightPx = it.height.toFloat() }
            .pointerInput(containerHeightPx) {
                detectTapGestures { offset ->
                    val newProgress = normalizeProgressFromY(offset.y)
                    scrubProgress = newProgress
                    onProgressChange(newProgress)
                    onProgressChangeFinished(newProgress)
                }
            }
            .draggable(
                orientation = Orientation.Vertical,
                interactionSource = interactionSource,
                state = rememberDraggableState { delta ->
                    val trackHeight = (containerHeightPx * 0.75f).coerceAtLeast(1f)
                    val newProgress = resolveVerticalSeekbarDragProgress(
                        currentProgress = scrubProgress,
                        deltaPx = delta,
                        trackHeightPx = trackHeight,
                    )
                    scrubProgress = newProgress
                    onProgressChange(newProgress)
                },
                onDragStarted = {
                    scrubProgress = progress.coerceIn(0f, 1f)
                },
                onDragStopped = {
                    onProgressChangeFinished(scrubProgress)
                },
            ),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                topLabel?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(6f)
                    .fillMaxWidth(),
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                ) {
                    val centerX = size.width / 2f
                    val trackTop = 0f
                    val trackBottom = size.height
                    val trackWidth = if (isDragged) 1.5.dp.toPx() else 1.dp.toPx()
                    val activeProgress = thumbProgress.coerceIn(0f, 1f)
                    val thumbY = trackTop + ((trackBottom - trackTop) * activeProgress)
                    val thumbRadius = if (isDragged) 5.5.dp.toPx() else 4.dp.toPx()
                    val thumbHaloRadius = if (isDragged) 9.dp.toPx() else 0f
                    val tickRadius = if (isDragged) 1.75.dp.toPx() else 1.5.dp.toPx()

                    drawLine(
                        color = trackColor,
                        start = Offset(centerX, trackTop),
                        end = Offset(centerX, trackBottom),
                        strokeWidth = trackWidth,
                    )
                    drawLine(
                        color = progressColor,
                        start = Offset(centerX, trackTop),
                        end = Offset(centerX, thumbY),
                        strokeWidth = trackWidth,
                    )
                    tickFractions.forEach { tickFraction ->
                        val normalizedTickFraction = tickFraction.coerceIn(0f, 1f)
                        val tickY = trackTop + ((trackBottom - trackTop) * normalizedTickFraction)
                        val tickColor = if (normalizedTickFraction <= activeProgress) {
                            progressColor.copy(alpha = if (isDragged) 0.95f else 0.85f)
                        } else {
                            trackColor.copy(alpha = 0.75f)
                        }
                        drawCircle(
                            color = tickColor,
                            radius = tickRadius,
                            center = Offset(centerX, tickY),
                        )
                    }
                    if (thumbHaloRadius > 0f) {
                        drawCircle(
                            color = progressColor.copy(alpha = 0.18f),
                            radius = thumbHaloRadius,
                            center = Offset(centerX, thumbY),
                        )
                    }
                    drawCircle(
                        color = if (isDragged) progressColor else progressColor.copy(alpha = 0.92f),
                        radius = thumbRadius,
                        center = Offset(centerX, thumbY),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                bottomLabel?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
