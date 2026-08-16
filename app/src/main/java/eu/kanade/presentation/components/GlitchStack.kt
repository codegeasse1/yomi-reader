package eu.kanade.presentation.components

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/* =============================================================================
 *  GLITCH STACK — единый движок апокалиптических глитч/хоррор-эффектов.
 *
 *  Идея: один слой [GlitchStack] оборачивает любой контент (экран поиска,
 *  терминал, страницу читалки) и накладывает многослойный сбой, управляемый
 *  единственным параметром intensity 0f..1f. Все три этапа пасхалки
 *  "Third Impact" собираются из этого движка + общих оверлеев.
 *
 *  API 33+ : один AGSL RuntimeShader (RGB-split + шум + скан-линии + tear).
 *  API <33 : автоматический fallback на Canvas-оверлеи (тот же вид, дешевле).
 * ========================================================================== */

// -----------------------------------------------------------------------------
//  Палитра Красного Сектора (Void Red)
// -----------------------------------------------------------------------------
object GlitchPalette {
    val HazardRed = Color(0xFFFF003C) // основной алый
    val SignalRed = Color(0xFFFF2A2A) // hazard / сигнальный
    val BloodDark = Color(0xFF3A000A) // тёмная кровь (края, виньетка)
    val BloodPool = Color(0xFF5A0012) // лужа крови
    val Phosphor = Color(0xFFFF4D6D) // фосфорное свечение текста
    val Void = Color(0xFF070001) // почти чёрный фон терминала
}

// -----------------------------------------------------------------------------
//  Конфиг: включаем/выключаем слои под конкретный экран
// -----------------------------------------------------------------------------
data class GlitchConfig(
    val chromaticAberration: Boolean = true,
    val blockDisplacement: Boolean = true,
    val scanlines: Boolean = true,
    val staticNoise: Boolean = true,
    val flicker: Boolean = true,
    val bloodVignette: Boolean = true,
    val bloodDrips: Boolean = false, // тяжёлый эффект, включаем на пике
    val crackedGlass: Boolean = false, // трещины поверх — для 5-го свайпа
    val heartbeat: Boolean = true, // пульсация виньетки "сердцебиением"
    val redBleed: Float = 0.06f, // сколько алого подмешивать в кадр
)

// -----------------------------------------------------------------------------
//  Пресеты интенсивности под стадии пасхалки
// -----------------------------------------------------------------------------
object MeltdownPresets {
    // Шаг 1 — инициация: резкий джолт + средний фон
    val Initiation = GlitchConfig(bloodDrips = false, crackedGlass = false)

    // Шаг 2 — разлом: агрессивнее
    val Rift = GlitchConfig(bloodDrips = false, crackedGlass = false)

    // Шаг 3 — финальный meltdown в читалке
    val Void = GlitchConfig(bloodDrips = false, crackedGlass = false)

    /** Плавное соответствие "номер свайпа (0..5) -> интенсивность". */
    fun swipeIntensity(swipe: Int): Float = when (swipe) {
        0 -> 0f
        1 -> 0.12f
        2 -> 0.22f
        3 -> 0.42f
        4 -> 0.66f
        else -> 1f
    }
}

// =============================================================================
//  ГЛАВНЫЙ КОМПОНЕНТ
// =============================================================================

/**
 * Оборачивает [content] в многослойный глитч. [intensity] 0f..1f — мастер-ручка.
 *
 * Структура слоёв:
 *   1. content — прогоняется через AGSL-шейдер (или без него на старых API);
 *   2. поверх — "чистые" хоррор-оверлеи (виньетка, кровь, трещины, мерцание),
 *      которые НЕ искажаются шейдером, чтобы кровь читалась чётко.
 */
@Composable
fun GlitchStack(
    intensity: Float,
    modifier: Modifier = Modifier,
    config: GlitchConfig = GlitchConfig(),
    content: @Composable BoxScope.() -> Unit,
) {
    val amount = intensity.coerceIn(0f, 1f)
    val time by rememberGlitchTime()
    var size by remember { mutableStateOf(IntSize.Zero) }

    val shaderHolder = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            GlitchStackShader()
        } else {
            null
        }
    }
    val useShader = shaderHolder?.isAvailable == true

    val beat = if (config.heartbeat) heartbeat(time) else 1f

    Box(modifier = modifier.onSizeChanged { size = it }) {
        // --- Слой 1: контент + шейдер (искажение картинки) ---
        Box(
            modifier = if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                shaderHolder != null &&
                useShader &&
                amount > 0.001f &&
                size != IntSize.Zero
            ) {
                with(shaderHolder) { Modifier.attach(time, amount, config, size) }
            } else {
                Modifier
            },
            content = content,
        )

        // --- Слой 2: fallback-оверлеи, если шейдера нет ---
        if (!useShader && amount > 0.001f) {
            if (config.blockDisplacement || config.chromaticAberration) {
                DatamoshOverlay(amount, time, Modifier.matchParentSize())
            }
            if (config.staticNoise) StaticNoiseOverlay(amount, time, Modifier.matchParentSize())
            if (config.scanlines) ScanlineOverlay(amount, time, Modifier.matchParentSize())
        }

        // --- Слой 3: хоррор-оверлеи поверх всего (всегда чёткие) ---
        if (config.bloodVignette && amount > 0f) {
            BloodVignetteOverlay(amount * beat, Modifier.matchParentSize())
        }
        if (config.bloodDrips && amount > 0.3f) {
            BloodDripsOverlay(amount, time, Modifier.matchParentSize())
        }
        if (config.crackedGlass && amount > 0.5f) {
            CrackedGlassOverlay(amount, Modifier.matchParentSize())
        }
        if (config.flicker && amount > 0.2f) {
            FlickerOverlay(amount, time, Modifier.matchParentSize())
        }
    }
}

// =============================================================================
//  ОБЩИЕ ДРАЙВЕРЫ ВРЕМЕНИ И РИТМА
// =============================================================================

/** Секунды с момента появления слоя (кадровый таймер, единый для всех эффектов). */
@Composable
fun rememberGlitchTime(): State<Float> {
    val t = remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> t.value = (now - start) / 1_000_000_000f }
        }
    }
    return t
}

/** Кривая сердцебиения (~66 уд/мин): двойной толчок 1.0 -> ~1.35 -> 1.0. */
fun heartbeat(time: Float): Float {
    val phase = (time * 1.1f) % 1f
    val thump1 = exp(-((phase - 0.00f) / 0.055f).pow(2))
    val thump2 = 0.6f * exp(-((phase - 0.20f) / 0.055f).pow(2))
    return 1f + 0.35f * (thump1 + thump2)
}

// =============================================================================
//  CANVAS-ОВЕРЛЕИ (fallback + хоррор-слои)
// =============================================================================

/** Тёмно-кровавая виньетка по краям + пульс "сердцебиением". */
@Composable
fun BloodVignetteOverlay(intensity: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val maxR = kotlin.math.hypot(size.width, size.height) / 2f
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                colors = listOf(
                    Color.Transparent,
                    GlitchPalette.BloodDark.copy(alpha = 0.20f * intensity),
                    GlitchPalette.BloodPool.copy(alpha = 0.55f * intensity),
                    Color.Black.copy(alpha = 0.75f * intensity),
                ),
                center = Offset(size.width / 2f, size.height / 2f),
                radius = maxR * (1.15f - 0.25f * intensity),
            ),
        )
    }
}

/** Скан-линии CRT + бегущая яркая полоса развёртки. */
@Composable
fun ScanlineOverlay(intensity: Float, time: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val step = 3f
        var y = 0f
        while (y < size.height) {
            drawLine(
                color = Color.Black.copy(alpha = 0.18f * intensity),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f,
            )
            y += step
        }
        // бегущая полоса развёртки
        val bandY = (time * 220f % (size.height + 120f)) - 60f
        drawRect(
            color = GlitchPalette.HazardRed.copy(alpha = 0.06f * intensity),
            topLeft = Offset(0f, bandY),
            size = Size(size.width, 90f),
        )
    }
}

/** Статик-шум из заранее сгенерированного тайла, сдвигаемого каждый кадр. */
@Composable
fun StaticNoiseOverlay(intensity: Float, time: Float, modifier: Modifier = Modifier) {
    val noise = remember { createNoiseBitmap(160, 160) }
    Canvas(modifier = modifier.fillMaxSize()) {
        val rnd = Random((time * 60f).toInt())
        val dx = rnd.nextFloat() * 40f
        val dy = rnd.nextFloat() * 40f
        var x = -dx
        while (x < size.width) {
            var y = -dy
            while (y < size.height) {
                drawImage(
                    image = noise,
                    topLeft = Offset(x, y),
                    alpha = 0.10f * intensity,
                    blendMode = BlendMode.Screen,
                )
                y += noise.height
            }
            x += noise.width
        }
    }
}

/** Fallback датамош: красные/белые смещённые полосы (богаче старого overlay). */
@Composable
fun DatamoshOverlay(intensity: Float, time: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val rnd = Random((time * 30f).toInt())
        val bars = (4 + intensity * 10).toInt()
        repeat(bars) {
            val barH = rnd.nextFloat() * 55f + 6f
            val barY = rnd.nextFloat() * size.height
            val a = (rnd.nextFloat() * 0.35f + 0.1f) * intensity
            val full = rnd.nextBoolean()
            val w = if (full) size.width else size.width * (rnd.nextFloat() * 0.6f + 0.2f)
            val x = if (full) 0f else rnd.nextFloat() * (size.width - w)
            drawRect(GlitchPalette.HazardRed.copy(alpha = a), Offset(x, barY), Size(w, barH))
        }
        // тонкие помехи
        repeat((10 + intensity * 12).toInt()) {
            val ly = rnd.nextFloat() * size.height
            val white = rnd.nextFloat() > 0.8f
            val c = if (white) Color.White else GlitchPalette.HazardRed
            drawLine(
                color = c.copy(alpha = (rnd.nextFloat() * 0.15f + 0.05f) * intensity),
                start = Offset(0f, ly),
                end = Offset(size.width, ly),
                strokeWidth = rnd.nextFloat() * 2f + 1f,
            )
        }
    }
}

/** Кровавые потёки, стекающие сверху; длина растёт с интенсивностью. */
@Composable
fun BloodDripsOverlay(intensity: Float, time: Float, modifier: Modifier = Modifier) {
    val seeds = remember { List(9) { Random(it * 977L) } }
    Canvas(modifier = modifier.fillMaxSize()) {
        seeds.forEachIndexed { i, r0 ->
            val r = Random(i * 977L)
            val x = r.nextFloat() * size.width
            val maxLen = size.height * (0.15f + 0.5f * intensity)
            // потёк постепенно "растёт" во времени
            val grow = ((time * (0.15f + r.nextFloat() * 0.2f)) % 1.4f).coerceIn(0f, 1f)
            val len = maxLen * grow
            val w = 3f + r.nextFloat() * 6f
            drawRect(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        GlitchPalette.HazardRed.copy(alpha = 0.85f * intensity),
                        GlitchPalette.BloodPool.copy(
                            alpha =
                            0.35f * intensity,
                        ),
                    ),
                ),
                topLeft = Offset(x, 0f),
                size = Size(w, len),
            )
            // капля на конце
            drawCircle(
                color = GlitchPalette.HazardRed.copy(alpha = 0.8f * intensity),
                radius = w * 0.9f,
                center = Offset(x + w / 2f, len),
            )
        }
    }
}

/** Трещины "разбитого стекла", расходящиеся из случайного центра. */
@Composable
fun CrackedGlassOverlay(intensity: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val cx = size.width * 0.5f
        val cy = size.height * 0.42f
        val rnd = Random(42)
        val rays = 11
        repeat(rays) { i ->
            val ang = (i.toFloat() / rays) * 2f * PI.toFloat() + rnd.nextFloat() * 0.3f
            var px = cx
            var py = cy
            val segs = 5
            repeat(segs) { s ->
                val segLen = (size.height * 0.16f) * (1f - s * 0.12f) * (0.6f + 0.4f * intensity)
                val jitter = (rnd.nextFloat() - 0.5f) * 0.5f
                val nx = px + kotlin.math.cos(ang + jitter) * segLen
                val ny = py + kotlin.math.sin(ang + jitter) * segLen
                drawLine(
                    color = GlitchPalette.HazardRed.copy(alpha = (0.5f - s * 0.07f) * intensity),
                    start = Offset(px, py),
                    end = Offset(nx, ny),
                    strokeWidth = (3.5f - s * 0.5f).coerceAtLeast(1f),
                )
                px = nx
                py = ny
            }
        }
    }
}

/** Резкое мерцание яркости — как сбой питания. */
@Composable
fun FlickerOverlay(intensity: Float, time: Float, modifier: Modifier = Modifier) {
    val rnd = Random((time * 24f).toInt())
    val flick = if (rnd.nextFloat() > 0.85f) rnd.nextFloat() * 0.25f * intensity else 0f
    if (flick > 0f) {
        Canvas(modifier = modifier.fillMaxSize()) {
            drawRect(color = Color.Black.copy(alpha = flick))
        }
    }
}

// =============================================================================
//  ТЕКСТ: скремблированный typewriter (для терминалов)
// =============================================================================

private const val SCRAMBLE_GLYPHS = "\u2588\u2593\u2592\u2591#@%&/\\<>*+=-01"

/**
 * Печатает [text] с эффектом "сборки" каждого символа из битых глифов.
 * Возвращает текущую строку для отрисовки.
 */
@Composable
fun rememberScrambleReveal(
    text: String,
    charDelayMs: Long = 26,
    scramblePerChar: Int = 2,
): String {
    var out by remember(text) { mutableStateOf("") }
    LaunchedEffect(text) {
        val sb = StringBuilder()
        for (i in text.indices) {
            val target = text[i]
            if (!target.isWhitespace()) {
                repeat(scramblePerChar) {
                    sb.append(SCRAMBLE_GLYPHS.random())
                    out = sb.toString()
                    kotlinx.coroutines.delay(charDelayMs / (scramblePerChar + 1))
                    sb.deleteCharAt(sb.length - 1)
                }
            }
            sb.append(target)
            out = sb.toString()
            kotlinx.coroutines.delay(charDelayMs)
        }
    }
    return out
}

// =============================================================================
//  CRT POWER-OFF: схлопывание слоя в красную точку (для "ПРИНЯТЬ ПУСТОТУ")
// =============================================================================

/**
 * Когда [active] становится true — слой схлопывается: сначала по вертикали в
 * тонкую линию, затем по горизонтали в точку. [onFinished] вызывается в конце.
 */
@Composable
fun Modifier.crtCollapse(
    active: Boolean,
    durationMs: Int = 620,
    onFinished: () -> Unit = {},
): Modifier {
    val progress by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(durationMs, easing = FastOutSlowInEasing),
        finishedListener = { if (active && it >= 1f) onFinished() },
        label = "crt_collapse",
    )
    return this.graphicsLayer {
        if (progress <= 0f) return@graphicsLayer
        val vy = 1f - (progress / 0.65f).coerceIn(0f, 1f) // фаза 1: вертикаль
        val hx = 1f - ((progress - 0.65f) / 0.35f).coerceIn(0f, 1f) // фаза 2: горизонталь
        scaleY = 0.008f + 0.992f * vy
        scaleX = if (progress < 0.65f) 1f else (0.008f + 0.992f * hx)
        alpha = 1f - ((progress - 0.9f) / 0.1f).coerceIn(0f, 1f)
    }
}

/**
 * Обратный эффект: слой "включается" из линии (для CRT power-on, напр. при
 * появлении терминала или загрузке Treasury). [visible] запускает разворот.
 */
@Composable
fun Modifier.crtPowerOn(
    visible: Boolean,
    durationMs: Int = 800,
): Modifier {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMs, easing = EaseInOutSine),
        label = "crt_power_on",
    )
    return this.graphicsLayer {
        val hx = (progress / 0.4f).coerceIn(0f, 1f) // сначала линия по X
        val vy = ((progress - 0.4f) / 0.6f).coerceIn(0f, 1f) // потом раскрытие по Y
        scaleX = 0.02f + 0.98f * hx
        scaleY = 0.02f + 0.98f * vy
        alpha = progress
    }
}

// =============================================================================
//  ВСПОМОГАТЕЛЬНОЕ
// =============================================================================

/** Генерация тайла шума ARGB для StaticNoiseOverlay. */
internal fun createNoiseBitmap(w: Int, h: Int): androidx.compose.ui.graphics.ImageBitmap {
    val px = IntArray(w * h)
    val r = java.util.Random(1337)
    for (i in px.indices) {
        val v = r.nextInt(256)
        val a = r.nextInt(200)
        px[i] = (a shl 24) or (v shl 16) or (v shl 8) or v
    }
    return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** Быстрое дрожание оффсета для тряски "землетрясения" (шаги 1 и 2). */
fun quakeOffset(time: Float, intensity: Float): Offset {
    val f = 34f
    val dx = sin(time * f) * 6f * intensity + sin(time * f * 2.3f) * 3f * intensity
    val dy = sin(time * f * 1.7f) * 5f * intensity + sin(time * f * 3.1f) * 2f * intensity
    return Offset(dx, dy)
}
