package eu.kanade.presentation.components

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas

/**
 *  Шаг 2 «Разлом» — кинематографичный фон DATAMOSH (AGSL).
 *
 *  Порт варианта DATAMOSH из HTML-прототипа с запечёнными выбранными
 *  значениями. Рантайм-uniformы — только u_resolution / u_time / u_open.
 *  Остальное запечено в const (см. ниже).
 *
 *  RuntimeShader есть только на API 33+. На старых устройствах — статичный
 *  fallback. Ссылки на RuntimeShader изолированы в RiftDatamoshShader, чтобы ART
 *  не грузил класс на Android < 13 (иначе NoClassDefFoundError).
 */
@Composable
fun RiftDatamoshBackground(
    time: Float,
    modifier: Modifier = Modifier,
    open: Float = 0.05f,
) {
    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                RiftDatamoshShader()
            } catch (e: Throwable) {
                android.util.Log.e("RiftDatamosh", "Failed to instantiate RiftDatamoshShader", e)
                null
            }
        } else {
            null
        }
    }
    Canvas(modifier = modifier) {
        var drawn = false
        if (shader != null) {
            drawn = with(shader) { draw(open = open, time = time) }
        }
        if (!drawn) {
            drawRiftFallback()
        }
    }
}

/** Fallback для API < 33 или при ошибках компиляции: тёмный Void + центральный hazard-шов. */
private fun DrawScope.drawRiftFallback() {
    drawRect(GlitchPalette.Void)
    val cx = size.width / 2f
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color.Transparent,
                GlitchPalette.HazardRed.copy(alpha = 0.5f),
                GlitchPalette.Phosphor.copy(alpha = 0.35f),
                GlitchPalette.HazardRed.copy(alpha = 0.5f),
                Color.Transparent,
            ),
            startX = cx - size.width * 0.14f,
            endX = cx + size.width * 0.14f,
        ),
    )
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class RiftDatamoshShader {
    private val shader = try {
        android.util.Log.d("RiftDatamosh", "Attempting to compile RIFT_DATAMOSH_AGSL...")
        android.graphics.RuntimeShader(RIFT_DATAMOSH_AGSL)
    } catch (e: Throwable) {
        android.util.Log.e("RiftDatamosh", "Failed to compile RIFT_DATAMOSH_AGSL shader", e)
        null
    }
    private val paint = android.graphics.Paint()

    fun DrawScope.draw(open: Float, time: Float): Boolean {
        val s = shader ?: return false
        return try {
            s.setFloatUniform("u_resolution", size.width, size.height)
            s.setFloatUniform("u_time", time)
            s.setFloatUniform("u_open", open)
            paint.shader = s
            drawContext.canvas.nativeCanvas.drawRect(
                0f,
                0f,
                size.width,
                size.height,
                paint,
            )
            true
        } catch (e: Throwable) {
            android.util.Log.e("RiftDatamosh", "Error drawing RiftDatamoshShader", e)
            false
        }
    }
}

// Запечённые значения (выбор пользователя, variant=DATAMOSH):
// jag=0.14 edge=0.80 core=0.74 push=0.28 shake=0.60 ab=0.65
// scan=0.25 grain=0.115 bloom=0.88 speed=0.32 ; open=0.05 (runtime, можно анимировать)
private const val RIFT_DATAMOSH_AGSL = """
uniform float2 u_resolution;
uniform float  u_time;
uniform float  u_open;

const float3 HAZARD = float3(1.0, 0.0, 0.235);
const float3 SIGNAL = float3(1.0, 0.165, 0.165);
const float3 BLOOD  = float3(0.227, 0.0, 0.039);
const float3 PHOS   = float3(1.0, 0.30, 0.427);
const float3 VOIDC  = float3(0.027, 0.0, 0.004);

const float JAG   = 0.14;
const float EDGE  = 0.80;
const float CORE  = 0.74;
const float PUSH  = 0.28;
const float SHAKE = 0.60;
const float AB    = 0.65;
const float SCAN  = 0.25;
const float GRAIN = 0.115;
const float BLOOM = 0.88;
const float SPEED = 0.32;

float hash(float2 p){
    p = fract(p * float2(127.1, 311.7));
    p += dot(p, p + 43.12);
    return fract(p.x * p.y);
}

float noise(float2 p){
    float2 i = floor(p);
    float2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + float2(1.0, 0.0));
    float c = hash(i + float2(0.0, 1.0));
    float d = hash(i + float2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(float2 p){
    float s = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        s += a * noise(p);
        p *= 2.02;
        a *= 0.5;
    }
    return s;
}

float3 surface(float2 uv, float t){
    float g = 0.02 + 0.03 * fbm(uv * 3.0 + t * 0.05);
    float3 base = mix(VOIDC, BLOOD, g * 8.0);
    float lines = step(0.5, fract(uv.y * 22.0)) * 0.015;
    base += lines;
    float grid = smoothstep(0.49, 0.5, fract(uv.x * 8.0)) * 0.01;
    base += HAZARD * grid * 0.4;
    return base;
}

float3 hotEdge(float d, float w, float edge, float core){
    float glow = exp(-max(d - w, 0.0) * (14.0 / (edge + 0.001)));
    float3 col = mix(HAZARD, PHOS, glow * 0.5) * glow * (1.2 * edge);
    col += float3(1.0, 0.85, 0.8) * smoothstep(w + 0.006, w, d) * 0.9 * core;
    return col;
}

half4 main(float2 fragCoord){
    float2 res = u_resolution;
    float2 uv = fragCoord / res;
    float t = u_time * SPEED;
    float pulse = 0.5 + 0.5 * sin(t * 3.0);

    // camera push-in + shake
    float2 c = uv - 0.5;
    float zoom = 1.0 / (1.0 + PUSH * (0.5 + 0.5 * u_open));
    float2 shake = float2(sin(t * 40.0), cos(t * 37.0)) * 0.0018 * SHAKE * (0.3 + u_open);
    c = c * zoom + shake;
    float aspect = res.x / res.y;
    float2 p = c; p.x *= aspect;
    uv = c + 0.5;

    float o = u_open;

    // DATAMOSH: коррупция от центрального шва
    float seamDist = abs(p.x) / aspect;
    float reach = o * 0.7;
    float corrupt = smoothstep(reach, 0.0, seamDist);
    float row = floor(uv.y * 40.0);
    float blk = floor(uv.x * 24.0);
    float jump = (hash(float2(row, floor(t * 6.0))) - 0.5);
    float2 du = uv;
    du.x += jump * 0.15 * corrupt * JAG;
    du.x += (hash(float2(blk, row)) - 0.5) * 0.05 * corrupt;
    float3 col = surface(du, t);
    float b = step(0.7, hash(float2(blk, row + floor(t * 3.0)))) * corrupt;
    col = mix(col, HAZARD * (0.5 + 0.5 * pulse), b * 0.5);
    // всегда-живой центральный шов (виден даже в покое), усиливается при раскрытии
    float ambient = exp(-seamDist * (9.0 - 5.0 * o));
    col += mix(HAZARD, PHOS, pulse) * ambient * (0.35 + 0.9 * o);
    col += hotEdge(seamDist, reach * 0.15, EDGE, CORE) * max(corrupt, ambient * 0.5);

    // bloom lift
    float lum = dot(col, float3(0.3, 0.3, 0.3));
    col += col * smoothstep(0.5, 1.2, lum) * 0.5 * BLOOM;
    // scanlines
    col *= 1.0 - SCAN * 0.5 * (0.5 + 0.5 * sin(fragCoord.y * 3.14159));
    // grain
    col += (hash(fragCoord + fract(u_time)) - 0.5) * GRAIN;
    // vignette
    float vg = smoothstep(1.15, 0.35, length(p));
    col *= mix(0.35, 1.0, vg);

    return half4(half3(max(col, float3(0.0))), 1.0);
}
"""
