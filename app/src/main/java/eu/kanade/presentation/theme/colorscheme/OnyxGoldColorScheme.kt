package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Colors for Onyx Gold theme.
 *
 * Trophy-tier palette: black-diamond surfaces with a deep antique-gold
 * hierarchy, warm ivory text, and a bronze secondary.
 */
internal object OnyxGoldColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFFA8841C),
        onPrimary = Color(0xFF221700),
        primaryContainer = Color(0xFF4A3A0E),
        onPrimaryContainer = Color(0xFFF5DE9E),
        inversePrimary = Color(0xFF9E7A10),

        secondary = Color(0xFF8C6D1F),
        onSecondary = Color(0xFF1F1700),
        secondaryContainer = Color(0xFF3A2D0A),
        onSecondaryContainer = Color(0xFFEFD88F),

        tertiary = Color(0xFFD9CF6E),
        onTertiary = Color(0xFF2E2800),
        tertiaryContainer = Color(0xFF453E10),
        onTertiaryContainer = Color(0xFFF5ECB8),

        background = Color(0xFF070604),
        onBackground = Color(0xFFF2E8CF),
        surface = Color(0xFF0C0A07),
        onSurface = Color(0xFFF2E8CF),
        surfaceVariant = Color(0xFF1D1910),
        onSurfaceVariant = Color(0xFFCFC2A2),
        surfaceTint = Color(0xFFA8841C),
        inverseSurface = Color(0xFFEBE2CC),
        inverseOnSurface = Color(0xFF221B0E),

        outline = Color(0xFFA08236),
        outlineVariant = Color(0xFF453715),

        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),

        scrim = Color(0xFF000000),

        surfaceDim = Color(0xFF070604),
        surfaceBright = Color(0xFF2B2413),
        surfaceContainerLowest = Color(0xFF030202),
        surfaceContainerLow = Color(0xFF0F0D09),
        surfaceContainer = Color(0xFF151209),
        surfaceContainerHigh = Color(0xFF1C1710),
        surfaceContainerHighest = Color(0xFF251E13),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF7A3D00),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFD9A6),
        onPrimaryContainer = Color(0xFF2B1300),
        inversePrimary = Color(0xFFA8841C),

        secondary = Color(0xFF4F3700),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFEED9A0),
        onSecondaryContainer = Color(0xFF241900),

        tertiary = Color(0xFFA0802F),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFF5E2A8),
        onTertiaryContainer = Color(0xFF2A2000),

        background = Color(0xFFFFF9F0),
        onBackground = Color(0xFF241C10),
        surface = Color(0xFFFFF9F0),
        onSurface = Color(0xFF241C10),
        surfaceVariant = Color(0xFFF3E5C8),
        onSurfaceVariant = Color(0xFF524630),
        surfaceTint = Color(0xFF7A3D00),
        inverseSurface = Color(0xFF3A2E1D),
        inverseOnSurface = Color(0xFFFFEFD6),

        outline = Color(0xFF877648),
        outlineVariant = Color(0xFFD9C8A4),

        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),

        scrim = Color(0xFF000000),

        surfaceDim = Color(0xFFE8DCC4),
        surfaceBright = Color(0xFFFFF9F0),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFFCF1DE),
        surfaceContainer = Color(0xFFF6E8CD),
        surfaceContainerHigh = Color(0xFFEFE0C2),
        surfaceContainerHighest = Color(0xFFE9D7B8),
    )
}
