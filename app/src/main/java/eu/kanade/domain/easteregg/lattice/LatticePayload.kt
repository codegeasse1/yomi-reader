package eu.kanade.domain.easteregg.lattice

import org.json.JSONObject

/**
 * Расшифрованный payload стадии B. Не несёт отображаемых строк:
 * весь пользовательский текст живёт в i18n.
 */
data class LatticePayload(
    val achievementId: String?,
    val bonusPoints: Int?,
    val themeId: String?,
    val unlockables: List<String>,
) {
    companion object {
        fun fromJson(raw: String): LatticePayload? = try {
            val obj = JSONObject(raw)
            LatticePayload(
                achievementId = obj.optString("achievementId").takeIf { it.isNotEmpty() },
                bonusPoints = if (obj.has("bonusPoints")) obj.optInt("bonusPoints") else null,
                themeId = obj.optString("themeId").takeIf { it.isNotEmpty() },
                unlockables = obj.optJSONArray("unlockables")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList(),
            )
        } catch (e: Exception) {
            null
        }
    }
}
