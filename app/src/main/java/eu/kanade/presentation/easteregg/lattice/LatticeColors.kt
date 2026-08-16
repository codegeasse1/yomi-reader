package eu.kanade.presentation.easteregg.lattice

import androidx.compose.ui.graphics.Color

/** Lattice palette: black void, cyan signal, amber service channel. */
object LatticeColors {
    val Void = Color(0xFF050608)
    val Panel = Color(0xFF0A1220)
    val Signal = Color(0xFF0095AE)
    val SignalDim = Color(0x660095AE)
    val TextPrimary = Color(0xFFF4FEFF)
    val Service = Color(0xFFFFA726)
    val Alert = Color(0xFFFF3B4E)
    val GridLine = Color(0x1A0095AE)

    /** Cold desync violet: failed-topology reject (never a cartoon red alarm). */
    val Desync = Color(0xFFB84BFF)
    val DesyncDim = Color(0x66B84BFF)

    /** Etched micro-circuit lines on glass-black plates. */
    val Etch = Color(0xFF16283C)

    // Stage 3.5: obsidian glass fill for the routing track plates.
    val Obsidian = Color(0xFF060A10)

    /** Prototype HTML bright cyan (#7FF3FF) used for lit plate rims and bridges. */
    val SignalBright = Color(0xFF7FF3FF)
}
