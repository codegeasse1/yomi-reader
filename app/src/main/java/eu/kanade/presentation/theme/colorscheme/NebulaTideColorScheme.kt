package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Colors for Yomi Tide theme.
 *
 * Deep-indigo cosmic theme: indigo primary, rose-yomi secondary and
 * plasma-cyan tertiary over an abyssal blue-black.
 */
internal object YomiTideColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFF5C4BFF),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF2C2190),
        onPrimaryContainer = Color(0xFFC7C1FF),
        inversePrimary = Color(0xFF6B5DFF),

        secondary = Color(0xFFFF5CC8),
        onSecondary = Color(0xFF4A0032),
        secondaryContainer = Color(0xFF4F0030),
        onSecondaryContainer = Color(0xFFFFB8E4),

        tertiary = Color(0xFF00D0FF),
        onTertiary = Color(0xFF003640),
        tertiaryContainer = Color(0xFF004756),
        onTertiaryContainer = Color(0xFF79E9FF),

        background = Color(0xFF030812),
        onBackground = Color(0xFFE2E5F5),
        surface = Color(0xFF090D1C),
        onSurface = Color(0xFFE2E5F5),
        surfaceVariant = Color(0xFF1A1E38),
        onSurfaceVariant = Color(0xFFB9BDD8),
        surfaceTint = Color(0xFF5C4BFF),
        inverseSurface = Color(0xFFE3E5F2),
        inverseOnSurface = Color(0xFF232642),

        outline = Color(0xFF767A9C),
        outlineVariant = Color(0xFF2C3050),

        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),

        scrim = Color(0xFF000000),

        surfaceDim = Color(0xFF030812),
        surfaceBright = Color(0xFF2A2C5C),
        surfaceContainerLowest = Color(0xFF01020A),
        surfaceContainerLow = Color(0xFF0C1022),
        surfaceContainer = Color(0xFF121730),
        surfaceContainerHigh = Color(0xFF181D3A),
        surfaceContainerHighest = Color(0xFF1E2344),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF2E2BC7),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFDEDDFF),
        onPrimaryContainer = Color(0xFF070069),
        inversePrimary = Color(0xFF5C4BFF),

        secondary = Color(0xFFD44AA0),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFFFD8EC),
        onSecondaryContainer = Color(0xFF460026),

        tertiary = Color(0xFF0066B3),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFCCE5FF),
        onTertiaryContainer = Color(0xFF001E36),

        background = Color(0xFFFBF9FF),
        onBackground = Color(0xFF1D1B2C),
        surface = Color(0xFFFBF9FF),
        onSurface = Color(0xFF1D1B2C),
        surfaceVariant = Color(0xFFE5E1F8),
        onSurfaceVariant = Color(0xFF474558),
        surfaceTint = Color(0xFF2E2BC7),
        inverseSurface = Color(0xFF323046),
        inverseOnSurface = Color(0xFFF5EFFF),

        outline = Color(0xFF76748C),
        outlineVariant = Color(0xFFC9C5DD),

        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),

        scrim = Color(0xFF000000),

        surfaceDim = Color(0xFFDFD9F6),
        surfaceBright = Color(0xFFFBF9FF),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF5F1FF),
        surfaceContainer = Color(0xFFEFE9FB),
        surfaceContainerHigh = Color(0xFFE9E3F6),
        surfaceContainerHighest = Color(0xFFE3DCF0),
    )
}
