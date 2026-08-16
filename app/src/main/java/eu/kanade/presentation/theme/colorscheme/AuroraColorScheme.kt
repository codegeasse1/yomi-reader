package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal object AuroraColorScheme : BaseColorScheme() {

    // Aniview Premium color palette
    val aniviewElectricBlue = Color(0xFF8B7CFF)
    val aniviewCyan = Color(0xFF35E0FF)
    val aniviewPurple = Color(0xFFE879F9)
    val aniviewDarkBg = Color(0xFF0A0B16)
    val aniviewSurface = Color(0xFF151630)
    val aniviewGlow = Color(0xFF8B7CFF)

    // Legacy Aurora colors (for backwards compatibility)
    val auroraAccent = aniviewElectricBlue
    val auroraAccentLight = Color(0xFF5A4BCF)

    val auroraDarkBackground = aniviewDarkBg
    val auroraDarkSurface = aniviewSurface
    val auroraDarkGradientStart = Color(0xFF241B52)
    val auroraDarkGradientEnd = aniviewDarkBg

    val auroraLightBackground = Color(0xFFf8fafc)
    val auroraLightSurface = Color(0xFFffffff)
    val auroraLightGradientStart = Color(0xFFe0e7ff)
    val auroraLightGradientEnd = Color(0xFFf8fafc)

    val auroraGlass = Color.White.copy(alpha = 0.26f)
    val auroraGlassLight = Color(0x1A000000)

    override val darkScheme = darkColorScheme(
        primary = aniviewElectricBlue,
        onPrimary = Color(0xFF171032),
        primaryContainer = Color(0xFF352A7D),
        onPrimaryContainer = Color(0xFFDED6FF),

        secondary = aniviewCyan,
        onSecondary = Color.Black,
        secondaryContainer = Color(0xFF0F3B57),
        onSecondaryContainer = Color(0xFFA9ECFF),

        tertiary = aniviewPurple,
        onTertiary = Color(0xFF2A0A3F),
        tertiaryContainer = Color(0xFF4A1D6E),
        onTertiaryContainer = Color(0xFFF5C9FF),

        background = aniviewDarkBg,
        onBackground = Color(0xFFECEAFD),

        surface = aniviewSurface,
        onSurface = Color(0xFFECEAFD),
        surfaceVariant = Color(0xFF232542),
        onSurfaceVariant = Color(0xFFA6A3CC),

        surfaceContainerLowest = Color(0xFF0B0C1B),
        surfaceContainerLow = Color(0xFF101225),
        surfaceContainer = aniviewSurface,
        surfaceContainerHigh = Color(0xFF1D1F40),
        surfaceContainerHighest = Color(0xFF272A55),

        outline = Color(0xFF3E3F6C),
        outlineVariant = Color(0xFF2B2D55),

        error = Color(0xFFf87171),
        onError = Color.White,
        errorContainer = Color(0xFF7f1d1d),
        onErrorContainer = Color(0xFFfecaca),

        inverseSurface = Color(0xFFe2e8f0),
        inverseOnSurface = Color(0xFF1e293b),
        inversePrimary = Color(0xFF6C5CE7),

        scrim = Color.Black,
    )

    override val lightScheme = lightColorScheme(
        primary = auroraAccentLight,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFe0f2fe),
        onPrimaryContainer = Color(0xFF0c4a6e),

        secondary = auroraAccentLight,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFe0f2fe),
        onSecondaryContainer = Color(0xFF0c4a6e),

        tertiary = Color(0xFF6366f1),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFe0e7ff),
        onTertiaryContainer = Color(0xFF3730a3),

        background = auroraLightBackground,
        onBackground = Color(0xFF0f172a),

        surface = auroraLightSurface,
        onSurface = Color(0xFF0f172a),
        surfaceVariant = Color(0xFFf1f5f9),
        onSurfaceVariant = Color(0xFF475569),

        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFf8fafc),
        surfaceContainer = Color(0xFFf1f5f9),
        surfaceContainerHigh = Color(0xFFe2e8f0),
        surfaceContainerHighest = Color(0xFFcbd5e1),

        outline = Color(0xFFcbd5e1),
        outlineVariant = Color(0xFFe2e8f0),

        error = Color(0xFFdc2626),
        onError = Color.White,
        errorContainer = Color(0xFFfee2e2),
        onErrorContainer = Color(0xFF991b1b),

        inverseSurface = Color(0xFF1e293b),
        inverseOnSurface = Color(0xFFf1f5f9),
        inversePrimary = Color(0xFF7dd3fc),

        scrim = Color.Black,
    )
}
