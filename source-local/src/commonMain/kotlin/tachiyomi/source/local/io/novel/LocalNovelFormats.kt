package tachiyomi.source.local.io.novel

/**
 * Supported on-disk formats for [LocalNovelSource].
 * Keep browse listing and chapter collection in lockstep — unsupported files
 * (notably PDF) must never create ghost chapters that history can latch onto.
 */
object LocalNovelFormats {
    val SUPPORTED_EXTENSIONS: Set<String> = setOf(
        "txt", "text",
        "md", "markdown",
        "html", "htm", "xhtml",
        "epub",
        "fb2",
        "zip", "cbz",
        "rar", "cbr",
    )

    fun isSupportedExtension(extension: String?): Boolean {
        val ext = extension?.lowercase()?.trim().orEmpty()
        return ext.isNotEmpty() && ext in SUPPORTED_EXTENSIONS
    }

    fun isSupportedFileName(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val ext = name.substringAfterLast('.', missingDelimiterValue = "")
        if (ext.isEmpty() || ext == name) return false
        return isSupportedExtension(ext)
    }
}
