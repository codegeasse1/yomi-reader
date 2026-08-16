package eu.kanade.presentation.easteregg.lattice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.domain.easteregg.lattice.LatticeProtocolManager
import eu.kanade.domain.easteregg.lattice.LatticeSignalBus
import kotlinx.coroutines.delay
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Global overlay host mounted in MainActivity.
 * Terminal flashes (whisper / latch / residual) + Grid on shell breach.
 */
@Composable
fun LatticeOverlayHost() {
    val context = LocalContext.current
    val manager = remember { LatticeProtocolManager.get(context) }
    val breach by LatticeSignalBus.breach.collectAsState()
    val latchTick by LatticeSignalBus.latchEvents.collectAsState()
    val whisper by LatticeSignalBus.whisper.collectAsState()

    var showGrid by remember { mutableStateOf(false) }
    // Self-healing auto-open. The breach signal alone was not enough: it could be emitted
    // (and consumed) while the reader/player sat on top of MainActivity, or while the
    // activity was being recreated, so the Grid never appeared and the easter egg became
    // permanently unreachable. The manager now persists a "breach pending" debt, so the
    // Grid opens as soon as this host is composed again.
    LaunchedEffect(Unit) {
        if (manager.isBreachPending()) showGrid = true
    }
    LaunchedEffect(breach) {
        if (breach) {
            LatticeSignalBus.consumeBreach()
            showGrid = true
        }
    }

    var flashText by remember { mutableStateOf<String?>(null) }
    val latchLabel = stringResource(AYMR.strings.lattice_carrier_signal)
    val residualLabel = stringResource(AYMR.strings.lattice_residual)
    val whisperLabel = stringResource(AYMR.strings.lattice_frame_whisper)

    LaunchedEffect(latchTick) {
        if (latchTick != 0L) {
            flashText = latchLabel
            delay(2000)
            if (flashText == latchLabel) flashText = null
        }
    }

    // The whisper is a sticky StateFlow, so consuming it inside an effect keyed on it
    // flips the key back to null and cancels that very coroutine. Previously the cancel
    // happened during delay(3200), so flashText was never reset and the whisper line
    // stayed on screen for the whole activity lifetime. Split the consume from the timed
    // flash (same pattern as latchTick) so the banner always clears.
    var whisperTick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(whisper) {
        if (whisper != null) {
            whisperTick = System.currentTimeMillis()
            LatticeSignalBus.consumeWhisper()
        }
    }
    LaunchedEffect(whisperTick) {
        if (whisperTick != 0L) {
            flashText = whisperLabel
            delay(3200)
            if (flashText == whisperLabel) flashText = null
        }
    }

    var residual by remember { mutableStateOf(manager.shouldShowResidual()) }
    LaunchedEffect(residual) {
        if (residual) {
            flashText = residualLabel
            delay(5500)
            manager.markResidualShown()
            residual = false
            if (flashText == residualLabel) flashText = null
        }
    }

    AnimatedVisibility(
        visible = flashText != null,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        flashText?.let { LatticeTerminalFlash(it) }
    }
    if (showGrid) {
        LatticeGridScreen(
            onClose = {
                showGrid = false
                // Debt paid: the Grid was actually shown. Manual re-entry stays available
                // through More -> "Open the Lattice".
                manager.markBreachOpened()
                // Re-arm residual flash after a successful unlock cinematic.
                residual = manager.shouldShowResidual()
            },
        )
    }
}

/**
 * Diegetic HUD inscription: a type-on monospace line at the top of the frame with a
 * brief chromatic glitch burst — a system whisper, not a floating snackbar chip.
 */
@Composable
private fun LatticeTerminalFlash(text: String) {
    val reduced = rememberLatticeReducedMotion()
    var shown by remember(text) { mutableIntStateOf(if (reduced) text.length else 0) }
    LaunchedEffect(text, reduced) {
        if (!reduced) {
            shown = 0
            while (shown < text.length) {
                delay(22)
                shown++
            }
        }
    }
    val time by rememberLatticeTimeSeconds(active = !reduced)
    val body = text.take(shown)
    val display = if (shown < text.length) body + "\u258C" else body
    // Deterministic glitch burst roughly every 1.4s; silent the rest of the time.
    val burst = (time * 0.7f) % 1f
    val glitch = if (!reduced && burst < 0.07f) 1f - burst / 0.07f else 0f
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 72.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box {
            if (glitch > 0f) {
                Text(
                    text = display,
                    color = LatticeColors.TextPrimary.copy(alpha = 0.4f * glitch),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    letterSpacing = 2.sp,
                    modifier = Modifier.offset(x = (2 + 3 * glitch).dp),
                )
                Text(
                    text = display,
                    color = LatticeColors.Desync.copy(alpha = 0.35f * glitch),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    letterSpacing = 2.sp,
                    modifier = Modifier.offset(x = (-2 - 3 * glitch).dp),
                )
            }
            Text(
                text = display,
                color = LatticeColors.Signal,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                letterSpacing = 2.sp,
            )
        }
    }
}
