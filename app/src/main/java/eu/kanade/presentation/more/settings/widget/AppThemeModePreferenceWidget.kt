package eu.kanade.presentation.more.settings.widget

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.domain.ui.model.ThemeMode
import eu.kanade.presentation.more.resolveAuroraMoreCardBorderColor
import eu.kanade.presentation.more.settings.LocalSettingsUiStyle
import eu.kanade.presentation.more.settings.SettingsUiStyle
import eu.kanade.presentation.theme.AuroraSurfaceLevel
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.resolveAuroraElevation
import eu.kanade.presentation.theme.resolveAuroraSurfaceColor
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics

private val options = mapOf(
    ThemeMode.SYSTEM to MR.strings.theme_system,
    ThemeMode.LIGHT to MR.strings.theme_light,
    ThemeMode.DARK to MR.strings.theme_dark,
)

@Composable
internal fun AppThemeModePreferenceWidget(
    value: ThemeMode,
    onItemClick: (ThemeMode) -> Unit,
) {
    val appHaptics = LocalAppHaptics.current
    val isAurora = LocalSettingsUiStyle.current == SettingsUiStyle.Aurora

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PrefsHorizontalPadding, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (mode, labelRes) ->
            val isSelected = mode == value

            // Interactive spring-scale states
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (isPressed) 0.96f else 1f,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessLow,
                ),
                label = "theme_mode_${mode.name}_scale",
            )

            val cardShape = RoundedCornerShape(12.dp)

            if (isAurora) {
                val auroraColors = AuroraTheme.colors
                val cardBgColor = when {
                    auroraColors.isEInk -> if (isSelected) {
                        resolveAuroraSurfaceColor(auroraColors, AuroraSurfaceLevel.Strong)
                    } else {
                        resolveAuroraSurfaceColor(auroraColors, AuroraSurfaceLevel.Subtle)
                    }
                    auroraColors.isDark -> if (isSelected) {
                        auroraColors.accent.copy(alpha = 0.18f)
                    } else {
                        Color.White.copy(alpha = 0.05f)
                    }
                    else -> if (isSelected) {
                        auroraColors.accent.copy(alpha = 0.12f)
                    } else {
                        Color.White
                    }
                }

                val cardBorderColor = when {
                    auroraColors.isEInk -> if (isSelected) {
                        auroraColors.accent
                    } else {
                        resolveAuroraMoreCardBorderColor(auroraColors)
                    }
                    auroraColors.isDark -> if (isSelected) {
                        auroraColors.accent.copy(alpha = 0.5f)
                    } else {
                        Color.White.copy(alpha = 0.08f)
                    }
                    else -> if (isSelected) {
                        auroraColors.accent.copy(alpha = 0.35f)
                    } else {
                        Color.Transparent
                    }
                }

                val textColor = if (isSelected) {
                    if (auroraColors.isDark || auroraColors.isEInk) auroraColors.accent else auroraColors.textPrimary
                } else {
                    auroraColors.textSecondary
                }

                Card(
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clickable(
                            interactionSource = interactionSource,
                            indication = androidx.compose.foundation.LocalIndication.current,
                            onClick = {
                                appHaptics.tap()
                                onItemClick(mode)
                            },
                        ),
                    shape = cardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = cardBgColor,
                    ),
                    border = BorderStroke(
                        width = if (isSelected && !auroraColors.isEInk) 1.5.dp else 1.dp,
                        color = cardBorderColor,
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = if (!auroraColors.isDark && !auroraColors.isEInk && !isSelected) {
                            resolveAuroraElevation(auroraColors, AuroraSurfaceLevel.Glass)
                        } else {
                            0.dp
                        },
                    ),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(labelRes),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                letterSpacing = 0.1.sp,
                            ),
                            color = textColor,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clickable(
                            interactionSource = interactionSource,
                            indication = androidx.compose.foundation.LocalIndication.current,
                            onClick = {
                                appHaptics.tap()
                                onItemClick(mode)
                            },
                        ),
                    shape = cardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                    ),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(labelRes),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                letterSpacing = 0.1.sp,
                            ),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
