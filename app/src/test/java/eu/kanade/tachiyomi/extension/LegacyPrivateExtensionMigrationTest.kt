package eu.kanade.tachiyomi.extension

import android.content.pm.FeatureInfo
import android.content.pm.PackageInfo
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class LegacyPrivateExtensionMigrationTest {

    private fun pkgInfo(vararg features: String) = PackageInfo().apply {
        reqFeatures = features.map { FeatureInfo().apply { name = it } }.toTypedArray()
    }

    @Test
    fun `a manga extension whose package contains anime is still manga`() {
        // Real published extensions: eu.kanade.tachiyomi.extension.fr.animesama, it.animegdrclub,
        // pt.animexnovel - all manga, all matched by the old pkgName.contains(".anime") check.
        val info = pkgInfo("tachiyomi.extension")

        matchesExtensionFeature(info, "tachiyomi.extension") shouldBe true
        matchesExtensionFeature(info, "tachiyomi.animeextension") shouldBe false
    }

    @Test
    fun `an anime extension matches only the anime feature`() {
        val info = pkgInfo("tachiyomi.animeextension")

        matchesExtensionFeature(info, "tachiyomi.animeextension") shouldBe true
        matchesExtensionFeature(info, "tachiyomi.extension") shouldBe false
    }

    @Test
    fun `an unreadable archive is left where it is instead of being moved`() {
        matchesExtensionFeature(null, "tachiyomi.extension") shouldBe null
        matchesExtensionFeature(PackageInfo(), "tachiyomi.extension") shouldBe null
    }
}
