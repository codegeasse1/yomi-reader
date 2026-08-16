package eu.kanade.presentation.download

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle as preferenceCollectAsState

/**
 * Lightweight OEM/battery policy advisor for long-running downloads.
 *
 * The reminder is shown only while the device is likely to throttle background
 * work (battery optimizations enabled, or a Xiaomi/MIUI/HyperOS device where
 * auto-start cannot be verified). It refreshes when the user returns from
 * system settings and can be dismissed permanently.
 */
@Composable
fun DownloadPowerPolicyBanner(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val downloadPreferences = remember { Injekt.get<DownloadPreferences>() }
    val hideBannerPref = remember { downloadPreferences.hideDownloadPowerPolicyBanner() }
    val dismissed by hideBannerPref.preferenceCollectAsState()

    val advisor = remember(context) { DownloadPowerPolicyAdvisor(context) }
    var state by remember { mutableStateOf(advisor.currentState()) }

    // Re-check restrictions every time the screen resumes (e.g. after the user
    // returns from the battery/auto-start system settings).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, advisor) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                state = advisor.currentState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!shouldShowDownloadPowerPolicyBanner(
            isXiaomiFamily = state.isXiaomiFamily,
            ignoringBatteryOptimizations = state.ignoringBatteryOptimizations,
            dismissed = dismissed,
        )
    ) {
        return
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Outlined.BatteryAlert,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(AYMR.strings.download_power_policy_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (state.isXiaomiFamily) {
                        stringResource(AYMR.strings.download_power_policy_xiaomi_summary)
                    } else {
                        stringResource(AYMR.strings.download_power_policy_android_summary)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.82f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (!state.ignoringBatteryOptimizations) {
                        TextButton(onClick = advisor::openBatteryOptimizationSettings) {
                            Text(stringResource(AYMR.strings.download_power_policy_battery_action))
                        }
                    }
                    if (state.isXiaomiFamily) {
                        TextButton(onClick = advisor::openXiaomiAutostartSettings) {
                            Text(stringResource(AYMR.strings.download_power_policy_autostart_action))
                        }
                    }
                    TextButton(onClick = { hideBannerPref.set(true) }) {
                        Text(stringResource(AYMR.strings.download_power_policy_dismiss))
                    }
                }
            }
        }
    }
}

/**
 * Pure visibility rule so the behavior stays unit-testable:
 * dismissed wins; otherwise show while battery optimizations are still on,
 * or on the Xiaomi family where auto-start state cannot be read.
 */
internal fun shouldShowDownloadPowerPolicyBanner(
    isXiaomiFamily: Boolean,
    ignoringBatteryOptimizations: Boolean,
    dismissed: Boolean,
): Boolean {
    if (dismissed) return false
    return isXiaomiFamily || !ignoringBatteryOptimizations
}

private class DownloadPowerPolicyAdvisor(
    private val context: Context,
) {
    fun currentState(): State {
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()
        val brand = Build.BRAND.orEmpty().lowercase()
        val isXiaomiFamily = listOf("xiaomi", "redmi", "poco").any { token ->
            manufacturer.contains(token) || brand.contains(token)
        }
        val ignoringBatteryOptimizations = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            runCatching {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                powerManager.isIgnoringBatteryOptimizations(context.packageName)
            }.getOrDefault(false)

        return State(
            isXiaomiFamily = isXiaomiFamily,
            ignoringBatteryOptimizations = ignoringBatteryOptimizations,
        )
    }

    fun openBatteryOptimizationSettings() {
        val packageUri = Uri.parse("package:${context.packageName}")
        val intents = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            listOf(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri),
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri),
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            )
        } else {
            listOf(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri),
            )
        }
        openFirstAvailable(intents)
    }

    fun openXiaomiAutostartSettings() {
        val intents = listOf(
            Intent().setClassName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity",
            ),
            Intent().setClassName(
                "com.miui.securitycenter",
                "com.miui.powercenter.PowerSettings",
            ),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
        )
        openFirstAvailable(intents)
    }

    private fun openFirstAvailable(intents: List<Intent>) {
        intents.forEach { intent ->
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
                // Try the next OEM/system fallback.
            } catch (_: SecurityException) {
                // Some ROMs block direct settings panels; try the next fallback.
            }
        }
    }

    data class State(
        val isXiaomiFamily: Boolean,
        val ignoringBatteryOptimizations: Boolean,
    )
}
