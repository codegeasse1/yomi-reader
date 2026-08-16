package eu.kanade.tachiyomi.ui.reader.viewer.navigation

const val CUSTOM_TAP_ZONE_COUNT = 9

const val CUSTOM_TAP_ZONES_DEFAULT = "PREV,PREV,NEXT,PREV,MENU,NEXT,PREV,NEXT,NEXT"

val CustomTapZoneTokens = listOf("NONE", "MENU", "PREV", "NEXT", "LEFT", "RIGHT")

fun parseCustomTapZoneTokens(serialized: String): List<String> {
    val tokens = serialized.split(",").map { it.trim() }
    if (tokens.size != CUSTOM_TAP_ZONE_COUNT || tokens.any { it !in CustomTapZoneTokens }) {
        return CUSTOM_TAP_ZONES_DEFAULT.split(",")
    }
    return tokens
}

fun serializeCustomTapZoneTokens(tokens: List<String>): String = tokens.joinToString(",")

fun cycleCustomTapZoneToken(token: String): String {
    val index = CustomTapZoneTokens.indexOf(token)
    return CustomTapZoneTokens[(index + 1) % CustomTapZoneTokens.size]
}
