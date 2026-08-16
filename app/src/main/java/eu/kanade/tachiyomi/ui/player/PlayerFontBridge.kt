package eu.kanade.tachiyomi.ui.player

import com.hippo.unifile.UniFile
import `is`.xyz.mpv.MPVLib
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.storage.service.StorageManager
import java.io.File
import java.io.IOException

internal object PlayerFontBridge {
    private const val MPV_FONTS_DIR = "fonts"

    /**
     * Copies the user fonts from the storage fonts directory into mpv's private fonts directory.
     *
     * Must run before [MPVLib] initializes so the subtitle renderer can register the fonts;
     * register them with [setFontsDirectories] after initialization.
     *
     * @return the mpv fonts directory that was staged.
     */
    fun copyFontsDirectory(storageManager: StorageManager, mpvDir: UniFile): File {
        // TODO: I think this is a bad hack.
        //  We need to find a way to let MPV directly access our fonts directory.
        val fontsDirectory = File(mpvDir.filePath!!, MPV_FONTS_DIR)
        if (!fontsDirectory.exists() && !fontsDirectory.mkdirs()) {
            error("Unable to create MPV fonts directory")
        }
        copyFontFiles(
            sourceFonts = storageManager.getFontsDirectory()?.listFiles()?.toList().orEmpty(),
            targetFontsDirectory = fontsDirectory,
        )
        return fontsDirectory
    }

    /** Registers the staged fonts directory with MPV; call only after MPVLib is initialized. */
    fun setFontsDirectories(fontsDirectory: File) {
        MPVLib.setPropertyString("sub-fonts-dir", fontsDirectory.absolutePath)
        MPVLib.setPropertyString("osd-fonts-dir", fontsDirectory.absolutePath)
    }

    internal fun copyFontFiles(
        sourceFonts: List<UniFile>,
        targetFontsDirectory: File,
    ) {
        if (!targetFontsDirectory.exists()) {
            targetFontsDirectory.mkdirs()
        }
        sourceFonts.forEach { font ->
            val fontName = font.name ?: return@forEach
            runCatching {
                val outFile = File(targetFontsDirectory, fontName)
                font.openInputStream().use { input ->
                    outFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }.onFailure { error ->
                if (error is IOException) {
                    logcat(LogPriority.WARN, error) {
                        "Skipping unreadable MPV font: $fontName"
                    }
                } else {
                    throw error
                }
            }
        }
    }
}
