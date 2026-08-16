package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Colors for Aurora Prime theme (static base).
 *
 * Living aurora-metal: polar-lime primary, violet secondary and aurora-teal
 * tertiary over a deep polar night. The live gleam overlay (device tilt +
 * breathing sheen) is applied on top in [eu.kanade.presentation.theme.auroraPrimeOverlay].
 */
internal object AuroraPrimeColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFFB6F04C),
        onPrimary = Color(0xFF1C2A00),
        primaryContainer = Color(0xFF425C05),
        onPrimaryContainer = Color(0xFFE0FFAD),
        inversePrimary = Color(0xFF8FB42B),

        secondary = Color(0xFFC25CFF),
        onSecondary = Color(0xFF32004F),
        secondaryContainer = Color(0xFF4E1A8C),
        onSecondaryContainer = Color(0xFFEAD3FF),

        tertiary = Color(0xFF2FC9A0),
        onTertiary = Color(0xFF00382C),
        tertiaryContainer = Color(0xFF004F3E),
        onTertiaryContainer = Color(0xFF7BF2C8),

        background = Color(0xFF030810),
        onBackground = Color(0xFFE3F0E8),
        surface = Color(0xFF081020),
        onSurface = Color(0xFFE3F0E8),
        surfaceVariant = Color(0xFF162238),
        onSurfaceVariant = Color(0xFFB5C4D8),
        surfaceTint = Color(0xFFB6F04C),
        inverseSurface = Color(0xFFDFEADF),
        inverseOnSurface = Color(0xFF1B241C),

        outline = Color(0xFF75849A),
        outlineVariant = Color(0xFF232E44),

        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),

        scrim = Color(0xFF000000),

        surfaceDim = Color(0xFF030810),
        surfaceBright = Color(0xFF2A3854),
        surfaceContainerLowest = Color(0xFF01040C),
        surfaceContainerLow = Color(0xFF0B1220),
        surfaceContainer = Color(0xFF101728),
        surfaceContainerHigh = Color(0xFF141D30),
        surfaceContainerHighest = Color(0xFF192337),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF5A9E00),
        onPrimary = Color(0xFF1C2A00),
        primaryContainer = Color(0xFFD3F2A0),
        onPrimaryContainer = Color(0xFF142A00),
        inversePrimary = Color(0xFFB6F04C),

        secondary = Color(0xFF8F2FD0),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFF1D7FF),
        onSecondaryContainer = Color(0xFF310054),

        tertiary = Color(0xFF008A9E),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFB2EBF2),
        onTertiaryContainer = Color(0xFF002F36),

        background = Color(0xFFF5FBF2),
        onBackground = Color(0xFF1A1D18),
        surface = Color(0xFFF5FBF2),
        onSurface = Color(0xFF1A1D18),
        surfaceVariant = Color(0xFFDEE8DA),
        onSurfaceVariant = Color(0xFF43493F),
        surfaceTint = Color(0xFF5A9E00),
        inverseSurface = Color(0xFF2F332B),
        inverseOnSurface = Color(0xFFEFF5EA),

        outline = Color(0xFF73796D),
        outlineVariant = Color(0xFFC2CBBE),

        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),

        scrim = Color(0xFF000000),

        surfaceDim = Color(0xFFDADFCF),
        surfaceBright = Color(0xFFF5FBF2),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFEEF5E8),
        surfaceContainer = Color(0xFFE8EFE2),
        surfaceContainerHigh = Color(0xFFE2E9DB),
        surfaceContainerHighest = Color(0xFFDCE3D5),
    )
}
