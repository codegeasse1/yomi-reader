package tachiyomi.domain.achievement.model

import androidx.compose.runtime.Immutable

@Immutable
data class UserPoints(
    val totalPoints: Int = 0,
    val level: Int = 1,
    val achievementsUnlocked: Int = 0,
)
