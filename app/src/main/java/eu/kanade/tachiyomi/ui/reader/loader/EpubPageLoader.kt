package eu.kanade.tachiyomi.ui.reader.loader

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import mihon.core.archive.EpubReader
import java.io.IOException

/**
 * Loader used to load a chapter from a .epub file.
 */
internal class EpubPageLoader(private val reader: EpubReader) : PageLoader() {

    override var isLocal: Boolean = true

    override suspend fun getPages(): List<ReaderPage> {
        return reader.getImagesFromPages().mapIndexed { i, path ->
            ReaderPage(i).apply {
                stream = {
                    reader.getInputStream(path)
                        ?: throw IOException("Epub reader is no longer available")
                }
                status = Page.State.READY
            }
        }
    }

    override suspend fun loadPage(page: ReaderPage) {
        if (isRecycled) return
    }

    override fun recycle() {
        super.recycle()
        reader.close()
    }
}
