package eu.kanade.presentation.more.onboarding

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.more.resolveAuroraMoreCardBorderColor
import eu.kanade.presentation.more.resolveAuroraMoreCardContainerColor
import eu.kanade.presentation.theme.AuroraSurfaceLevel
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import eu.kanade.presentation.theme.resolveAuroraElevation
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics

internal class GuidesStep(
    private val onRestoreBackup: () -> Unit,
) : OnboardingStep {

    override val isComplete: Boolean = true

    @Composable
    override fun Content() {
        val handler = LocalUriHandler.current
        val colors = AuroraTheme.colors
        val appHaptics = LocalAppHaptics.current

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val heroBg = if (!colors.isDark && !colors.isEInk) {
                Color.White
            } else {
                resolveAuroraMoreCardContainerColor(colors)
            }
            val heroBorder = if (colors.isEInk) {
                BorderStroke(1.dp, resolveAuroraMoreCardBorderColor(colors))
            } else {
                null
            }
            val heroElevation = if (!colors.isDark && !colors.isEInk) {
                resolveAuroraElevation(colors, AuroraSurfaceLevel.Strong)
            } else {
                0.dp
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = heroBg),
                border = heroBorder,
                elevation = CardDefaults.cardElevation(defaultElevation = heroElevation),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(colors.success.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Celebration,
                            contentDescription = null,
                            tint = colors.success,
                            modifier = Modifier.size(36.dp),
                        )
                    }

                    Text(
                        text = stringResource(MR.strings.onboarding_guides_all_set),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textPrimary,
                    )

                    Text(
                        text = stringResource(MR.strings.onboarding_guides_welcome_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
            }

            // ── Section label ─────────────────────────────────────────────────
            Text(
                text = stringResource(MR.strings.onboarding_guides_next_steps),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
            )

            // ── Action cards ──────────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // New user — Getting Started
                ActionCard(
                    icon = Icons.AutoMirrored.Filled.HelpOutline,
                    text = stringResource(
                        MR.strings.onboarding_guides_new_user,
                        stringResource(MR.strings.app_name),
                    ),
                    buttonLabel = stringResource(MR.strings.getting_started_guide),
                    // Primary filled style — same accent, same elevation pattern
                    buttonFilled = true,
                    onClick = {
                        appHaptics.tap()
                        handler.openUri(GETTING_STARTED_URL)
                    },
                )

                // Returning user — Restore backup
                ActionCard(
                    icon = Icons.Default.Restore,
                    text = stringResource(
                        MR.strings.onboarding_guides_returning_user,
                        stringResource(MR.strings.app_name),
                    ),
                    buttonLabel = stringResource(MR.strings.pref_restore_backup),
                    // Tinted secondary style — consistent with accent tinting used elsewhere
                    buttonFilled = true,
                    onClick = {
                        appHaptics.tap()
                        onRestoreBackup()
                    },
                )
            }
        }
    }

    @Composable
    private fun ActionCard(
        icon: ImageVector,
        text: String,
        buttonLabel: String,
        buttonFilled: Boolean,
        onClick: () -> Unit,
    ) {
        val colors = AuroraTheme.colors

        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.97f else 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
            label = "guide_card_scale",
        )

        val cardBg = if (!colors.isDark && !colors.isEInk) {
            Color.White
        } else {
            resolveAuroraMoreCardContainerColor(colors)
        }
        val cardBorder = if (colors.isEInk) {
            BorderStroke(1.dp, resolveAuroraMoreCardBorderColor(colors))
        } else {
            null
        }
        val cardElevation = if (!colors.isDark && !colors.isEInk) {
            resolveAuroraElevation(colors, AuroraSurfaceLevel.Glass)
        } else {
            0.dp
        }

        Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = cardBorder,
            elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
            interactionSource = interactionSource,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.accent.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                }

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onClick,
                    colors = if (buttonFilled) {
                        ButtonDefaults.buttonColors(
                            containerColor = colors.accent,
                            contentColor = colors.textOnAccent,
                        )
                    } else {
                        ButtonDefaults.buttonColors(
                            containerColor = colors.accent.copy(alpha = 0.12f),
                            contentColor = colors.accent,
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                ) {
                    if (!buttonFilled) {
                        Icon(
                            imageVector = Icons.Default.Restore,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(buttonLabel)
                }
            }
        }
    }
}

const val GETTING_STARTED_URL = "https://aniyomi.org/docs/guides/getting-started"

@PreviewLightDark
@Composable
private fun GuidesStepPreview() {
    TachiyomiPreviewTheme {
        GuidesStep(
            onRestoreBackup = {},
        ).Content()
    }
}
