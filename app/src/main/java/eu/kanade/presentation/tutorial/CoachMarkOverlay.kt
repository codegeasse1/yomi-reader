package eu.kanade.presentation.tutorial

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Full-screen coach mark overlay. Draws a dim scrim with a rectangular cutout
 * around the active tip's anchor and a card with the tip text.
 *
 * The scrim is composed of a Canvas drawing a dark transparent rectangle with a
 * transparent cutout using BlendMode.Clear. Blocks all background taps and advances
 * the tour on tap.
 */
@Composable
fun CoachMarkOverlay(
    state: CoachMarkState,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tip = state.activeTip ?: return
    val bounds = state.boundsOf(tip.anchor)
    val density = LocalDensity.current
    val scrimColor = Color.Black.copy(alpha = 0.65f)

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val isAnchorInBottomHalf = bounds != null && bounds.center.y > screenHeightPx / 2f

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onNext() },
                )
            },
    ) {
        // Scrim background with cutout
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                },
        ) {
            // Draw full dark scrim
            drawRect(color = scrimColor)

            if (bounds != null) {
                val padPx = 6.dp.toPx()
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = androidx.compose.ui.geometry.Offset(bounds.left - padPx, bounds.top - padPx),
                    size = androidx.compose.ui.geometry.Size(bounds.width + padPx * 2, bounds.height + padPx * 2),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                    blendMode = androidx.compose.ui.graphics.BlendMode.Clear,
                )
            }
        }

        if (bounds != null) {
            val left = with(density) { bounds.left.toDp() }
            val top = with(density) { bounds.top.toDp() }
            val width = with(density) { bounds.width.toDp() }
            val height = with(density) { bounds.height.toDp() }
            val pad = 6.dp

            // Highlight border around the anchor (the "spotlight").
            Box(
                modifier = Modifier
                    .offset(x = left - pad, y = top - pad)
                    .size(width = width + pad * 2, height = height + pad * 2)
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(12.dp),
                    ),
            )
        }

        val cardModifier = if (isAnchorInBottomHalf) {
            Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp)
        } else {
            val bottomPadding = if (state.isBottomBarVisible) 96.dp else 16.dp
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = bottomPadding)
        }

        TipCard(
            tip = tip,
            onNext = onNext,
            onSkip = onSkip,
            modifier = cardModifier,
        )
    }
}

@Composable
private fun TipCard(
    tip: CoachTip,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(tip.titleRes),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(tip.bodyRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onSkip) {
                    Text(text = stringResource(MR.strings.tip_action_skip))
                }
                Button(onClick = onNext) {
                    Text(text = stringResource(MR.strings.tip_action_next))
                }
            }
        }
    }
}
