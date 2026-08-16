package eu.kanade.tachiyomi.ui.reader.novel.setting

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NovelReaderTapZonesTest {

    @Test
    fun `zone index maps 3x3 grid row by row`() {
        val width = 300f
        val height = 600f
        assertEquals(0, resolveNovelReaderTapZoneIndex(10f, 10f, width, height))
        assertEquals(1, resolveNovelReaderTapZoneIndex(150f, 10f, width, height))
        assertEquals(2, resolveNovelReaderTapZoneIndex(290f, 10f, width, height))
        assertEquals(4, resolveNovelReaderTapZoneIndex(150f, 300f, width, height))
        assertEquals(6, resolveNovelReaderTapZoneIndex(10f, 590f, width, height))
        assertEquals(8, resolveNovelReaderTapZoneIndex(290f, 590f, width, height))
        assertEquals(0, resolveNovelReaderTapZoneIndex(0f, 0f, 0f, 0f))
    }

    @Test
    fun `serialization round trips`() {
        val serialized = serializeNovelReaderTapZoneActions(NovelReaderDefaultTapZoneActions)
        assertEquals(
            NovelReaderDefaultTapZoneActions,
            parseNovelReaderTapZoneActions(serialized),
        )
    }

    @Test
    fun `invalid serialized value falls back to defaults`() {
        assertEquals(NovelReaderDefaultTapZoneActions, parseNovelReaderTapZoneActions("garbage"))
        assertEquals(NovelReaderDefaultTapZoneActions, parseNovelReaderTapZoneActions("NONE,NONE"))
        assertEquals(NovelReaderDefaultTapZoneActions, parseNovelReaderTapZoneActions(""))
    }

    @Test
    fun `legacy fallback used when custom zones disabled`() {
        val left = resolveConfiguredNovelReaderTapAction(
            tapX = 10f,
            tapY = 10f,
            width = 300f,
            height = 600f,
            customTapZonesEnabled = false,
            tapZoneActions = NovelReaderDefaultTapZoneActions,
            tapToScrollEnabled = true,
        )
        assertEquals(NovelReaderTapZoneAction.BACKWARD, left)
        val center = resolveConfiguredNovelReaderTapAction(
            tapX = 150f,
            tapY = 10f,
            width = 300f,
            height = 600f,
            customTapZonesEnabled = false,
            tapZoneActions = NovelReaderDefaultTapZoneActions,
            tapToScrollEnabled = true,
        )
        assertEquals(NovelReaderTapZoneAction.TOGGLE_UI, center)
        val disabledTapToScroll = resolveConfiguredNovelReaderTapAction(
            tapX = 10f,
            tapY = 10f,
            width = 300f,
            height = 600f,
            customTapZonesEnabled = false,
            tapZoneActions = NovelReaderDefaultTapZoneActions,
            tapToScrollEnabled = false,
        )
        assertEquals(NovelReaderTapZoneAction.TOGGLE_UI, disabledTapToScroll)
    }

    @Test
    fun `custom zones honor configured actions including none`() {
        val zones = List(NOVEL_READER_TAP_ZONE_COUNT) { NovelReaderTapZoneAction.NONE }
        val action = resolveConfiguredNovelReaderTapAction(
            tapX = 150f,
            tapY = 300f,
            width = 300f,
            height = 600f,
            customTapZonesEnabled = true,
            tapZoneActions = zones,
            tapToScrollEnabled = true,
        )
        assertEquals(NovelReaderTapZoneAction.NONE, action)
        val corner = resolveConfiguredNovelReaderTapAction(
            tapX = 290f,
            tapY = 590f,
            width = 300f,
            height = 600f,
            customTapZonesEnabled = true,
            tapZoneActions = NovelReaderDefaultTapZoneActions,
            tapToScrollEnabled = false,
        )
        assertEquals(NovelReaderTapZoneAction.FORWARD, corner)
    }

    @Test
    fun `cycle iterates all actions and wraps around`() {
        var action = NovelReaderTapZoneAction.NONE
        val seen = mutableSetOf<NovelReaderTapZoneAction>()
        repeat(NovelReaderTapZoneAction.entries.size) {
            seen += action
            action = cycleNovelReaderTapZoneAction(action)
        }
        assertEquals(NovelReaderTapZoneAction.entries.toSet(), seen)
        assertEquals(NovelReaderTapZoneAction.NONE, action)
    }
}
