package eu.kanade.tachiyomi.extension

import android.content.pm.PackageInfo

/**
 * Which media type a legacy private extension APK belongs to.
 *
 * The private extension directory used to be shared, and the split migration classified files by
 * looking for ".anime" in the file name. That misroutes real manga extensions whose package segment
 * happens to start with "anime" - `eu.kanade.tachiyomi.extension.fr.animesama`,
 * `...it.animegdrclub` and `...pt.animexnovel` all exist - and a misrouted file is silently lost,
 * because the receiving loader rejects it by manifest feature and never looks in the other directory.
 *
 * The manifest feature is the authoritative marker, so classify by that instead. A file we cannot
 * read stays where it is rather than being moved somewhere it will never be loaded from.
 */
internal fun matchesExtensionFeature(pkgInfo: PackageInfo?, feature: String): Boolean? {
    val features = pkgInfo?.reqFeatures ?: return null
    return features.any { it.name == feature }
}
