package eu.kanade.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.theme.AuroraTheme

/**
 * Aniview Premium Glow Effect
 * Adds an outer glow to the composable using shadow or blur effects
 */
fun Modifier.glowEffect(
    color: Color,
    blurRadius: Dp = 16.dp,
    alpha: Float = 0.6f,
    shape: Shape? = null,
): Modifier {
    return this.shadow(
        elevation = blurRadius,
        shape = shape ?: androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        clip = false,
        ambientColor = color.copy(alpha = alpha),
        spotColor = color.copy(alpha = alpha),
    )
}

/**
 * Cyan glow effect for library cards and progress indicators.
 * Uses the theme's glow color by default for accent-awareness.
 */
@Composable
fun Modifier.cyanGlow(
    blurRadius: Dp = 12.dp,
    alpha: Float = 0.15f,
    color: Color = AuroraTheme.colors.glowEffect,
): Modifier = glowEffect(
    color = color,
    blurRadius = blurRadius,
    alpha = alpha,
)

/**
 * Electric blue glow for active elements and buttons.
 * Uses the theme's accent color by default.
 */
@Composable
fun Modifier.electricBlueGlow(
    blurRadius: Dp = 16.dp,
    alpha: Float = 0.6f,
    color: Color = AuroraTheme.colors.accent,
): Modifier = glowEffect(
    color = color,
    blurRadius = blurRadius,
    alpha = alpha,
)

/**
 * Purple glow for premium features.
 * Uses the theme's accent color by default.
 */
@Composable
fun Modifier.purpleGlow(
    blurRadius: Dp = 16.dp,
    alpha: Float = 0.5f,
    color: Color = AuroraTheme.colors.accent,
): Modifier = glowEffect(
    color = color,
    blurRadius = blurRadius,
    alpha = alpha,
)

/**
 * Harem aura glow (Pink/Heart themed)
 */
@Composable
fun Modifier.haremGlow(
    blurRadius: Dp = 14.dp,
    alpha: Float = 0.5f,
): Modifier = glowEffect(
    color = Color(0xFFFF69B4), // HotPink
    blurRadius = blurRadius,
    alpha = alpha,
)

/**
 * Matrix aura glow (Green/Digital themed)
 */
@Composable
fun Modifier.matrixGlow(
    blurRadius: Dp = 14.dp,
    alpha: Float = 0.5f,
): Modifier = glowEffect(
    color = Color(0xFF00FF41), // Matrix Green
    blurRadius = blurRadius,
    alpha = alpha,
)

/**
 * Gradient border glow effect for hero cards
 * Creates a glowing border effect around the composable
 */
internal const val GRADIENT_BORDER_GLOW_LAYER_COUNT = 3

internal data class GradientBorderGlowPass(
    val alpha: Float,
    val expansionPx: Float,
    val offsetPx: Float,
)

internal fun gradientBorderGlowPasses(
    alpha: Float,
    glowRadiusPx: Float,
): List<GradientBorderGlowPass> = (1..GRADIENT_BORDER_GLOW_LAYER_COUNT).map { layer ->
    GradientBorderGlowPass(
        alpha = alpha / (layer * 1.5f),
        expansionPx = glowRadiusPx * layer / 3f,
        offsetPx = glowRadiusPx * layer / 6f,
    )
}

fun Modifier.gradientBorderGlow(
    colors: List<Color>,
    borderWidth: Dp = 2.dp,
    glowRadius: Dp = 12.dp,
    alpha: Float = 0.7f,
    cornerRadius: Dp = 24.dp,
): Modifier = this.drawWithCache {
    val stroke = Stroke(width = borderWidth.toPx())
    val cornerRadiusPx = cornerRadius.toPx()
    val glowRadiusPx = glowRadius.toPx()
    val glowPasses = gradientBorderGlowPasses(
        alpha = alpha,
        glowRadiusPx = glowRadiusPx,
    )
    val brush = Brush.horizontalGradient(colors)

    onDrawBehind {
        glowPasses.forEach { pass ->
            colors.forEach { color ->
                drawRoundRect(
                    color = color.copy(alpha = pass.alpha),
                    style = stroke,
                    size = size.copy(
                        width = size.width + pass.expansionPx,
                        height = size.height + pass.expansionPx,
                    ),
                    topLeft = Offset(
                        x = -pass.offsetPx,
                        y = -pass.offsetPx,
                    ),
                    cornerRadius = CornerRadius(cornerRadiusPx),
                )
            }
        }

        drawRoundRect(
            brush = brush,
            style = stroke,
            cornerRadius = CornerRadius(cornerRadiusPx),
        )
    }
}
