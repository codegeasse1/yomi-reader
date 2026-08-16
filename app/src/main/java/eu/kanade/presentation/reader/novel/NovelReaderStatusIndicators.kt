package eu.kanade.presentation.reader.novel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat

@Composable
internal fun rememberBatteryLevel(context: Context): State<Int> {
    val batteryLevelState = remember(context) { mutableIntStateOf(readBatteryLevel(context)) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                batteryLevelState.intValue = readBatteryLevel(context, intent)
            }
        }
        val stickyIntent = ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        batteryLevelState.intValue = readBatteryLevel(context, stickyIntent)
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    return batteryLevelState
}

@Composable
internal fun rememberCurrentTimeText(context: Context): State<String> {
    val timeState = remember(context) { mutableStateOf(currentTimeString(context)) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                timeState.value = currentTimeString(context)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        timeState.value = currentTimeString(context)
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    return timeState
}
