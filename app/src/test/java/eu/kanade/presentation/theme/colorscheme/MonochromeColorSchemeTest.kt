package eu.kanade.presentation.theme.colorscheme

import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

/**
 * Regression guards for theme contrast bugs:
 * - https://github.com/andarcanum/Tadami-Aniyomi-fork/issues/170 (player buffer bar invisible in Monochrome)
 * - https://github.com/andarcanum/Tadami-Aniyomi-fork/issues/171 (chapter progress bar looks full in Monochrome)
 * - https://github.com/andarcanum/Tadami-Aniyomi-fork/issues/172 (volume slider fill invisible in Yin & Yang)
 */
class MonochromeColorSchemeTest {

    @Test
    fun `monochrome outline variant differs from primary so progress tracks stay visible`() {
        MonochromeColorScheme.lightScheme.outlineVariant shouldNotBe MonochromeColorScheme.lightScheme.primary
        MonochromeColorScheme.darkScheme.outlineVariant shouldNotBe MonochromeColorScheme.darkScheme.primary
    }

    @Test
    fun `monochrome inverse primary contrasts with background so player buffer stays visible`() {
        MonochromeColorScheme.lightScheme.inversePrimary shouldNotBe MonochromeColorScheme.lightScheme.background
        MonochromeColorScheme.darkScheme.inversePrimary shouldNotBe MonochromeColorScheme.darkScheme.background
    }

    @Test
    fun `yinyang primary contrasts with background so vertical volume slider fill stays visible`() {
        YinYangColorScheme.lightScheme.primary shouldNotBe YinYangColorScheme.lightScheme.background
        YinYangColorScheme.darkScheme.primary shouldNotBe YinYangColorScheme.darkScheme.background
    }
}
