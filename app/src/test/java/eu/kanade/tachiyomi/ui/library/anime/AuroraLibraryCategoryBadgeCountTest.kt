package eu.kanade.tachiyomi.ui.library.anime

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.Locale

class AuroraLibraryCategoryBadgeCountTest {

    @Test
    fun `caps the count at 99 plus when full count is disabled`() {
        formatAuroraLibraryCategoryBadgeCount(
            count = 100,
            showFullCount = false,
            groupDigits = false,
        ) shouldBe "99+"
    }

    @Test
    fun `keeps counts up to 99 untouched when full count is disabled`() {
        formatAuroraLibraryCategoryBadgeCount(
            count = 99,
            showFullCount = false,
            groupDigits = false,
        ) shouldBe "99"
    }

    @Test
    fun `shows the exact count when full count is enabled`() {
        formatAuroraLibraryCategoryBadgeCount(
            count = 123,
            showFullCount = true,
            groupDigits = false,
        ) shouldBe "123"
    }

    @Test
    fun `does not group digits when grouping is disabled`() {
        formatAuroraLibraryCategoryBadgeCount(
            count = 1234,
            showFullCount = true,
            groupDigits = false,
        ) shouldBe "1234"
    }

    @Test
    fun `groups digits using the locale separator`() {
        formatAuroraLibraryCategoryBadgeCount(
            count = 1234,
            showFullCount = true,
            groupDigits = true,
            locale = Locale.US,
        ) shouldBe "1,234"
    }

    @Test
    fun `groups digits with a space separator for russian locale`() {
        val formatted = formatAuroraLibraryCategoryBadgeCount(
            count = 1234,
            showFullCount = true,
            groupDigits = true,
            locale = Locale.forLanguageTag("ru-RU"),
        )

        formatted.length shouldBe 5
        formatted.filter { it.isDigit() } shouldBe "1234"
        formatted[1].isWhitespace() shouldBe true
    }

    @Test
    fun `ignores grouping while the count is still capped`() {
        formatAuroraLibraryCategoryBadgeCount(
            count = 1234,
            showFullCount = false,
            groupDigits = true,
        ) shouldBe "99+"
    }

    @Test
    fun `leaves short counts unchanged when grouping is enabled`() {
        formatAuroraLibraryCategoryBadgeCount(
            count = 42,
            showFullCount = true,
            groupDigits = true,
            locale = Locale.US,
        ) shouldBe "42"
    }
}
