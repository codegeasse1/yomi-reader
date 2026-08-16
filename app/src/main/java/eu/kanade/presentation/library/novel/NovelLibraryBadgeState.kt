package eu.kanade.presentation.library.novel

internal data class NovelLibraryBadgeState(
    val showDownloaded: Boolean,
    val unreadCount: Long?,
    val language: String?,
)

internal fun resolveNovelLibraryBadgeState(
    item: NovelLibraryItem,
    showDownloadBadge: Boolean,
    downloadedNovelIds: Set<Long>,
    showUnreadBadge: Boolean,
    showLanguageBadge: Boolean,
    sourceLanguage: String,
): NovelLibraryBadgeState {
    val resolvedLanguage = sourceLanguage.ifBlank { item.sourceLanguage }
    return NovelLibraryBadgeState(
        showDownloaded = showDownloadBadge && (item.isDownloaded || item.id in downloadedNovelIds),
        unreadCount = item.unreadCount.takeIf { showUnreadBadge && it > 0L },
        language = resolvedLanguage.takeIf { showLanguageBadge && it.isNotBlank() },
    )
}
