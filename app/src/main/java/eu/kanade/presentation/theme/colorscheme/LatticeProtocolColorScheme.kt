package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Colors for Lattice Protocol theme.
 *
 * Circuit-etched void: deep signal-cyan primary/secondary and an amber
 * tertiary over near-black. The live breathing overlay (slow cyan pulse +
 * amber glint) is applied on top in [eu.kanade.presentation.theme.latticeProtocolOverlay].
 */
internal object LatticeProtocolColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFF0095AE),
        onPrimary = Color(0xFF00343E),
        primaryContainer = Color(0xFF00536A),
        onPrimaryContainer = Color(0xFF93ECFF),
        inversePrimary = Color(0xFF2BB8D0),

        secondary = Color(0xFF00718A),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFF004A5E),
        onSecondaryContainer = Color(0xFFA0ECFF),

        tertiary = Color(0xFFFFA726),
        onTertiary = Color(0xFF442A00),
        tertiaryContainer = Color(0xFF5C3600),
        onTertiaryContainer = Color(0xFFFFE1B0),

        background = Color(0xFF050608),
        onBackground = Color(0xFFDDEAEE),
        surface = Color(0xFF0A0C0E),
        onSurface = Color(0xFFDDEAEE),
        surfaceVariant = Color(0xFF182226),
        onSurfaceVariant = Color(0xFFB2C2C8),
        surfaceTint = Color(0xFF0095AE),
        inverseSurface = Color(0xFFDFE9EC),
        inverseOnSurface = Color(0xFF1D2629),

        outline = Color(0xFF6F8289),
        outlineVariant = Color(0xFF202C30),

        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),

        scrim = Color(0xFF000000),

        surfaceDim = Color(0xFF050608),
        surfaceBright = Color(0xFF26343A),
        surfaceContainerLowest = Color(0xFF020304),
        surfaceContainerLow = Color(0xFF0C1012),
        surfaceContainer = Color(0xFF111619),
        surfaceContainerHigh = Color(0xFF151B1E),
        surfaceContainerHighest = Color(0xFF1A2023),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF003A66),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFCAE6FF),
        onPrimaryContainer = Color(0xFF001E36),
        inversePrimary = Color(0xFF0095AE),

        secondary = Color(0xFF003557),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFC7E7FF),
        onSecondaryContainer = Color(0xFF001E34),

        tertiary = Color(0xFF995900),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFDDB4),
        onTertiaryContainer = Color(0xFF2C1900),

        background = Color(0xFFF6FAFD),
        onBackground = Color(0xFF171D21),
        surface = Color(0xFFF6FAFD),
        onSurface = Color(0xFF171D21),
        surfaceVariant = Color(0xFFDBE4EA),
        onSurfaceVariant = Color(0xFF3F484E),
        surfaceTint = Color(0xFF003A66),
        inverseSurface = Color(0xFF2C3135),
        inverseOnSurface = Color(0xFFEDF5F9),

        outline = Color(0xFF6F767C),
        outlineVariant = Color(0xFFBFC8CE),

        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),

        scrim = Color(0xFF000000),

        surfaceDim = Color(0xFFD6DCE2),
        surfaceBright = Color(0xFFF6FAFD),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFEEF4F8),
        surfaceContainer = Color(0xFFE8EEF4),
        surfaceContainerHigh = Color(0xFFE2E9EF),
        surfaceContainerHighest = Color(0xFFDCE3EA),
    )
}
