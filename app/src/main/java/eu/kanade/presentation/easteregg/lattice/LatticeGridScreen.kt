package eu.kanade.presentation.easteregg.lattice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yomi.reader.R
import eu.kanade.domain.easteregg.lattice.LatticeBoard
import eu.kanade.domain.easteregg.lattice.LatticeCircuit
import eu.kanade.domain.easteregg.lattice.LatticePort
import eu.kanade.domain.easteregg.lattice.LatticeProtocolManager
import eu.kanade.domain.easteregg.lattice.LatticeSignalBus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val SQRT3 = 1.7320508f
private const val RAD_TO_DEG = 57.29578f

private enum class LatticePhase { LOADING, INTRO, ROUTING, SYNTHESIS, REWARDS }

/** What occupies the stage slot between header and footer. Crossfaded as one unit. */
private enum class LatticeStage { LOADING, BOARD, CORE }

/** Single source of truth for the terminal header. One beat replaces another, never overlaps. */
private enum class LatticeHeader { BREACH, ROUTE, LOCK, CORE }

/** Mechanical snap: slight overshoot past 60 degrees that settles back вЂ” a physical click. */
private val TileSpinEasing = LatticeEasing.MechanicalSnap

private fun seg(t: Float, a: Float, b: Float): Float = ((t - a) / (b - a)).coerceIn(0f, 1f)

/** Axial hex distance between two cells. */
private fun hexDist(aq: Int, ar: Int, bq: Int, br: Int): Int {
    val dq = aq - bq
    val dr = ar - br
    return (abs(dq) + abs(dr) + abs(dq + dr)) / 2
}

/** Full-screen Grid: route the signal from the amber port to the core. */
@Composable
fun LatticeGridScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val manager = remember { LatticeProtocolManager.get(context) }
    val scope = rememberCoroutineScope()
    val reduced = rememberLatticeReducedMotion()
    val haptics = LocalHapticFeedback.current

    var board by remember { mutableStateOf<LatticeBoard?>(null) }
    var boardVersion by remember { mutableIntStateOf(0) }
    var phase by remember {
        mutableStateOf(
            if (manager.isSynthesisDone()) LatticePhase.SYNTHESIS else LatticePhase.LOADING,
        )
    }
    var synthesizing by remember { mutableStateOf(false) }
    var spinCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val spinProgress = remember { Animatable(0f) }
    var derezProgress by remember { mutableFloatStateOf(1f) }
    var synthBeat by remember { mutableIntStateOf(0) }
    var synthFailed by remember { mutableStateOf(false) }
    var ignitionProgress by remember { mutableFloatStateOf(0f) }
    // Triggers the cinematic LaunchedEffect without being cancelled by phase key changes.
    var payoffToken by remember { mutableIntStateOf(0) }
    // Crossfade from ignition copy into the dedicated rewards showcase.
    val rewardsReveal = remember { Animatable(0f) }
    // Act 4: brief white-cyan overexposure right after ignition peaks, then settle.
    val overexposure = remember { Animatable(0f) }
    // Fusion timeline: seams dissolve and the board fuses into one plate (0 = idle).
    val fuseProgress = remember { Animatable(0f) }
    // Signal re-propagation wave after each topology change (port -> outward).
    val powerReveal = remember { Animatable(0f) }
    // Ritual commit gate: a closed circuit waits for the Architect to hold SYNTHESIZE.
    var awaitingCommit by remember { mutableStateOf(false) }
    var commitToken by remember { mutableIntStateOf(0) }
    // Desync flash on rejected synthesis (1 -> 0).
    val desyncLevel = remember { Animatable(0f) }
    // Touch tilt: the board plate leans toward the tapped cell, then settles back.
    val tiltX = remember { Animatable(0f) }
    val tiltY = remember { Animatable(0f) }
    // Obsidian dial (stage 3.5): the touched plate follows the finger, then snaps onto the lattice.
    var dialCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val dialAngle = remember { Animatable(0f) }
    var commitCharge by remember { mutableFloatStateOf(0f) }

    // Initial open / intro only вЂ” never depends on phase (avoid cancelling cinematics).
    LaunchedEffect(Unit) {
        if (manager.isSynthesisDone() && phase == LatticePhase.SYNTHESIS) {
            board = manager.openBoard()
            derezProgress = 0f
            payoffToken++
            return@LaunchedEffect
        }
        val opened = manager.openBoard()
        if (opened == null) {
            onClose()
            return@LaunchedEffect
        }
        board = opened
        phase = LatticePhase.INTRO
        if (reduced) {
            derezProgress = 0f
            delay(350)
        } else {
            val steps = 28
            for (i in 0..steps) {
                derezProgress = 1f - i / steps.toFloat()
                delay(50)
            }
            derezProgress = 0f
        }
        if (phase == LatticePhase.INTRO) phase = LatticePhase.ROUTING
    }

    val activeBoard = board
    val circuit = remember(boardVersion, activeBoard) {
        activeBoard?.evaluate(LatticePort(0, 1, 1)) ?: LatticeCircuit(false, false, emptySet(), emptySet())
    }

    // Power re-propagation wave: after every topology change the signal visibly re-flows
    // outward from the port instead of the whole board recoloring in a single frame.
    LaunchedEffect(phase, boardVersion) {
        if (phase != LatticePhase.ROUTING) return@LaunchedEffect
        if (reduced) {
            powerReveal.snapTo(1f)
        } else {
            powerReveal.snapTo(0f)
            powerReveal.animateTo(1f, tween(650, easing = LinearEasing))
        }
    }

    // A closed circuit only ARMS the ritual commit вЂ” synthesis waits for the Architect.
    LaunchedEffect(circuit.closed, boardVersion) {
        if (phase != LatticePhase.ROUTING || activeBoard == null) return@LaunchedEffect
        if (!circuit.closed) {
            awaitingCommit = false
            return@LaunchedEffect
        }
        if (synthesizing) return@LaunchedEffect
        if (manager.isSynthesisDone()) {
            phase = LatticePhase.SYNTHESIS
            payoffToken++
            return@LaunchedEffect
        }
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        awaitingCommit = true
    }

    // Commit beam: the forge runs once the SYNTHESIZE ring is fully charged.
    // IMPORTANT: keyed by commitToken only вЂ” assigning SYNTHESIS must not cancel this
    // coroutine mid-flight and leave the user stuck on a dead "ARCHITECT" screen.
    LaunchedEffect(commitToken) {
        if (commitToken == 0) return@LaunchedEffect
        if (synthesizing) return@LaunchedEffect
        val boardNow = board ?: return@LaunchedEffect
        awaitingCommit = false
        synthesizing = true
        synthFailed = false
        // Beat 1 вЂ” the lock lands: hold on the finished circuit so it feels earned.
        delay(if (reduced) 360 else 750)
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        // Beat 2 вЂ” fusion: seams dissolve into one plate while the forge runs in parallel.
        val fuseJob = launch {
            if (reduced) {
                fuseProgress.snapTo(1f)
            } else {
                fuseProgress.animateTo(1f, tween(2600, easing = FastOutSlowInEasing))
            }
        }
        var ok = false
        repeat(5) { attempt ->
            if (ok) return@repeat
            ok = manager.trySynthesize(boardNow)
            if (!ok) delay(300L * (attempt + 1))
        }
        fuseJob.join()
        if (ok) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            phase = LatticePhase.SYNTHESIS
            payoffToken++ // starts cinematic in a separate effect that is not cancelled
        } else {
            // Rollback beat вЂ” cold desync: the plate un-fuses so the topology can be adjusted.
            fuseProgress.animateTo(0f, tween(if (reduced) 1 else 450))
            synthesizing = false
            synthFailed = true
            awaitingCommit = true
        }
    }

    // Desync shimmer flash whenever a synthesis attempt is rejected.
    LaunchedEffect(synthFailed) {
        if (!synthFailed) return@LaunchedEffect
        if (reduced) {
            desyncLevel.snapTo(0f)
        } else {
            desyncLevel.snapTo(1f)
            desyncLevel.animateTo(0f, tween(1100, easing = LinearEasing))
        }
    }

    // High-end core ignition + text beats, then hand off to rewards screen.
    // Keyed ONLY by payoffToken вЂ” never by `phase` (that cancelled the cinematic before).
    LaunchedEffect(payoffToken) {
        if (payoffToken == 0) return@LaunchedEffect
        if (phase != LatticePhase.SYNTHESIS && phase != LatticePhase.REWARDS) {
            phase = LatticePhase.SYNTHESIS
        }
        synthBeat = 0
        ignitionProgress = 0f
        rewardsReveal.snapTo(0f)
        val steps = if (reduced) 24 else 96
        for (i in 0..steps) {
            ignitionProgress = i / steps.toFloat()
            delay(if (reduced) 36 else 90)
        }
        ignitionProgress = 1f
        if (!reduced) {
            launch {
                overexposure.animateTo(1f, tween(160, easing = LinearEasing))
                overexposure.animateTo(0f, tween(1500, easing = LatticeEasing.LightDecay))
            }
        }
        synthBeat = 1
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        delay(if (reduced) 1400 else 2800)
        synthBeat = 2
        delay(if (reduced) 1200 else 2400)
        LatticeSignalBus.consumeSynthesis()
        phase = LatticePhase.REWARDS
        if (reduced) {
            rewardsReveal.snapTo(1f)
        } else {
            rewardsReveal.animateTo(
                1f,
                tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            )
        }
    }

    // Header beats replace each other вЂ” they never stack or overlap.
    val headerStage = when {
        phase == LatticePhase.SYNTHESIS || phase == LatticePhase.REWARDS -> LatticeHeader.CORE
        phase == LatticePhase.ROUTING && synthesizing && !synthFailed -> LatticeHeader.LOCK
        phase == LatticePhase.ROUTING -> LatticeHeader.ROUTE
        else -> LatticeHeader.BREACH
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LatticeColors.Void)
                .latticeGridFloor()
                .then(
                    if (phase == LatticePhase.SYNTHESIS || phase == LatticePhase.REWARDS) {
                        Modifier
                    } else {
                        Modifier.latticeLightCycles()
                    },
                )
                .latticeTerminalFrame(),
        ) {
            // Stage 3.5: the routing acts play out in front of the citadel matte painting.
            AnimatedVisibility(
                visible = phase == LatticePhase.INTRO || phase == LatticePhase.ROUTING,
                enter = fadeIn(tween(1200)),
                exit = fadeOut(tween(900)),
            ) {
                Box(Modifier.fillMaxSize()) {
                    Image(
                        painter = painterResource(R.drawable.bg_lattice_citadel),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // Prototype drawBg: 28% veil, a dark pool under the board and a wide vignette.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.28f)),
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .drawBehind {
                                val c = Offset(size.width / 2f, size.height / 2f)
                                val pool = size.minDimension * 0.78f
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        0.13f to Color.Black.copy(alpha = 0.55f),
                                        1f to Color.Black.copy(alpha = 0f),
                                        center = c,
                                        radius = pool,
                                    ),
                                    radius = pool,
                                    center = c,
                                )
                                val vign = size.maxDimension * 0.67f
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        0.23f to Color.Black.copy(alpha = 0f),
                                        1f to Color.Black.copy(alpha = 0.85f),
                                        center = c,
                                        radius = vign,
                                    ),
                                    radius = vign,
                                    center = c,
                                )
                            },
                    )
                    // Terminal brackets must survive above the matte painting.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .latticeTerminalFrame(),
                    )
                }
            }
            if (phase == LatticePhase.INTRO && derezProgress > 0.01f && !reduced) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .latticeDerez(progress = derezProgress),
                )
            }

            // Full-screen ignition FX under UI copy
            if (phase == LatticePhase.SYNTHESIS || phase == LatticePhase.REWARDS) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .latticeCoreIgnition(progress = ignitionProgress)
                        .then(
                            if (phase == LatticePhase.REWARDS) {
                                Modifier.alpha(1f - rewardsReveal.value * 0.55f)
                            } else {
                                Modifier
                            },
                        ),
                )
            }

            // Act 4 overexposure: the frame floods white-cyan for a beat, then settles.
            if (overexposure.value > 0.005f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    LatticeColors.TextPrimary.copy(alpha = 0.85f * overexposure.value),
                                    LatticeColors.Signal.copy(alpha = 0.5f * overexposure.value),
                                    LatticeColors.Signal.copy(alpha = 0.22f * overexposure.value),
                                ),
                            ),
                        ),
                )
            }

            // Grid / synthesis stage (fades out as rewards reveal)
            AnimatedVisibility(
                visible = phase != LatticePhase.REWARDS || rewardsReveal.value < 0.98f,
                enter = fadeIn(tween(800)),
                exit = fadeOut(tween(1400)),
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (phase == LatticePhase.REWARDS) 1f - rewardsReveal.value else 1f),
            ) {
                val stage = when {
                    phase == LatticePhase.LOADING || activeBoard == null -> LatticeStage.LOADING
                    phase == LatticePhase.SYNTHESIS || phase == LatticePhase.REWARDS -> LatticeStage.CORE
                    else -> LatticeStage.BOARD
                }
                val boardModifierPortrait = Modifier
                    .fillMaxWidth(0.9f)
                    .widthIn(max = 440.dp)
                    .aspectRatio(1f)
                val boardModifierLandscape = Modifier
                    .fillMaxHeight(0.9f)
                    .aspectRatio(1f, matchHeightConstraintsFirst = true)

                @Composable
                fun LatticeHeaderBlock() {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 90.dp),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // Hard cut + type-on: system messages are inscribed, never soft-faded.
                            val routeClosed = headerStage == LatticeHeader.ROUTE && circuit.closed
                            LatticeTypeOnText(
                                text = when (headerStage) {
                                    LatticeHeader.BREACH -> stringResource(AYMR.strings.lattice_breach_line1)
                                    LatticeHeader.ROUTE -> if (routeClosed) {
                                        stringResource(AYMR.strings.lattice_track_closed_title)
                                    } else {
                                        stringResource(AYMR.strings.lattice_routing_line1)
                                    }
                                    LatticeHeader.LOCK -> stringResource(AYMR.strings.lattice_circuit_locked)
                                    LatticeHeader.CORE -> stringResource(AYMR.strings.lattice_core_online)
                                },
                                color = LatticeColors.Signal,
                                fontSize = 16.sp,
                                letterSpacing = 4.sp,
                                reduced = reduced,
                                fringe = headerStage == LatticeHeader.BREACH,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(6.dp))
                            LatticeTitleEnergyBar(
                                Modifier
                                    .width(150.dp)
                                    .height(10.dp),
                            )
                            Spacer(Modifier.height(10.dp))
                            LatticeTypeOnText(
                                text = when (headerStage) {
                                    LatticeHeader.BREACH -> ""
                                    LatticeHeader.ROUTE -> if (routeClosed) {
                                        stringResource(AYMR.strings.lattice_track_closed_sub)
                                    } else {
                                        stringResource(AYMR.strings.lattice_track_hint)
                                    }
                                    LatticeHeader.LOCK -> stringResource(AYMR.strings.lattice_synthesis_line1)
                                    LatticeHeader.CORE -> stringResource(AYMR.strings.lattice_synthesis_line1)
                                },
                                color = LatticeColors.TextPrimary.copy(alpha = 0.75f),
                                fontSize = 11.sp,
                                letterSpacing = 1.2.sp,
                                reduced = reduced,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                @Composable
                fun LatticeStageBlock(boardModifier: Modifier) {
                    Crossfade(
                        targetState = stage,
                        animationSpec = tween(if (reduced) 1 else 1000),
                        label = "latticeStage",
                    ) { st ->
                        when (st) {
                            LatticeStage.LOADING -> Box(
                                Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                // No spinner on the Grid: the plate etches itself out of the void.
                                LatticeMaterializeLoader(Modifier.size(180.dp))
                            }
                            LatticeStage.CORE -> Column(
                                Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    Modifier
                                        .size(220.dp)
                                        .latticeCoreGlow(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        Modifier
                                            .size((48 + ignitionProgress * 36).dp)
                                            .background(
                                                Brush.radialGradient(
                                                    listOf(
                                                        LatticeColors.TextPrimary.copy(alpha = 0.95f),
                                                        LatticeColors.Signal.copy(alpha = 0.55f),
                                                        LatticeColors.Void.copy(alpha = 0f),
                                                    ),
                                                ),
                                            ),
                                    )
                                }
                                Spacer(Modifier.height(16.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(120.dp),
                                    contentAlignment = Alignment.TopCenter,
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = stringResource(AYMR.strings.lattice_synthesis_line2),
                                            color = LatticeColors.TextPrimary,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 13.sp,
                                            textAlign = TextAlign.Center,
                                            letterSpacing = 1.sp,
                                        )
                                        AnimatedVisibility(
                                            visible = synthBeat >= 1,
                                            enter = fadeIn(tween(900)),
                                            exit = fadeOut(tween(700)),
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Spacer(Modifier.height(12.dp))
                                                Text(
                                                    text = stringResource(AYMR.strings.lattice_synthesis_reward),
                                                    color = LatticeColors.Signal.copy(alpha = 0.95f),
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 11.sp,
                                                    textAlign = TextAlign.Center,
                                                    letterSpacing = 1.sp,
                                                )
                                            }
                                        }
                                        AnimatedVisibility(
                                            visible = synthBeat >= 2,
                                            enter = fadeIn(tween(900)),
                                            exit = fadeOut(tween(700)),
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Spacer(Modifier.height(10.dp))
                                                Text(
                                                    text = stringResource(AYMR.strings.lattice_synthesis_continue),
                                                    color = LatticeColors.TextPrimary.copy(alpha = 0.55f),
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 10.sp,
                                                    textAlign = TextAlign.Center,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            LatticeStage.BOARD -> Column(
                                Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                if (activeBoard != null) {
                                    LatticeObsidianBoardCanvas(
                                        board = activeBoard,
                                        circuit = circuit,
                                        enabled = phase == LatticePhase.ROUTING && !synthesizing,
                                        spinCell = spinCell,
                                        spinProgress = spinProgress.value,
                                        revealProgress = powerReveal.value,
                                        fuseProgress = fuseProgress.value,
                                        desyncLevel = desyncLevel.value,
                                        animateFlow = circuit.closed && !reduced,
                                        dialCell = dialCell,
                                        dialAngle = dialAngle.value,
                                        commitCharge = commitCharge,
                                        onDialStart = { q, r ->
                                            if (spinCell == null && dialCell == null) {
                                                dialCell = q to r
                                                scope.launch { dialAngle.snapTo(0f) }
                                            }
                                        },
                                        onDialDelta = { delta ->
                                            if (dialCell != null) {
                                                scope.launch { dialAngle.snapTo(dialAngle.value + delta) }
                                            }
                                        },
                                        onDialEnd = {
                                            dialCell?.let { (q, r) ->
                                                scope.launch {
                                                    val steps = (dialAngle.value / 60f).roundToInt()
                                                    if (steps != 0) {
                                                        repeat(((steps % 6) + 6) % 6) { activeBoard.rotate(q, r) }
                                                        manager.persistRotations(activeBoard)
                                                        boardVersion++
                                                        haptics.performHapticFeedback(
                                                            HapticFeedbackType.TextHandleMove,
                                                        )
                                                    }
                                                    dialAngle.snapTo(dialAngle.value - steps * 60f)
                                                    if (reduced) {
                                                        dialAngle.snapTo(0f)
                                                    } else {
                                                        dialAngle.animateTo(
                                                            0f,
                                                            tween(320, easing = LatticeEasing.MechanicalSnap),
                                                        )
                                                    }
                                                    dialCell = null
                                                }
                                            }
                                        },
                                        modifier = boardModifier.graphicsLayer {
                                            rotationX = tiltY.value
                                            rotationY = tiltX.value
                                            cameraDistance = 24f * density
                                        },
                                    ) { q, r ->
                                        if (spinCell != null) return@LatticeObsidianBoardCanvas
                                        if (!reduced) {
                                            val rad = activeBoard.radius.toFloat()
                                            val dirX = (q / rad).coerceIn(-1f, 1f)
                                            val dirY = ((r + q / 2f) / rad).coerceIn(-1f, 1f)
                                            scope.launch {
                                                tiltX.animateTo(5f * dirX, tween(150, easing = LinearEasing))
                                                tiltX.animateTo(0f, tween(450, easing = LatticeEasing.LightDecay))
                                            }
                                            scope.launch {
                                                tiltY.animateTo(-5f * dirY, tween(150, easing = LinearEasing))
                                                tiltY.animateTo(0f, tween(450, easing = LatticeEasing.LightDecay))
                                            }
                                        }
                                        scope.launch {
                                            spinCell = q to r
                                            spinProgress.snapTo(0f)
                                            if (!reduced) {
                                                spinProgress.animateTo(
                                                    1f,
                                                    tween(420, easing = TileSpinEasing),
                                                )
                                            }
                                            activeBoard.rotate(q, r)
                                            manager.persistRotations(activeBoard)
                                            boardVersion++
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            spinCell = null
                                            spinProgress.snapTo(0f)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                @Composable
                fun LatticeBottomControlsBlock() {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        AnimatedVisibility(
                            visible = synthFailed && circuit.closed,
                            enter = fadeIn(tween(500)),
                            exit = fadeOut(tween(400)),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = stringResource(AYMR.strings.lattice_synth_failed),
                                    color = LatticeColors.Desync.copy(alpha = 0.95f),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    letterSpacing = 1.5.sp,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                        AnimatedVisibility(
                            visible = awaitingCommit && !synthesizing && circuit.closed,
                            enter = fadeIn(tween(400)),
                            exit = fadeOut(tween(250)),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                LatticeCommitButton(
                                    reduced = reduced,
                                    onChargeChange = { commitCharge = it },
                                    onCommit = { commitToken++ },
                                )
                            }
                        }
                    }
                }

                // Landscape: side panel (header) + board/core; portrait: stacked cinematic column.
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val isLandscape = maxWidth > maxHeight
                    if (isLandscape) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 32.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                LatticeHeaderBlock()
                                Spacer(Modifier.height(16.dp))
                                LatticeBottomControlsBlock()
                            }
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center,
                            ) {
                                LatticeStageBlock(boardModifier = boardModifierLandscape)
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.TopCenter)
                                    .padding(top = 28.dp),
                                contentAlignment = Alignment.TopCenter,
                            ) {
                                LatticeHeaderBlock()
                            }
                            Box(
                                modifier = Modifier.align(Alignment.Center),
                                contentAlignment = Alignment.Center,
                            ) {
                                LatticeStageBlock(boardModifier = boardModifierPortrait)
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                LatticeBottomControlsBlock()
                            }
                        }
                    }
                }
            }

            // Dedicated Tron rewards showcase (visual previews only)
            if (phase == LatticePhase.REWARDS) {
                LatticeRewardsScreen(
                    onClose = onClose,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(rewardsReveal.value),
                )
            }

            // Track telemetry HUD: bottom-left corner, exactly like the prototype.
            if (phase == LatticePhase.ROUTING && activeBoard != null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 24.dp, bottom = 66.dp),
                ) {
                    Text(
                        text = stringResource(
                            AYMR.strings.lattice_track_nodes,
                            circuit.reached.size,
                            activeBoard.cells.size,
                        ),
                        color = LatticeColors.Signal.copy(alpha = 0.7f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                    )
                    Text(
                        text = if (circuit.closed) {
                            stringResource(AYMR.strings.lattice_track_ring_closed)
                        } else {
                            stringResource(AYMR.strings.lattice_track_ring_open)
                        },
                        color = LatticeColors.Signal.copy(alpha = 0.7f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                    )
                }
            }

            if (phase != LatticePhase.REWARDS) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(AYMR.strings.lattice_overlay_close),
                        tint = LatticeColors.SignalDim,
                    )
                }
            }
        }
    }
}

/** Loading on the Grid: the plate etches itself out of the void, traced by a signal head. */
@Composable
private fun LatticeMaterializeLoader(modifier: Modifier = Modifier) {
    val reduced = rememberLatticeReducedMotion()
    val time by rememberLatticeTimeSeconds(active = !reduced)
    Canvas(modifier = modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension * 0.36f
        val t = if (reduced) 0.999f else (time * 0.45f) % 1f
        // Etch base: the full silhouette waits in deep slate.
        val hex = Path()
        for (i in 0..5) {
            val a = (Math.PI / 3 * i).toFloat()
            val p = Offset(c.x + r * cos(a), c.y + r * sin(a))
            if (i == 0) hex.moveTo(p.x, p.y) else hex.lineTo(p.x, p.y)
        }
        hex.close()
        drawPath(hex, LatticeColors.Etch, style = Stroke(width = 2f))
        // Light traces the silhouette edge by edge.
        val litEdges = t * 6f
        for (i in 0..5) {
            val e = (litEdges - i).coerceIn(0f, 1f)
            if (e <= 0f) continue
            val a1 = (Math.PI / 3 * i).toFloat()
            val a2 = (Math.PI / 3 * (i + 1)).toFloat()
            val p1 = Offset(c.x + r * cos(a1), c.y + r * sin(a1))
            val p2 = Offset(c.x + r * cos(a2), c.y + r * sin(a2))
            val pe = Offset(p1.x + (p2.x - p1.x) * e, p1.y + (p2.y - p1.y) * e)
            drawLine(LatticeColors.Signal.copy(alpha = 0.85f), p1, pe, strokeWidth = 2.8f, cap = StrokeCap.Round)
            if (e < 1f) {
                drawCircle(LatticeColors.TextPrimary.copy(alpha = 0.9f), radius = 3.2f, center = pe)
            }
        }
        // Inner disc ring charging toward the core.
        drawCircle(
            LatticeColors.Signal.copy(alpha = 0.20f + 0.35f * t),
            radius = r * 0.42f,
            center = c,
            style = Stroke(width = 2f),
        )
        drawCircle(LatticeColors.Signal.copy(alpha = 0.5f + 0.4f * t), radius = r * 0.07f, center = c)
    }
}

/** Ritual commit: hold to charge the identity ring; a full ring fires the commit beam. */
@Composable
private fun LatticeCommitButton(
    reduced: Boolean,
    onChargeChange: (Float) -> Unit,
    onCommit: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val charge = remember { Animatable(0f) }

    LaunchedEffect(charge.value) {
        onChargeChange(charge.value)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(84.dp)
                .pointerInput(reduced) {
                    detectTapGestures(
                        onPress = {
                            var fired = false
                            val job = scope.launch {
                                if (reduced) {
                                    charge.snapTo(1f)
                                } else {
                                    charge.animateTo(1f, tween(3000, easing = LinearEasing))
                                }
                                fired = true
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onCommit()
                            }
                            tryAwaitRelease()
                            if (!fired) {
                                job.cancel()
                                scope.launch { charge.animateTo(0f, tween(200, easing = LinearEasing)) }
                            }
                        },
                    )
                },
        ) {
            Canvas(Modifier.size(84.dp)) {
                val cc = Offset(size.width / 2f, size.height / 2f)
                val rr = size.minDimension / 2f - 6f
                drawCircle(LatticeColors.SignalDim, radius = rr, center = cc, style = Stroke(width = 2f))
                if (charge.value > 0.005f) {
                    drawArc(
                        color = LatticeColors.Signal,
                        startAngle = -90f,
                        sweepAngle = 360f * charge.value,
                        useCenter = false,
                        topLeft = Offset(cc.x - rr, cc.y - rr),
                        size = Size(rr * 2f, rr * 2f),
                        style = Stroke(width = 3.5f, cap = StrokeCap.Round),
                    )
                }
                drawCircle(
                    LatticeColors.Signal.copy(alpha = 0.18f + 0.5f * charge.value),
                    radius = rr * 0.45f,
                    center = cc,
                )
            }
            Text(
                text = stringResource(AYMR.strings.lattice_synthesis_commit),
                color = LatticeColors.TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                letterSpacing = 1.5.sp,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(AYMR.strings.lattice_commit_hint),
            color = LatticeColors.SignalDim,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            letterSpacing = 1.2.sp,
        )
    }
}

/** Terminal inscription: hard cut + type-on with a cursor; critical lines never soft-fade. */
@Composable
private fun LatticeTypeOnText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    letterSpacing: TextUnit,
    reduced: Boolean,
    fringe: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var shown by remember(text) { mutableIntStateOf(if (reduced) text.length else 0) }
    LaunchedEffect(text, reduced) {
        if (!reduced) {
            shown = 0
            while (shown < text.length) {
                delay(16)
                shown++
            }
        }
    }
    val body = text.take(shown)
    val display = if (shown < text.length) body + "\u258C" else body
    Box(modifier) {
        if (fringe && !reduced && shown < text.length) {
            // Chromatic fringe only while the line is being inscribed (stress beat).
            Text(
                text = display,
                color = LatticeColors.Desync.copy(alpha = 0.35f),
                fontFamily = FontFamily.Monospace,
                fontSize = fontSize,
                letterSpacing = letterSpacing,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(x = (-2).dp),
            )
            Text(
                text = display,
                color = LatticeColors.Signal.copy(alpha = 0.4f),
                fontFamily = FontFamily.Monospace,
                fontSize = fontSize,
                letterSpacing = letterSpacing,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(x = 2.dp),
            )
        }
        Text(
            text = display,
            color = color,
            fontFamily = FontFamily.Monospace,
            fontSize = fontSize,
            letterSpacing = letterSpacing,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
