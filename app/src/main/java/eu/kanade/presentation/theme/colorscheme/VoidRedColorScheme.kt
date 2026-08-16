package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Colors for Void Red theme.
 *
 * Deep crimson terminal: wet-artery primary, coagulated secondary and a
 * bright raspberry tertiary over a pitch-black red-void tint.
 */
internal object VoidRedColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFFB0003C),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF57001F),
        onPrimaryContainer = Color(0xFFFFD8E4),
        inversePrimary = Color(0xFFE0457E),

        secondary = Color(0xFF7A0026),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFF400014),
        onSecondaryContainer = Color(0xFFFFD6E2),

        tertiary = Color(0xFFE0407E),
        onTertiary = Color(0xFF4A0028),
        tertiaryContainer = Color(0xFF64003D),
        onTertiaryContainer = Color(0xFFFFD6E8),

        background = Color(0xFF050001),
        onBackground = Color(0xFFF0E2E8),
        surface = Color(0xFF0D0207),
        onSurface = Color(0xFFF0E2E8),
        surfaceVariant = Color(0xFF1F0D15),
        onSurfaceVariant = Color(0xFFCCB4BE),
        surfaceTint = Color(0xFFB0003C),
        inverseSurface = Color(0xFFEDDEE4),
        inverseOnSurface = Color(0xFF261119),

        outline = Color(0xFF8F5C6E),
        outlineVariant = Color(0xFF33121F),

        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),

        scrim = Color(0xFF000000),

        surfaceDim = Color(0xFF050001),
        surfaceBright = Color(0xFF3A1525),
        surfaceContainerLowest = Color(0xFF020001),
        surfaceContainerLow = Color(0xFF100409),
        surfaceContainer = Color(0xFF16060D),
        surfaceContainerHigh = Color(0xFF1B0911),
        surfaceContainerHighest = Color(0xFF220C16),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF6E0024),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFD8E2),
        onPrimaryContainer = Color(0xFF2C0010),
        inversePrimary = Color(0xFFB0003C),

        secondary = Color(0xFF4E0018),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFFFDBE2),
        onSecondaryContainer = Color(0xFF230007),

        tertiary = Color(0xFFC2185B),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFDBE9),
        onTertiaryContainer = Color(0xFF3E0024),

        background = Color(0xFFFFF7F8),
        onBackground = Color(0xFF211920),
        surface = Color(0xFFFFF7F8),
        onSurface = Color(0xFF211920),
        surfaceVariant = Color(0xFFF5DEE4),
        onSurfaceVariant = Color(0xFF52434A),
        surfaceTint = Color(0xFF6E0024),
        inverseSurface = Color(0xFF37292F),
        inverseOnSurface = Color(0xFFFFEDF1),

        outline = Color(0xFF857279),
        outlineVariant = Color(0xFFD8C2C8),

        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),

        scrim = Color(0xFF000000),

        surfaceDim = Color(0xFFE6CBD3),
        surfaceBright = Color(0xFFFFF7F8),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFFDF0F3),
        surfaceContainer = Color(0xFFF7E4E9),
        surfaceContainerHigh = Color(0xFFF1D9E0),
        surfaceContainerHighest = Color(0xFFEBCED7),
    )
}
