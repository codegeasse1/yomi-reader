package eu.kanade.presentation.more.settings.screen

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DebugUnlockablePreviewTest {

    @Test
    fun `debug build shows treasury even without unlocked rewards`() {
        shouldShowTreasury(
            isDebugBuild = true,
            unlockedUnlockables = emptySet(),
        ) shouldBe true
    }

    @Test
    fun `release build hides treasury when nothing is unlocked`() {
        shouldShowTreasury(
            isDebugBuild = false,
            unlockedUnlockables = emptySet(),
        ) shouldBe false
    }

    @Test
    fun `debug build exposes preview themes and auras`() {
        visibleUnlockablesForTreasuryPreview(
            debugBypassLocks = true,
            unlockedUnlockables = setOf("badge_week_warrior"),
        ).shouldContainAll(
            "badge_week_warrior",
            "theme_ONYX_GOLD",
            "theme_onyx_gold",
            "aura_harem",
            "aura_level_up",
            "aura_matrix",
            "profile_nickname_effect_aurora_crown",
            "profile_nickname_effect_glitch_rune",
            "profile_nickname_effect_cipher",
            "avatar_frame_neon",
            "avatar_frame_hologram",
            "avatar_frame_prismatic",
            "home_badge_orbit",
            "home_badge_crown",
            "home_badge_shuriken",
            "special_background_petal_storm",
            "special_background_neon_orbit",
            "theme_SAKURA_NOIR",
            "theme_NEBULA_TIDE",
        )
    }

    @Test
    fun `release build keeps only real unlockables`() {
        visibleUnlockablesForTreasuryPreview(
            debugBypassLocks = false,
            unlockedUnlockables = setOf("badge_week_warrior"),
        ).shouldContainExactly("badge_week_warrior")
    }
}
