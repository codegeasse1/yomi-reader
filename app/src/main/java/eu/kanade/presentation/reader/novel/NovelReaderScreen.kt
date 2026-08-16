package eu.kanade.presentation.reader.novel

import androidx.compose.runtime.Composable
import eu.kanade.tachiyomi.ui.reader.novel.BookSeekRequest
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookLocation
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookSpine
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookWindowState
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreenModel

@Composable
fun NovelReaderScreen(
    rawState: NovelReaderScreenModel.State.Success,
    showReaderUi: Boolean,
    bookEngineSpine: NovelBookSpine = NovelBookSpine.EMPTY,
    bookInitialLocation: NovelBookLocation = NovelBookLocation.START,
    bookSeekRequest: BookSeekRequest? = null,
    bookWindow: NovelBookWindowState = NovelBookWindowState.EMPTY,
    actions: NovelReaderScreenActions,
) {
    NovelReaderContentHost(
        rawState = rawState,
        showReaderUi = showReaderUi,
        bookEngineSpine = bookEngineSpine,
        bookInitialLocation = bookInitialLocation,
        bookSeekRequest = bookSeekRequest,
        bookWindow = bookWindow,
        actions = actions,
    )
}
