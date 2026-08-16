package eu.kanade.presentation.easteregg.lattice

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import eu.kanade.domain.easteregg.lattice.LatticeCarrier
import eu.kanade.domain.easteregg.lattice.LatticeProtocolManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Hidden hold node for Lattice carriers (player / manga / novel chrome).
 *
 * Visual (prototype «ether node»): amber core + ~40 golden particles in a soft
 * spiral swirl being drawn into the center — subtle on dark chrome, cinematic up close.
 * Hit target 48dp; glyph field ~28dp.
 */
@Composable
fun LatticeCarrierSlot(carrier: LatticeCarrier) {
    val context = LocalContext.current
    val manager = remember { LatticeProtocolManager.get(context) }
    var visible by remember { mutableStateOf(carrier in manager.visibleCarriers()) }
    if (!visible) return

    val haptics = LocalHapticFeedback.current
    val a11y = stringResource(AYMR.strings.lattice_carrier_node_a11y)
    val reduced = rememberLatticeReducedMotion()
    val scope = rememberCoroutineScope()
    // Slow orbital phase (~0.25 Hz) — not a strobe
    val phase by latticePulse(periodMs = 4000)
    val particles = remember(carrier) { EtherParticle.seed(carrier.ordinal, count = 40) }
    // Hold-to-latch: the dormant terminal charges while held; a full ring latches the carrier.
    val charge = remember { Animatable(0f) }
    val snap = remember { Animatable(0f) }

    Box(
        modifier = Modifier
            .size(48.dp)
            .semantics { contentDescription = a11y }
            .pointerInput(carrier) {
                detectTapGestures(
                    onPress = {
                        var fired = false
                        val job = scope.launch {
                            if (reduced) {
                                delay(650)
                                charge.snapTo(1f)
                            } else {
                                charge.animateTo(1f, tween(650, easing = LinearEasing))
                            }
                            fired = true
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            manager.onCarrierHold(carrier)
                            // Light-cycle snap: one sharp ring flash instead of a toast.
                            if (!reduced) {
                                snap.snapTo(1f)
                                snap.animateTo(0f, tween(340, easing = LinearEasing))
                            }
                            visible = carrier in manager.visibleCarriers()
                            charge.snapTo(0f)
                        }
                        tryAwaitRelease()
                        if (!fired) {
                            job.cancel()
                            scope.launch { charge.animateTo(0f, tween(160, easing = LinearEasing)) }
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(28.dp)) {
            val c = center
            val maxR = size.minDimension * 0.48f
            val p = if (reduced) 0.35f else phase
            val chargeT = charge.value
            val snapT = snap.value

            // Soft ambient halo (keeps node readable on dark chrome)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        LatticeColors.Service.copy(alpha = 0.18f + 0.08f * p),
                        Color.Transparent,
                    ),
                    center = c,
                    radius = maxR * 1.15f,
                ),
                radius = maxR * 1.15f,
                center = c,
            )

            // Particles: spiral inward (ether noise sucked into the node)
            particles.forEach { particle ->
                val spin = p * 2f * PI.toFloat() * particle.speed * (1f + 2.5f * chargeT) + particle.phase
                // radius breathes inward: outer ring → core
                val inhale = if (reduced) {
                    particle.orbit
                } else {
                    // cycle each particle's distance 0.25..1.0 of its orbit
                    val local = ((p * particle.speed + particle.seed) % 1f + 1f) % 1f
                    val pull = 0.22f + 0.78f * (1f - local) // closer as local→1
                    particle.orbit * pull
                }
                val r = maxR * inhale
                val x = c.x + cos(spin) * r
                val y = c.y + sin(spin) * r * 0.92f // slight vertical squash = depth
                val nearCore = 1f - (inhale).coerceIn(0f, 1f)
                val alpha = (0.18f + 0.55f * nearCore + 0.15f * particle.brightness)
                    .coerceIn(0.12f, 0.85f)
                val radiusPx = (0.55f + 1.15f * particle.size * (0.55f + 0.45f * nearCore))
                    .coerceAtLeast(0.5f)
                drawCircle(
                    color = LatticeColors.Service.copy(alpha = alpha),
                    radius = radiusPx,
                    center = Offset(x, y),
                )
            }

            // Amber core + hot center
            val corePulse = 0.85f + 0.15f * (if (reduced) 1f else sin(p * 2f * PI.toFloat()) * 0.5f + 0.5f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFF0C8).copy(alpha = 0.95f * corePulse),
                        LatticeColors.Service.copy(alpha = 0.9f),
                        LatticeColors.Service.copy(alpha = 0.0f),
                    ),
                    center = c,
                    radius = maxR * 0.38f,
                ),
                radius = maxR * 0.38f,
                center = c,
            )
            drawCircle(
                color = Color(0xFFFFE08A).copy(alpha = 0.95f),
                radius = maxR * 0.11f * corePulse,
                center = c,
            )

            // Charge ring: an amber arc closes the circle, tipping into cyan near the latch.
            if (chargeT > 0.01f) {
                drawArc(
                    color = LatticeColors.Service.copy(alpha = 0.85f),
                    startAngle = -90f,
                    sweepAngle = 360f * chargeT,
                    useCenter = false,
                    topLeft = Offset(c.x - maxR, c.y - maxR),
                    size = Size(maxR * 2f, maxR * 2f),
                    style = Stroke(width = 2.5f, cap = StrokeCap.Round),
                )
                if (chargeT > 0.6f) {
                    val tip = (chargeT - 0.6f) / 0.4f
                    drawArc(
                        color = LatticeColors.Signal.copy(alpha = 0.9f * tip),
                        startAngle = -90f + 360f * chargeT - 40f * tip,
                        sweepAngle = 40f * tip,
                        useCenter = false,
                        topLeft = Offset(c.x - maxR, c.y - maxR),
                        size = Size(maxR * 2f, maxR * 2f),
                        style = Stroke(width = 2.5f, cap = StrokeCap.Round),
                    )
                }
            }
            // Light-cycle snap on latch: one sharp expanding ring, then silence.
            if (snapT > 0.01f) {
                drawCircle(
                    color = LatticeColors.Signal.copy(alpha = 0.9f * snapT),
                    radius = maxR * (1.05f + 0.85f * (1f - snapT)),
                    center = c,
                    style = Stroke(width = 1.5f + 2.5f * snapT),
                )
            }
        }
    }
}

/** Deterministic particle for a carrier glyph (no alloc per frame). */
private data class EtherParticle(
    val phase: Float,
    val orbit: Float,
    val speed: Float,
    val size: Float,
    val brightness: Float,
    val seed: Float,
) {
    companion object {
        fun seed(carrierOrdinal: Int, count: Int): List<EtherParticle> {
            val rnd = Random(0x4C41_5454L xor (carrierOrdinal * 0x9E37_79B9L))
            return List(count) {
                EtherParticle(
                    phase = rnd.nextFloat() * 2f * PI.toFloat(),
                    orbit = 0.35f + rnd.nextFloat() * 0.62f,
                    speed = 0.55f + rnd.nextFloat() * 1.15f,
                    size = 0.55f + rnd.nextFloat() * 0.9f,
                    brightness = 0.4f + rnd.nextFloat() * 0.6f,
                    seed = rnd.nextFloat(),
                )
            }
        }
    }
}
