package eu.kanade.presentation.reader.novel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import eu.kanade.tachiyomi.source.novel.NovelPluginImage
import eu.kanade.tachiyomi.source.novel.NovelPluginImageResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal object NovelImageActionHelper {

    suspend fun resolveImageFile(
        context: Context,
        imageUrl: String,
    ): File? = withContext(Dispatchers.IO) {
        val trimmed = imageUrl.trim()
        if (trimmed.isBlank()) return@withContext null

        if (NovelPluginImage.isSupported(trimmed)) {
            val payload = NovelPluginImageResolver.resolve(trimmed) ?: return@withContext null
            val ext = if (payload.mimeType.contains("png")) ".png" else ".jpg"
            val file = File(context.cacheDir, "shared_novel_img_${trimmed.hashCode()}$ext")
            file.writeBytes(payload.bytes)
            file
        } else {
            null
        }
    }

    fun copyToClipboard(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard?.setPrimaryClip(clip)
    }

    fun shareImage(context: Context, imageUrl: String, resolvedFile: File? = null) {
        if (resolvedFile != null && resolvedFile.exists()) {
            val uri = runCatching {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    resolvedFile,
                )
            }.getOrNull()

            if (uri != null) {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(
                    Intent.createChooser(intent, null).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
                return
            }
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, imageUrl)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(
            Intent.createChooser(intent, null).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }
}
