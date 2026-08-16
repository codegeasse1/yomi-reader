package eu.kanade.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.presentation.theme.colorscheme.AuroraColorScheme
import eu.kanade.presentation.theme.colorscheme.BaseColorScheme
import org.junit.jupiter.api.Test
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Уникальность наградных (achievement) тем.
 *
 * Требование: каждая тема, выдаваемая за достижения, должна быть уникальна —
 * не пересекаться ни с одной другой наградной темой и ни с одной встроенной.
 * Порог: CIE76 ΔE > 15 для одноимённых ролей (primary/secondary/tertiary),
 * в тёмной и светлой схеме. MONET исключён (динамическая палитра обоев).
 */
private val ACHIEVEMENT_THEMES = listOf(
    AppTheme.ONYX_GOLD,
    AppTheme.SAKURA_NOIR,
    AppTheme.NEBULA_TIDE,
    AppTheme.EVENT_HORIZON,
    AppTheme.VOID_RED,
    AppTheme.AURORA_PRIME,
    AppTheme.LATTICE_PROTOCOL,
)

private val ROLE_NAMES = listOf("primary", "secondary", "tertiary")

class ThemeUniquenessTest {

    private fun ColorScheme.roleTokens(): List<Color> = listOf(primary, secondary, tertiary)

    private fun Color.toLab(): Triple<Double, Double, Double> {
        fun lin(c: Float): Double {
            val cc = c.toDouble()
            return if (cc <= 0.04045) cc / 12.92 else ((cc + 0.055) / 1.055).pow(2.4)
        }
        val r = lin(red)
        val g = lin(green)
        val b = lin(blue)
        val x = (r * 0.4124 + g * 0.3576 + b * 0.1805) / 0.95047
        val y = r * 0.2126 + g * 0.7152 + b * 0.0722
        val z = (r * 0.0193 + g * 0.1192 + b * 0.9505) / 1.08883
        fun f(t: Double) = if (t > 0.008856) t.pow(1.0 / 3.0) else 7.787 * t + 16.0 / 116.0
        val fx = f(x)
        val fy = f(y)
        val fz = f(z)
        return Triple(116.0 * fy - 16.0, 500.0 * (fx - fy), 200.0 * (fy - fz))
    }

    private fun deltaE(a: Color, b: Color): Double {
        val la = a.toLab()
        val lb = b.toLab()
        return sqrt(
            (la.first - lb.first).pow(2) +
                (la.second - lb.second).pow(2) +
                (la.third - lb.third).pow(2),
        )
    }

    private fun violationsFor(isDark: Boolean): List<String> {
        val violations = mutableListOf<String>()
        val mode = if (isDark) "dark" else "light"
        val allSchemes = colorSchemes.values.distinct()
        for (achTheme in ACHIEVEMENT_THEMES) {
            val achScheme = colorSchemes.getValue(achTheme)
            val ach = achScheme.getColorScheme(isDark = isDark, isAmoled = false).roleTokens()
            for (other in allSchemes) {
                if (other === achScheme) continue
                val otherTokens = other.getColorScheme(isDark = isDark, isAmoled = false).roleTokens()
                for (role in ROLE_NAMES.indices) {
                    val d = deltaE(ach[role], otherTokens[role])
                    if (d <= 15.0) {
                        violations += "$achTheme.${ROLE_NAMES[role]} ($mode) ΔE=${
                            String.format("%.1f", d)
                        } vs ${schemeNames[other]}"
                    }
                }
            }
        }
        return violations
    }

    @Test
    fun `achievement themes are unique from every other theme in dark mode`() {
        val violations = violationsFor(isDark = true)
        check(violations.isEmpty()) { violations.joinToString("\n") }
    }

    @Test
    fun `achievement themes are unique from every other theme in light mode`() {
        val violations = violationsFor(isDark = false)
        check(violations.isEmpty()) { violations.joinToString("\n") }
    }

    @Test
    fun `aurora prime has its own base scheme and is not the built-in aurora`() {
        check(colorSchemes.getValue(AppTheme.AURORA_PRIME) !== AuroraColorScheme) {
            "AURORA_PRIME must not reuse AuroraColorScheme as its base"
        }
    }

    @Test
    fun `aurora prime live palette stays unique in dark mode`() {
        // Visible AURORA_PRIME colors come from the payload defaults (AuroraPublicPalette):
        // Green=primary, Violet=secondary, Blue=tertiary.
        val violations = mutableListOf<String>()
        val live = listOf(
            eu.kanade.presentation.easteregg.aurora.AuroraPublicPalette.Green,
            eu.kanade.presentation.easteregg.aurora.AuroraPublicPalette.Violet,
            eu.kanade.presentation.easteregg.aurora.AuroraPublicPalette.Blue,
        )
        val ownScheme = colorSchemes.getValue(AppTheme.AURORA_PRIME)
        for (other in colorSchemes.values.distinct()) {
            if (other === ownScheme) continue
            val otherTokens = other.getColorScheme(isDark = true, isAmoled = false).roleTokens()
            for (role in ROLE_NAMES.indices) {
                val d = deltaE(live[role], otherTokens[role])
                if (d <= 15.0) {
                    violations += "AURORA_PRIME-live.${ROLE_NAMES[role]} (dark) ΔE=${
                        String.format("%.1f", d)
                    } vs ${schemeNames[other]}"
                }
            }
        }
        check(violations.isEmpty()) { violations.joinToString("\n") }
    }

    @Test
    fun `lattice live palette endpoints stay unique in dark mode`() {
        // Visible LATTICE_PROTOCOL colors breathe between the live endpoints.
        val palette = eu.kanade.presentation.easteregg.lattice.LatticeProtocolLivePalette
        val primaryEndpoints = listOf(palette.cyanDeep, palette.cyanCore)
        val secondaryEndpoints = listOf(palette.cyanDeep, palette.cyanCore)
        val tertiaryEndpoints = listOf(palette.amberSoft, palette.amber)
        val violations = mutableListOf<String>()
        val ownScheme = colorSchemes.getValue(AppTheme.LATTICE_PROTOCOL)
        for (other in colorSchemes.values.distinct()) {
            if (other === ownScheme) continue
            val otherTokens = other.getColorScheme(isDark = true, isAmoled = false).roleTokens()
            for (endpoint in primaryEndpoints) {
                val d = deltaE(endpoint, otherTokens[0])
                if (d <=
                    15.0
                ) {
                    violations +=
                        "LATTICE-live.primary (${hex(
                            endpoint,
                        )}) vs ${schemeNames[other]} ΔE=${String.format("%.1f", d)}"
                }
            }
            for (endpoint in secondaryEndpoints) {
                val d = deltaE(endpoint, otherTokens[1])
                if (d <=
                    15.0
                ) {
                    violations +=
                        "LATTICE-live.secondary (${hex(
                            endpoint,
                        )}) vs ${schemeNames[other]} ΔE=${String.format("%.1f", d)}"
                }
            }
            for (endpoint in tertiaryEndpoints) {
                val d = deltaE(endpoint, otherTokens[2])
                if (d <=
                    15.0
                ) {
                    violations +=
                        "LATTICE-live.tertiary (${hex(
                            endpoint,
                        )}) vs ${schemeNames[other]} ΔE=${String.format("%.1f", d)}"
                }
            }
        }
        check(violations.isEmpty()) { violations.joinToString("\n") }
    }

    @Test
    fun `achievement palettes have no duplicate role tokens`() {
        val violations = mutableListOf<String>()
        for (achTheme in ACHIEVEMENT_THEMES) {
            val scheme = colorSchemes.getValue(achTheme)
            for (isDark in listOf(true, false)) {
                val tokens = scheme.getColorScheme(isDark = isDark, isAmoled = false).roleTokens()
                for (i in ROLE_NAMES.indices) {
                    for (j in i + 1 until ROLE_NAMES.size) {
                        if (tokens[i] == tokens[j]) {
                            violations += "$achTheme (${if (isDark) "dark" else "light"}): " +
                                "${ROLE_NAMES[i]} == ${ROLE_NAMES[j]}"
                        }
                    }
                }
            }
        }
        check(violations.isEmpty()) { violations.joinToString("\n") }
    }

    private val schemeNames: Map<BaseColorScheme?, String> by lazy {
        val names = mutableMapOf<BaseColorScheme?, String>()
        colorSchemes.forEach { (theme, scheme) ->
            names[scheme] = if (scheme in names) names.getValue(scheme) + "/" + theme.name else theme.name
        }
        names
    }
}

private fun hex(color: Color): String = "#" + color.value.toString(16).padStart(8, '0')
