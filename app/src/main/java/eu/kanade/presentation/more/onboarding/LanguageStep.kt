package eu.kanade.presentation.more.onboarding

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.yomi.reader.R
import eu.kanade.presentation.more.resolveAuroraMoreCardBorderColor
import eu.kanade.presentation.theme.AuroraSurfaceLevel
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.resolveAuroraElevation
import eu.kanade.presentation.theme.resolveAuroraSurfaceColor
import eu.kanade.tachiyomi.util.system.LocaleHelper
import org.xmlpull.v1.XmlPullParser
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

internal class LanguageStep : OnboardingStep {

    override val isComplete: Boolean = true

    private data class Language(
        val langTag: String,
        val displayName: String,
        val localizedDisplayName: String?,
    )

    private fun getLangs(context: Context): List<Language> {
        val langs = mutableListOf<Language>()
        val parser = context.resources.getXml(R.xml.locales_config)
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "locale") {
                for (i in 0..<parser.attributeCount) {
                    if (parser.getAttributeName(i) == "name") {
                        val langTag = parser.getAttributeValue(i)
                        val displayName = LocaleHelper.getLocalizedDisplayName(langTag)
                        if (displayName.isNotEmpty()) {
                            langs.add(
                                Language(
                                    langTag,
                                    displayName,
                                    LocaleHelper.getDisplayName(langTag),
                                ),
                            )
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        val uniqueLangs = langs.distinctBy { it.langTag }.sortedBy { it.displayName }
        return uniqueLangs.toMutableList().apply {
            add(0, Language("", context.stringResource(MR.strings.label_default), null))
        }
    }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val langs = remember { getLangs(context) }

        var currentLanguage by remember {
            mutableStateOf(
                AppCompatDelegate.getApplicationLocales().get(0)?.toLanguageTag() ?: "",
            )
        }

        val colors = AuroraTheme.colors

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(MR.strings.onboarding_language_title),
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
            )

            Text(
                text = stringResource(MR.strings.onboarding_language_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                langs.forEach { lang ->
                    val isSelected = currentLanguage == lang.langTag

                    // Interactive spring scale states
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val scale by animateFloatAsState(
                        targetValue = if (isPressed) 0.96f else 1f,
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                            stiffness = androidx.compose.animation.core.Spring.StiffnessLow,
                        ),
                        label = "lang_${lang.langTag}_scale",
                    )

                    // Resolve card container and border colors
                    val cardBgColor = when {
                        colors.isEInk -> if (isSelected) {
                            resolveAuroraSurfaceColor(colors, AuroraSurfaceLevel.Strong)
                        } else {
                            resolveAuroraSurfaceColor(colors, AuroraSurfaceLevel.Subtle)
                        }
                        colors.isDark -> if (isSelected) {
                            colors.accent.copy(alpha = 0.18f)
                        } else {
                            Color.White.copy(alpha = 0.05f)
                        }
                        else -> if (isSelected) {
                            colors.accent.copy(alpha = 0.12f)
                        } else {
                            Color.White
                        }
                    }

                    val cardBorderColor = when {
                        colors.isEInk -> if (isSelected) {
                            colors.accent
                        } else {
                            resolveAuroraMoreCardBorderColor(colors)
                        }
                        colors.isDark -> if (isSelected) {
                            colors.accent.copy(alpha = 0.5f)
                        } else {
                            Color.White.copy(alpha = 0.08f)
                        }
                        else -> if (isSelected) {
                            colors.accent.copy(alpha = 0.35f)
                        } else {
                            Color.Transparent
                        }
                    }

                    val cardElevation = if (!colors.isDark && !colors.isEInk && !isSelected) {
                        resolveAuroraElevation(colors, AuroraSurfaceLevel.Glass)
                    } else {
                        0.dp
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .clickable(
                                interactionSource = interactionSource,
                                indication = androidx.compose.foundation.LocalIndication.current,
                                onClick = {
                                    currentLanguage = lang.langTag
                                    val locale = if (lang.langTag.isEmpty()) {
                                        LocaleListCompat.getEmptyLocaleList()
                                    } else {
                                        LocaleListCompat.forLanguageTags(lang.langTag)
                                    }
                                    AppCompatDelegate.setApplicationLocales(locale)
                                },
                            ),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = cardBgColor,
                        ),
                        border = BorderStroke(
                            width = if (isSelected && !colors.isEInk) 1.5.dp else 1.dp,
                            color = cardBorderColor,
                        ),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = cardElevation,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = lang.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.textPrimary,
                                )
                                lang.localizedDisplayName?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textSecondary,
                                    )
                                }
                            }
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.accent.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = colors.accent,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
