package eu.kanade.tachiyomi.ui.reader.viewer.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CustomTapZonesTest {

    @Test
    fun `default layout parses to nine known tokens`() {
        val tokens = parseCustomTapZoneTokens(CUSTOM_TAP_ZONES_DEFAULT)
        assertEquals(CUSTOM_TAP_ZONE_COUNT, tokens.size)
        assertEquals("MENU", tokens[4])
        tokens.forEach { assertEquals(true, it in CustomTapZoneTokens) }
    }

    @Test
    fun `serialization round trip keeps tokens`() {
        val tokens = listOf("NONE", "MENU", "PREV", "NEXT", "LEFT", "RIGHT", "NONE", "NEXT", "PREV")
        assertEquals(tokens, parseCustomTapZoneTokens(serializeCustomTapZoneTokens(tokens)))
    }

    @Test
    fun `invalid serialized value falls back to default`() {
        val expected = CUSTOM_TAP_ZONES_DEFAULT.split(",")
        assertEquals(expected, parseCustomTapZoneTokens(""))
        assertEquals(expected, parseCustomTapZoneTokens("MENU,FOO"))
        assertEquals(expected, parseCustomTapZoneTokens("MENU,MENU,MENU"))
    }

    @Test
    fun `cycle walks through every token and wraps`() {
        var token = CustomTapZoneTokens.first()
        val seen = mutableListOf<String>()
        repeat(CustomTapZoneTokens.size) {
            seen += token
            token = cycleCustomTapZoneToken(token)
        }
        assertEquals(CustomTapZoneTokens, seen)
        assertEquals(CustomTapZoneTokens.first(), token)
    }
}
