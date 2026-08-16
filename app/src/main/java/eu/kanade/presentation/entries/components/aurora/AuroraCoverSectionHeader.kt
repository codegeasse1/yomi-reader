package eu.kanade.presentation.entries.components.aurora

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.AuroraTheme

@Composable
fun AuroraCoverSectionHeader(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    count: String? = null,
    showChevron: Boolean = false,
    onChevronClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val colors = AuroraTheme.colors
    val isDark = colors.isDark

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        color = if (isDark) {
                            Color.White.copy(alpha = 0.08f)
                        } else {
                            colors.accent
                        },
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isDark) colors.accent else Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }

            val titleColor = if (!isDark) Color(0xFF241A16) else colors.textPrimary
            val titleStyle = if (!isDark) {
                MaterialTheme.typography.titleLarge.copy(
                    shadow = Shadow(
                        color = Color.White.copy(alpha = 0.58f),
                        offset = Offset(0f, 1f),
                        blurRadius = 10f,
                    ),
                )
            } else {
                MaterialTheme.typography.titleLarge
            }

            Text(
                text = title,
                color = titleColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                style = titleStyle,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            trailingContent?.invoke()

            if (count != null) {
                Box(
                    modifier = Modifier
                        .background(
                            color = if (isDark) {
                                Color.White.copy(alpha = 0.08f)
                            } else {
                                Color.White.copy(alpha = 0.62f)
                            },
                            shape = RoundedCornerShape(100.dp),
                        )
                        .then(
                            if (!isDark) {
                                Modifier.border(
                                    width = 1.dp,
                                    color = Color.White.copy(alpha = 0.46f),
                                    shape = RoundedCornerShape(100.dp),
                                )
                            } else {
                                Modifier
                            },
                        )
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = count,
                        color = colors.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            if (showChevron && onChevronClick != null) {
                Box(
                    modifier = Modifier
                        .auroraSpringClick(onClick = onChevronClick)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}
