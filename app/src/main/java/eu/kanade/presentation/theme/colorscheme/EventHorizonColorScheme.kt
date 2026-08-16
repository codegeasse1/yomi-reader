package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Colors for Event Horizon theme.
 *
 * Blueshifted accretion disk: deep blue primary, violet plasma secondary and
 * pale X-ray tertiary over absolute black.
 */
internal object EventHorizonColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFF1A4FE0),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF0A2E8C),
        onPrimaryContainer = Color(0xFFB9D3FF),
        inversePrimary = Color(0xFF4A78FF),

        secondary = Color(0xFF7A5CFF),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFF3B24A6),
        onSecondaryContainer = Color(0xFFDBD3FF),

        tertiary = Color(0xFFC9DFFF),
        onTertiary = Color(0xFF0B2C66),
        tertiaryContainer = Color(0xFF25418F),
        onTertiaryContainer = Color(0xFFD9E9FF),

        background = Color(0xFF000000),
        onBackground = Color(0xFFE8ECF8),
        surface = Color(0xFF05060C),
        onSurface = Color(0xFFE8ECF8),
        surfaceVariant = Color(0xFF141828),
        onSurfaceVariant = Color(0xFFB4BBD2),
        surfaceTint = Color(0xFF1A4FE0),
        inverseSurface = Color(0xFFE5E9F4),
        inverseOnSurface = Color(0xFF1D2232),

        outline = Color(0xFF6F7694),
        outlineVariant = Color(0xFF23283C),

        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),

        scrim = Color(0xFF000000),

        surfaceDim = Color(0xFF000000),
        surfaceBright = Color(0xFF2E3250),
        surfaceContainerLowest = Color(0xFF01010A),
        surfaceContainerLow = Color(0xFF080A14),
        surfaceContainer = Color(0xFF0D1020),
        surfaceContainerHigh = Color(0xFF121527),
        surfaceContainerHighest = Color(0xFF171A2F),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF0033A0),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD9E2FF),
        onPrimaryContainer = Color(0xFF001449),
        inversePrimary = Color(0xFF1A4FE0),

        secondary = Color(0xFF3F2790),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE1D9FF),
        onSecondaryContainer = Color(0xFF1E0066),

        tertiary = Color(0xFF9CC3F5),
        onTertiary = Color(0xFF0A2C6A),
        tertiaryContainer = Color(0xFFDBE7FF),
        onTertiaryContainer = Color(0xFF00204F),

        background = Color(0xFFF8FAFF),
        onBackground = Color(0xFF191C25),
        surface = Color(0xFFF8FAFF),
        onSurface = Color(0xFF191C25),
        surfaceVariant = Color(0xFFE1E6F5),
        onSurfaceVariant = Color(0xFF44495C),
        surfaceTint = Color(0xFF0033A0),
        inverseSurface = Color(0xFF2D303C),
        inverseOnSurface = Color(0xFFF0F1FF),

        outline = Color(0xFF74798E),
        outlineVariant = Color(0xFFC5CADD),

        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),

        scrim = Color(0xFF000000),

        surfaceDim = Color(0xFFDCDDF2),
        surfaceBright = Color(0xFFF8FAFF),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF1F4FF),
        surfaceContainer = Color(0xFFEAE9FB),
        surfaceContainerHigh = Color(0xFFE4E3F6),
        surfaceContainerHighest = Color(0xFFDEDDF0),
    )
}
