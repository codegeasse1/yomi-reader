package eu.kanade.presentation.easteregg.lattice

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import eu.kanade.domain.easteregg.lattice.DIRS
import eu.kanade.domain.easteregg.lattice.LatticeBoard
import eu.kanade.domain.easteregg.lattice.LatticeCircuit
import eu.kanade.domain.easteregg.lattice.LatticeSegment
import kotlinx.coroutines.launch
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * 1:1 port of `lattice-obsidian-track.html` (drawGuide / drawPort / drawTile / plate / link /
 * drawBridges / drawCore / drawRacer / drawSpark).
 *
 * Geometry note: the board is laid out exactly like the prototype - pointy-top plates whose flat
 * edges face their neighbours, centres at cpos(q, r) and connectors at polar(60 * dir). Plate
 * rotation is therefore plain `rotation * 60` with no extra tilt, so the plates tile perfectly.
 */
@Composable
fun LatticeObsidianBoardCanvas(
    board: LatticeBoard,
    circuit: LatticeCircuit,
    enabled: Boolean,
    spinCell: Pair<Int, Int>?,
    spinProgress: Float,
    revealProgress: Float,
    fuseProgress: Float,
    desyncLevel: Float,
    animateFlow: Boolean,
    dialCell: Pair<Int, Int>?,
    dialAngle: Float,
    commitCharge: Float = 0f,
    onDialStart: (Int, Int) -> Unit,
    onDialDelta: (Float) -> Unit,
    onDialEnd: () -> Unit,
    modifier: Modifier = Modifier,
    onTap: (Int, Int) -> Unit,
) {
    val a11y = stringResource(AYMR.strings.lattice_board_a11y)
    val reduced = rememberLatticeReducedMotion()
    val time by rememberLatticeTimeSeconds(active = !reduced)
    val scope = rememberCoroutineScope()

    var spark by remember { mutableStateOf<Spark?>(null) }
    var winT0 by remember { mutableFloatStateOf(-99f) }
    LaunchedEffect(circuit.closed) {
        if (circuit.closed) {
            if (winT0 < 0f) winT0 = time
        } else {
            winT0 = -99f
        }
    }

    Canvas(
        modifier = modifier
            .semantics { contentDescription = a11y }
            .pointerInput(enabled, board) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val s = boardScale(size.width.toFloat(), size.height.toFloat(), board.radius)
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    var best: Pair<Int, Int>? = null
                    var bestDist = s * 1.05f
                    board.cells.keys.forEach { (q, r) ->
                        val c = boardCenter(cx, cy, s, q, r)
                        val d = hypot(down.position.x - c.x, down.position.y - c.y)
                        if (d < bestDist) {
                            bestDist = d
                            best = q to r
                        }
                    }
                    val hit = best ?: return@awaitEachGesture
                    val pivot = boardCenter(cx, cy, s, hit.first, hit.second)
                    var lastDeg = atan2(down.position.y - pivot.y, down.position.x - pivot.x) * RAD_TO_DEG
                    var moved = 0f
                    val start = down.position
                    var lastPos = start
                    onDialStart(hit.first, hit.second)
                    drag(down.id) { change ->
                        change.consume()
                        lastPos = change.position
                        val deg = atan2(
                            change.position.y - pivot.y,
                            change.position.x - pivot.x,
                        ) * RAD_TO_DEG
                        var delta = deg - lastDeg
                        while (delta > 180f) delta -= 360f
                        while (delta < -180f) delta += 360f
                        lastDeg = deg
                        moved += abs(delta)
                        onDialDelta(delta)
                    }
                    val dist = hypot(lastPos.x - start.x, lastPos.y - start.y)
                    if (moved < 8f && dist < 14f) {
                        scope.launch {
                            onDialEnd()
                            spark = Spark(pivot.x, pivot.y, time)
                            onTap(hit.first, hit.second)
                        }
                    } else {
                        onDialEnd()
                    }
                }
            },
    ) {
        val s = boardScale(size.width, size.height, board.radius)
        val cx = size.width / 2f
        val cy = size.height / 2f
        fun center(q: Int, r: Int) = boardCenter(cx, cy, s, q, r)

        /** Edge midpoint for raw connector [d] in local (plate) space. */
        fun localEdge(d: Int, radius: Float = s * 0.82f): Offset = connectorOffset(d, radius)

        val portQ = 0
        val portR = 1
        val portDir = 1
        val maxDist = (
            board.cells.keys.maxOfOrNull { (q, r) -> hexDistAxial(q, r, portQ, portR) } ?: 1
            ).coerceAtLeast(1)

        val pulsePos = seg(fuseProgress, 0.02f, 0.40f) * (maxDist + 1.6f)
        val seamAlpha = 1f - seg(fuseProgress, 0.35f, 0.72f)
        val unified = seg(fuseProgress, 0.55f, 0.90f)
        val contraction = 1f - 0.08f * seg(fuseProgress, 0.68f, 1f)
        val win = circuit.closed
        val winAge = if (winT0 >= 0f) (time - winT0).coerceAtLeast(0f) else 99f

        // Prototype drawBg: five breathing hex rings under the plates.
        if (!reduced && seamAlpha > 0.01f) {
            val pu = 0.5f + 0.5f * sin(time * 0.8f)
            for (ring in 1..5) {
                val rr = ring * s * 1.35f + 6f * sin(time * 0.6f + ring)
                drawPath(
                    hexPath(Offset(cx, cy), rr, HEX_VERTEX_OFFSET),
                    LatticeColors.Signal.copy(alpha = (0.05f + 0.03f * pu) * seamAlpha),
                    style = Stroke(width = 1f),
                )
            }
        }

        withTransform({
            scale(contraction, contraction, Offset(cx, cy))
        }) {
            // ── Prototype TRACK & segW definitions ──
            fun segW(k: Int): Triple<Offset, Offset, Offset> {
                val def = OBSIDIAN_TRACK_DEFS[k]
                val c = center(def.tileQ, def.tileR)
                val p1Off = connectorOffset(def.inEdge, s * 0.82f)
                val p2Off = connectorOffset(def.outEdge, s * 0.82f)
                return Triple(
                    Offset(c.x + p1Off.x, c.y + p1Off.y),
                    c,
                    Offset(c.x + p2Off.x, c.y + p2Off.y),
                )
            }

            // ── Dashed membership guide (prototype drawGuide) ─────────────────
            if (seamAlpha > 0.01f) {
                val guide = Path()
                for (k in 0..6) {
                    val (p1, c, p2) = segW(k)
                    guide.moveTo(p1.x, p1.y)
                    guide.quadraticTo(c.x, c.y, p2.x, p2.y)
                    if (k < 6) {
                        val nextP1 = segW(k + 1).first
                        guide.moveTo(p2.x, p2.y)
                        guide.lineTo(nextP1.x, nextP1.y)
                    }
                }
                val g6P2 = segW(6).third
                guide.moveTo(g6P2.x, g6P2.y)
                guide.lineTo(cx, cy)

                drawPath(
                    guide,
                    LatticeColors.Signal.copy(alpha = 0.14f * seamAlpha),
                    style = Stroke(
                        width = 1.5f,
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 6f), phase = -time * 8f),
                    ),
                )
            }

            // ── Amber service feeder (prototype drawPort) ─────────────────────
            val pc = center(portQ, portR)
            val pe = connectorOffset(portDir, s * 0.82f)
            val portPt = Offset(pc.x + pe.x, pc.y + pe.y)
            val portAlpha = 1f - 0.75f * unified
            if (portAlpha > 0.01f) {
                val feedFrom = Offset(portPt.x, size.height)
                val feedTo = Offset(portPt.x, portPt.y + 8f)
                drawLine(
                    brush = Brush.linearGradient(
                        listOf(
                            LatticeColors.Service.copy(alpha = 0f),
                            LatticeColors.Service.copy(alpha = 0.75f * portAlpha),
                        ),
                        start = feedFrom,
                        end = feedTo,
                    ),
                    start = feedFrom,
                    end = feedTo,
                    strokeWidth = 2.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f), phase = -time * 40f),
                )
                val pulse = (4f + sin(time * 5f) * 0.8f) * (s / 44f)
                drawCircle(
                    LatticeColors.Service.copy(alpha = 0.35f * portAlpha),
                    radius = pulse * 2.6f,
                    center = portPt,
                )
                drawCircle(LatticeColors.Service.copy(alpha = portAlpha), radius = pulse, center = portPt)
            }

            // ── Plates (prototype drawTile / plate / link) ────────────────────
            fun drawCell(key: Pair<Int, Int>, spinning: Boolean) {
                val cell = board.cells.getValue(key)
                val (q, r) = key
                val c = center(q, r)
                val dist = hexDistAxial(q, r, portQ, portR)
                val litFade = (revealProgress * (maxDist + 1.5f) - dist).coerceIn(0f, 1f)
                val powered = key in circuit.reached
                val isStub = key in circuit.stubs
                val p = if (spinning) spinProgress else 0f
                val dialExtra = if (key == dialCell) dialAngle else 0f
                val visDeg = cell.rotation * 60f +
                    (if (spinning) 60f * easeBack(p.coerceIn(0f, 1f)) else 0f) +
                    dialExtra
                val flare = if (fuseProgress > 0.01f) {
                    (1f - abs(dist - pulsePos) / 1.4f).coerceIn(0f, 1f)
                } else {
                    0f
                }

                withTransform({
                    translate(c.x, c.y)
                    rotate(visDeg, pivot = Offset.Zero)
                }) {
                    // plate(): obsidian fill, lit rim (with bloom), machined bevel + arcs.
                    val hexOuter = hexPath(Offset.Zero, s * 0.94f, HEX_VERTEX_OFFSET)
                    drawPath(hexOuter, Color(0xF504080D))
                    if (powered && seamAlpha > 0.01f) {
                        drawPath(
                            hexOuter,
                            LatticeColors.SignalBright.copy(alpha = 0.22f * seamAlpha),
                            style = Stroke(width = 6f),
                        )
                    }
                    val rim = when {
                        isStub -> LatticeColors.Desync.copy(alpha = 0.85f)
                        powered -> Color(0xF27FF3FF)
                        else -> Color(0xCC1E4658)
                    }
                    drawPath(
                        hexOuter,
                        rim.copy(alpha = rim.alpha * seamAlpha.coerceAtLeast(0.4f)),
                        style = Stroke(width = 1.4f),
                    )
                    val hexInner = hexPath(Offset.Zero, s * 0.82f, HEX_VERTEX_OFFSET)
                    drawPath(hexInner, Color.White.copy(alpha = 0.10f), style = Stroke(width = 3f))
                    drawPath(hexInner, Color.Black.copy(alpha = 0.5f), style = Stroke(width = 1f))
                    drawArc(
                        color = Color(0x80285064),
                        startAngle = 20f,
                        sweepAngle = 130f,
                        useCenter = false,
                        topLeft = Offset(-s * 0.30f, -s * 0.30f),
                        size = Size(s * 0.60f, s * 0.60f),
                        style = Stroke(width = 1f),
                    )
                    drawArc(
                        color = Color(0x80285064),
                        startAngle = 200f,
                        sweepAngle = 130f,
                        useCenter = false,
                        topLeft = Offset(-s * 0.44f, -s * 0.44f),
                        size = Size(s * 0.88f, s * 0.88f),
                        style = Stroke(width = 1f),
                    )
                    if (flare > 0.01f) {
                        drawPath(hexOuter, LatticeColors.TextPrimary.copy(alpha = 0.22f * flare))
                    }

                    // link(): every connector pair is a quadratic arc through the centre.
                    val on = powered && litFade > 0.01f
                    val pairs = if (key.first == 0 && key.second == 1) {
                        listOf(1 to 5, 3 to 4)
                    } else {
                        linkPairs(cell.segment)
                    }
                    for ((a, b) in pairs) {
                        val p1 = localEdge(a)
                        val p2 = localEdge(b)
                        val path = Path().apply {
                            moveTo(p1.x, p1.y)
                            quadraticTo(0f, 0f, p2.x, p2.y)
                        }
                        drawPath(
                            path,
                            Color.Black.copy(alpha = 0.9f),
                            style = Stroke(width = s * 0.159f, cap = StrokeCap.Round),
                        )
                        drawPath(
                            path,
                            if (on) Color(0x407FF3FF) else Color(0xE61A3442),
                            style = Stroke(width = s * 0.114f, cap = StrokeCap.Round),
                        )
                        if (on) {
                            drawPath(
                                path,
                                Color(0xFFEAFCFF),
                                style = Stroke(width = s * 0.041f, cap = StrokeCap.Round),
                            )
                            if (!spinning && !reduced) {
                                // Prototype qpt(): u^2*p1 + t^2*p2 — the head dips toward the hub.
                                val ph = (time * (if (win) 1.6f else 0.7f)) % 1f
                                val u = 1f - ph
                                val head = Offset(u * u * p1.x + ph * ph * p2.x, u * u * p1.y + ph * ph * p2.y)
                                drawCircle(Color.White, radius = 2.2f, center = head)
                            }
                        }
                    }
                }
            }

            board.cells.keys.forEach { key -> if (key != spinCell && key != dialCell) drawCell(key, false) }
            spinCell?.let { if (it in board.cells) drawCell(it, true) }
            dialCell?.let { if (it in board.cells && it != spinCell) drawCell(it, false) }

            // ── Bridges between lit neighbours (prototype drawBridges) ───────────
            if (seamAlpha > 0.01f) {
                for (k in 0..5) {
                    val qA = OBSIDIAN_TRACK_DEFS[k].tileQ
                    val rA = OBSIDIAN_TRACK_DEFS[k].tileR
                    val qB = OBSIDIAN_TRACK_DEFS[k + 1].tileQ
                    val rB = OBSIDIAN_TRACK_DEFS[k + 1].tileR
                    if ((qA to rA) !in circuit.reached || (qB to rB) !in circuit.reached) continue
                    val exitA = segW(k).third
                    val entryB = segW(k + 1).first
                    drawLine(
                        LatticeColors.SignalBright.copy(alpha = 0.30f * seamAlpha),
                        exitA,
                        entryB,
                        strokeWidth = 6f,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        Color(0xFFEAFCFF).copy(alpha = 0.9f * seamAlpha),
                        exitA,
                        entryB,
                        strokeWidth = 1.8f,
                        cap = StrokeCap.Round,
                    )
                }
                if (circuit.closed) {
                    val g6P2 = segW(6).third
                    drawLine(
                        LatticeColors.SignalBright.copy(alpha = 0.30f * seamAlpha),
                        g6P2,
                        Offset(cx, cy),
                        strokeWidth = 7f,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        Color(0xFFEAFCFF).copy(alpha = 0.95f * seamAlpha),
                        g6P2,
                        Offset(cx, cy),
                        strokeWidth = 2f,
                        cap = StrokeCap.Round,
                    )
                }
            }

            // ── Core (prototype drawCore) ────────────────────────────────
            val u = s / 44f
            val wBloom = if (win) min(1f, winAge / 1.2f) else 0f
            val coreR = (10f + 3f * sin(time * 2.2f) + wBloom * 26f) * u
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color.White.copy(alpha = 0.9f),
                    0.35f to LatticeColors.Signal.copy(alpha = 0.7f),
                    1f to LatticeColors.Signal.copy(alpha = 0f),
                    center = Offset(cx, cy),
                    radius = coreR * 3f,
                ),
                radius = coreR * 3f,
                center = Offset(cx, cy),
            )
            drawCircle(Color.White, radius = (4f + wBloom * 3f) * u, center = Offset(cx, cy))
            val ringR = s * 0.5f + wBloom * 6f * u
            drawArc(
                color = LatticeColors.Signal.copy(alpha = 0.5f + 0.3f * sin(time * 3f)),
                startAngle = time * 70f,
                sweepAngle = 250f,
                useCenter = false,
                topLeft = Offset(cx - ringR, cy - ringR),
                size = Size(ringR * 2f, ringR * 2f),
                style = Stroke(width = 1.5f),
            )

            // ── Light-cycle racer triggered ONLY on holding synthesis ───────
            if (circuit.closed && commitCharge > 0.001f && !reduced) {
                val nTrack = 7
                val fHead = commitCharge.coerceIn(0f, 1f) * nTrack
                for (tr in 11 downTo 0) {
                    val ft = fHead - tr * 0.06f
                    if (ft < 0f) continue
                    val kk = ft.toInt().coerceIn(0, 6)
                    val (p1, c, p2) = segW(kk)
                    val segmentT = (ft - kk).coerceIn(0f, 1f)
                    val p = if (kk == 6) {
                        qptW(p1, c, Offset(cx, cy), segmentT)
                    } else {
                        qptW(p1, c, p2, segmentT)
                    }
                    if (tr == 0) {
                        drawCircle(LatticeColors.SignalBright.copy(alpha = 0.95f), radius = 10f * u, center = p)
                        drawCircle(Color.White, radius = 4f * u, center = p)
                    } else {
                        val fade = 1f - (tr / 12f)
                        drawCircle(
                            LatticeColors.SignalBright.copy(alpha = 0.7f * fade),
                            radius = 3.8f * u * fade,
                            center = p,
                        )
                    }
                }
            }

            // Dial affordance while a plate is wound.
            dialCell?.let { key ->
                if (key in board.cells) {
                    val dc = center(key.first, key.second)
                    for (i in 0..5) {
                        val a = i * 60f * DEG_TO_RAD
                        drawLine(
                            LatticeColors.SignalBright.copy(alpha = 0.7f),
                            Offset(dc.x + s * 1.12f * cos(a), dc.y + s * 1.12f * sin(a)),
                            Offset(dc.x + s * 1.24f * cos(a), dc.y + s * 1.24f * sin(a)),
                            strokeWidth = 1.5f,
                        )
                    }
                    val na = dialAngle * DEG_TO_RAD
                    drawLine(
                        Color.White,
                        Offset(dc.x + s * 1.05f * cos(na), dc.y + s * 1.05f * sin(na)),
                        Offset(dc.x + s * 1.30f * cos(na), dc.y + s * 1.30f * sin(na)),
                        strokeWidth = 2f,
                    )
                }
            }

            spark?.let { sp ->
                val e = (time - sp.t0) / 0.34f
                if (e in 0f..1f) {
                    drawCircle(
                        color = Color(0xFFB4F5FF).copy(alpha = (1f - e) * 0.9f),
                        radius = s * (0.5f + 0.9f * e),
                        center = Offset(sp.x, sp.y),
                        style = Stroke(width = 2.2f * (1f - e) + 0.4f),
                    )
                }
            }

            if (desyncLevel > 0.01f) {
                for (i in 0 until 5) {
                    val h = ((i * 131 + 17) % 97) / 97f
                    val y = size.height * ((h + desyncLevel * 0.13f * (i + 1)) % 1f)
                    val shift = (h - 0.5f) * s * 1.6f * desyncLevel
                    drawRect(
                        LatticeColors.Desync.copy(alpha = 0.16f * desyncLevel),
                        topLeft = Offset(shift, y),
                        size = Size(size.width, s * (0.10f + 0.16f * h)),
                    )
                }
                drawRect(LatticeColors.Desync.copy(alpha = 0.06f * desyncLevel))
            }

            if (unified > 0.01f) {
                val pr = s * (board.radius * 1.5f + 1.05f)
                val plate = hexPath(Offset(cx, cy), pr, HEX_VERTEX_OFFSET)
                drawPath(
                    plate,
                    brush = Brush.radialGradient(
                        listOf(
                            LatticeColors.Signal.copy(alpha = 0.10f * unified),
                            LatticeColors.Signal.copy(alpha = 0f),
                        ),
                        center = Offset(cx, cy),
                        radius = pr,
                    ),
                )
                drawPath(plate, LatticeColors.Signal.copy(alpha = 0.6f * unified), style = Stroke(width = 3f))
            }
            val bloom = seg(fuseProgress, 0.62f, 1f)
            if (bloom > 0.01f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(
                            LatticeColors.TextPrimary.copy(alpha = 0.85f * bloom),
                            LatticeColors.Signal.copy(alpha = 0.45f * bloom),
                            LatticeColors.Void.copy(alpha = 0f),
                        ),
                        center = Offset(cx, cy),
                        radius = s * (0.5f + 2.4f * bloom),
                    ),
                    radius = s * (0.5f + 2.4f * bloom),
                    center = Offset(cx, cy),
                )
            }
        }

        // Win flood (prototype drawFlood).
        if (win && winAge in 0f..1.8f) {
            val f = if (winAge < 0.18f) {
                winAge / 0.18f
            } else {
                (1f - (winAge - 0.18f) / 1.6f).coerceAtLeast(0f)
            }
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color.White.copy(alpha = 0.85f * f),
                    0.4f to LatticeColors.Signal.copy(alpha = 0.5f * f),
                    1f to LatticeColors.Signal.copy(alpha = 0.12f * f),
                    center = Offset(cx, cy),
                    radius = size.height * 0.8f,
                ),
                radius = size.height * 0.8f,
                center = Offset(cx, cy),
            )
        }
    }

    LaunchedEffect(spark, time) {
        val sp = spark ?: return@LaunchedEffect
        if (time - sp.t0 > 0.4f) spark = null
    }
}

// ─── link topology (raw segment → arc pairs, never radial sticks) ───────────────

private fun linkPairs(segment: LatticeSegment): List<Pair<Int, Int>> {
    val c = segment.connectors
    return when (segment) {
        LatticeSegment.LINE,
        LatticeSegment.CURVE,
        LatticeSegment.ELBOW,
        -> listOf(c[0] to c[1])
        LatticeSegment.TEE,
        LatticeSegment.FORK,
        -> listOf(c[0] to c[2])
    }
}

// ─── helpers ───────────────────────────────────────────────────

private data class Spark(val x: Float, val y: Float, val t0: Float)

private const val SQRT3 = 1.7320508f
private const val RAD_TO_DEG = 57.29578f
private const val DEG_TO_RAD = 0.017453292f

/** Prototype hexPath0(): every hexagon has vertices at 30 + 60k degrees (pointy-top). */
private const val HEX_VERTEX_OFFSET = 30f

/**
 * Prototype sizing: S = 44 on a 390 px canvas, i.e. the flower spans ~57% of the shortest side.
 */
private fun boardScale(width: Float, height: Float, radius: Int): Float {
    val span = 3.464f * radius + 1.88f
    return min(width, height) * 0.669f / span
}

/** Prototype cpos(): pointy-top layout, neighbour [d] sits at screen angle 60 * d. */
private fun boardCenter(cx: Float, cy: Float, s: Float, q: Int, r: Int): Offset =
    Offset(cx + s * SQRT3 * (q + r / 2f), cy + s * 1.5f * r)

/** Prototype polar(60 * e, radius): connector [d] leaves the plate at screen angle 60 * d. */
private fun connectorOffset(d: Int, radius: Float): Offset {
    val a = 60f * d.mod(6) * DEG_TO_RAD
    return Offset(cos(a) * radius, sin(a) * radius)
}

/** Point on the [from] plate rim facing [toward] (prototype eXY). */
private fun edgePoint(from: Offset, toward: Offset, radius: Float): Offset {
    val dx = toward.x - from.x
    val dy = toward.y - from.y
    val len = hypot(dx, dy).coerceAtLeast(1e-4f)
    return Offset(from.x + dx / len * radius, from.y + dy / len * radius)
}

private fun easeBack(k: Float): Float {
    val c1 = 1.35f
    val c3 = c1 + 1f
    val x = k - 1f
    return 1f + c3 * x * x * x + c1 * x * x
}

private fun qptW(p1: Offset, c: Offset, p2: Offset, t: Float): Offset {
    val u = 1f - t
    return Offset(
        u * u * p1.x + 2f * u * t * c.x + t * t * p2.x,
        u * u * p1.y + 2f * u * t * c.y + t * t * p2.y,
    )
}

private fun hexPath(center: Offset, radius: Float, vertexOffsetDeg: Float): Path {
    val path = Path()
    for (i in 0..5) {
        val a = (PI / 3.0 * i).toFloat() + vertexOffsetDeg * DEG_TO_RAD
        val p = Offset(center.x + radius * cos(a), center.y + radius * sin(a))
        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    path.close()
    return path
}

private fun hexDistAxial(aq: Int, ar: Int, bq: Int, br: Int): Int {
    val dq = aq - bq
    val dr = ar - br
    return (abs(dq) + abs(dr) + abs(dq + dr)) / 2
}

private fun seg(t: Float, a: Float, b: Float): Float = ((t - a) / (b - a)).coerceIn(0f, 1f)

private data class ObsidianTrackDef(val tileQ: Int, val tileR: Int, val inEdge: Int, val outEdge: Int)

private val OBSIDIAN_TRACK_DEFS = listOf(
    ObsidianTrackDef(0, 1, 1, 5),
    ObsidianTrackDef(1, 0, 2, 4),
    ObsidianTrackDef(1, -1, 1, 3),
    ObsidianTrackDef(0, -1, 0, 2),
    ObsidianTrackDef(-1, 0, 5, 1),
    ObsidianTrackDef(-1, 1, 4, 0),
    ObsidianTrackDef(0, 1, 3, 4),
)
