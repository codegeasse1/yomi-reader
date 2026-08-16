package eu.kanade.tachiyomi.ui.player

/**
 * Builds the mpv `vf` option value from the decoder preferences that map to libavfilter.
 * Returns null when no software filter is needed (GPU debanding uses the `deband` option,
 * not the vf chain).
 */
fun buildVideoFilterChain(debanding: Debanding, useYuv420p: Boolean): String? {
    val filters = buildList {
        when (debanding) {
            Debanding.CPU -> add("gradfun=radius=12")
            Debanding.GPU, Debanding.None -> {}
        }
        if (useYuv420p) add("format=yuv420p")
    }
    return filters.takeIf { it.isNotEmpty() }?.joinToString(",")
}
