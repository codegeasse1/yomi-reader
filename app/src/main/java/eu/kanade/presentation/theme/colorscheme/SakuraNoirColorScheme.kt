package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Colors for Sakura Noir theme.
 *
 * Midnight sakura: neon-rose petals over plum noir surfaces, with a jade
 * secondary and a soft plum tertiary.
 */
internal object SakuraNoirColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFFAD1060),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF4A0028),
        onPrimaryContainer = Color(0xFFFFD6E4),
        inversePrimary = Color(0xFFE04596),

        secondary = Color(0xFF00C853),
        onSecondary = Color(0xFF003A15),
        secondaryContainer = Color(0xFF004D1E),
        onSecondaryContainer = Color(0xFF8AF0AE),

        tertiary = Color(0xFF9A6BB8),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFF3F2058),
        onTertiaryContainer = Color(0xFFD3B1EC),

        background = Color(0xFF09050A),
        onBackground = Color(0xFFF4E9F2),
        surface = Color(0xFF100A12),
        onSurface = Color(0xFFF4E9F2),
        surfaceVariant = Color(0xFF241726),
        onSurfaceVariant = Color(0xFFCFBBD2),
        surfaceTint = Color(0xFFAD1060),
        inverseSurface = Color(0xFFF0E0EE),
        inverseOnSurface = Color(0xFF2B1A2C),

        outline = Color(0xFF9A6B9E),
        outlineVariant = Color(0xFF3F283F),

        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),

        scrim = Color(0xFF000000),

        surfaceDim = Color(0xFF09050A),
        surfaceBright = Color(0xFF38202E),
        surfaceContainerLowest = Color(0xFF040204),
        surfaceContainerLow = Color(0xFF130C16),
        surfaceContainer = Color(0xFF1A0F1C),
        surfaceContainerHigh = Color(0xFF201322),
        surfaceContainerHighest = Color(0xFF281828),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF8F0050),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFCDE2),
        onPrimaryContainer = Color(0xFF38001E),
        inversePrimary = Color(0xFFAD1060),

        secondary = Color(0xFF00854A),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFF8FF5BC),
        onSecondaryContainer = Color(0xFF003A15),

        tertiary = Color(0xFF9A86C9),
        onTertiary = Color(0xFF2A1040),
        tertiaryContainer = Color(0xFFE3D4F7),
        onTertiaryContainer = Color(0xFF3A1E58),

        background = Color(0xFFFFF8FA),
        onBackground = Color(0xFF261A22),
        surface = Color(0xFFFFF8FA),
        onSurface = Color(0xFF261A22),
        surfaceVariant = Color(0xFFF5DCE9),
        onSurfaceVariant = Color(0xFF55414E),
        surfaceTint = Color(0xFF8F0050),
        inverseSurface = Color(0xFF3B2A33),
        inverseOnSurface = Color(0xFFFFEAF2),

        outline = Color(0xFF8F7280),
        outlineVariant = Color(0xFFD9C0CE),

        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),

        scrim = Color(0xFF000000),

        surfaceDim = Color(0xFFE3C6D5),
        surfaceBright = Color(0xFFFFF8FA),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFFCF0F6),
        surfaceContainer = Color(0xFFF6E4EC),
        surfaceContainerHigh = Color(0xFFEFD8E2),
        surfaceContainerHighest = Color(0xFFE9CCD8),
    )
}
