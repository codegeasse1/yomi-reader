package eu.kanade.presentation.download

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun DownloadSpeedSparkline(
    points: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0xFF7C5CFC),
    lineWidth: Float = 4f,
) {
    if (points.isEmpty()) return

    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas

        val stepX = size.width / (points.size - 1).coerceAtLeast(1)
        val maxVal = points.max().coerceAtLeast(0.001f)

        val path = Path()
        var lastX = 0f
        var lastY = 0f

        points.forEachIndexed { i, pt ->
            val x = i * stepX
            val y = size.height * (1f - pt / maxVal)
            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
            lastX = x
            lastY = y
        }

        // Draw gradient area fill under the line
        val fillPath = Path().apply {
            addPath(path)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.18f), Color.Transparent),
                startY = 0f,
                endY = size.height,
            ),
        )

        // Draw line stroke
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = lineWidth, cap = StrokeCap.Round),
        )

        // Draw pulsing circle at the end of the line
        drawCircle(
            color = lineColor.copy(alpha = 0.16f),
            radius = 9.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(lastX, lastY),
        )
        drawCircle(
            color = lineColor,
            radius = 4.8f.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(lastX, lastY),
        )
    }
}
