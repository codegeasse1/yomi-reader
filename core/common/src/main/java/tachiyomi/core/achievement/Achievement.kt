package tachiyomi.core.achievement

import dev.icerock.moko.resources.StringResource

data class Achievement(
    val id: String,
    val titleRes: StringResource,
    val descriptionRes: StringResource,
    val condition: AchievementCondition,
    val reward: AchievementReward,
    val isHidden: Boolean = false,
)

sealed class AchievementCondition {
    data class ChaptersRead(val count: Int) : AchievementCondition()
    data class LibrarySize(val count: Int) : AchievementCondition()
    data class Streak(val days: Int) : AchievementCondition()
    data class NightReading(val startHour: Int, val endHour: Int) : AchievementCondition()
    data object HiddenDiscovery : AchievementCondition()
}

sealed class AchievementReward {
    data class Theme(val themeId: String) : AchievementReward()
    data class Aura(val auraId: String) : AchievementReward()
    data class AppIcon(val iconId: String) : AchievementReward()
    data class Title(val title: String) : AchievementReward()
}
