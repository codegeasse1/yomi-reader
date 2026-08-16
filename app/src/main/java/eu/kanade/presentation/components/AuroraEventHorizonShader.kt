package eu.kanade.presentation.components

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb

/**
 * Isolates all [RuntimeShader] (AGSL) usage for the Aurora "event horizon"
 * background.
 *
 * WHY A SEPARATE CLASS: android.graphics.RuntimeShader only exists on API 33+
 * (Android 13). If RuntimeShader is referenced directly inside a composable, the
 * ART class verifier resolves the class when that method is first executed —
 * even when the actual `new RuntimeShader` call is behind a Build.VERSION.SDK_INT
 * guard. On older devices (e.g. Android 9) that resolution fails with
 * NoClassDefFoundError and crashes the app when the Aurora home screen renders.
 *
 * Keeping every RuntimeShader reference in this @RequiresApi(33) class means the
 * class is only loaded/verified on devices that actually have RuntimeShader, so
 * pre-33 devices never trigger the failed resolution.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class AuroraEventHorizonShader {

    private val shader = RuntimeShader(EVENT_HORIZON_AGSL)
    private val brush = ShaderBrush(shader)

    /** One full-screen GPU AGSL pass for the event-horizon background. */
    fun DrawScope.drawEventHorizon(accent: Color, time: Float) {
        shader.setFloatUniform("uResolution", size.width, size.height)
        shader.setFloatUniform("iTime", time)
        shader.setColorUniform("uAccent", accent.toArgb())
        shader.setFloatUniform("uHorizon", 0.125f)
        shader.setFloatUniform("uTilt", 0.26f)
        shader.setFloatUniform("uBright", 0.35f)
        shader.setFloatUniform("uTurb", 0.97f)
        shader.setFloatUniform("uSpeed", 0.1f)
        shader.setFloatUniform("uGlow", 0.45f)
        shader.setFloatUniform("uLens", 0.3f)
        shader.setFloatUniform("uTemp", -0.09f)
        drawRect(brush = brush)
    }
}

// AGSL source for the event-horizon background (Android 13+).
private const val EVENT_HORIZON_AGSL = """
uniform float2 uResolution;
uniform float  iTime;
layout(color) uniform half4 uAccent;
uniform float uHorizon;
uniform float uTilt;
uniform float uBright;
uniform float uTurb;
uniform float uSpeed;
uniform float uGlow;
uniform float uLens;
uniform float uTemp;

half3 plasma(float t) {
    t = clamp(t + uTemp, 0.0, 1.0);
    half3 white  = half3(1.0, 0.96, 0.88);
    half3 orange = half3(1.0, 0.52, 0.10);
    half3 ember  = half3(0.80, 0.16, 0.02);
    return t < 0.5 ? mix(white, orange, t * 2.0) : mix(orange, ember, t * 2.0 - 1.0);
}

half4 main(float2 fragCoord) {
    float m = min(uResolution.x, uResolution.y);
    float2 uv = (fragCoord - 0.5 * uResolution) / m;
    uv.y = -uv.y;
    float sr = length(uv);
    float horizon = uHorizon;
    half3 col = half3(0.0);

    float2 dp = float2(uv.x, uv.y / uTilt);
    float r = length(dp);
    float innerR = horizon * 1.18;
    float t = (r - innerR) / (0.62 - innerR);
    float db = 0.0;
    half3 dc = half3(0.0);
    if (t > 0.0 && t < 1.0) {
        float ang = atan(dp.y, dp.x);
        float rot = iTime * uSpeed;
        float pat = 0.55 + 0.45 * sin(ang * 6.0 + r * 16.0 - rot * 3.0) * sin(ang * 3.0 + rot * 1.7);
        float radial = smoothstep(0.0, 0.12, t) * smoothstep(1.0, 0.45, t);
        float dopp = 1.0 + 0.6 * (-uv.x / max(r, 0.06));
        db = max(radial * mix(1.0, pat, uTurb) * uBright * dopp, 0.0);
        dc = mix(plasma(t), uAccent.rgb, 0.12);
    }
    float nearMask = step(uv.y, 0.0);

    col += dc * db * (1.0 - nearMask);
    col *= 1.0 - smoothstep(horizon * 1.02, horizon * 0.96, sr);

    float ring = 1.0 - smoothstep(0.0, horizon * 0.06, abs(sr - horizon * 1.03));
    col += mix(half3(1.0, 0.78, 0.47), uAccent.rgb, 0.10) * ring * uGlow;

    col += dc * db * nearMask;

    float halo = 1.0 - smoothstep(0.0, horizon * 0.16, abs(sr - horizon * 1.22));
    float topW = 0.5 + 0.5 * (-uv.y / max(sr, 0.06));
    col += mix(plasma(0.18), uAccent.rgb, 0.15) * halo * (0.35 + 0.9 * topW) * uLens;

    col *= 1.0 - smoothstep(0.30, 0.95, sr) * 0.5;
    col = col / (col + half3(0.6));
    return half4(col, 1.0);
}
"""
