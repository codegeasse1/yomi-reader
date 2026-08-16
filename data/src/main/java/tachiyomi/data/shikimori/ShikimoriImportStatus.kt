package tachiyomi.data.shikimori

enum class ShikimoriImportStatus(val apiValue: String) {
    WATCHING("watching"),
    READING("reading"),
    COMPLETED("completed"),
    PLANNED("planned"),
    ON_HOLD("on_hold"),
    DROPPED("dropped"),
    REWATCHING("rewatching"),
    REREADING("rereading"),
    ;

    companion object {
        fun fromApi(value: String?): ShikimoriImportStatus? {
            val key = value?.trim()?.lowercase().orEmpty()
            if (key.isEmpty()) return null
            return entries.firstOrNull { it.apiValue == key }
        }

        fun forMediaType(mediaType: ShikimoriImportMediaType): List<ShikimoriImportStatus> = when (mediaType) {
            ShikimoriImportMediaType.ANIME -> listOf(
                WATCHING,
                COMPLETED,
                PLANNED,
                ON_HOLD,
                DROPPED,
                REWATCHING,
            )
            ShikimoriImportMediaType.MANGA,
            ShikimoriImportMediaType.RANOBE,
            -> listOf(
                READING,
                COMPLETED,
                PLANNED,
                ON_HOLD,
                DROPPED,
                REREADING,
            )
        }
    }
}
