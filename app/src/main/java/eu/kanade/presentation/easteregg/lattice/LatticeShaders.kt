package eu.kanade.presentation.easteregg.lattice

import android.os.Build

/**
 * AGSL shaders (API 33+). Temporal frequencies stay below ~1 Hz for ambient layers;
 * light-cycle head motion is slightly faster but trail-only (no full-screen strobe).
 */
object LatticeShaders {

    val available: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /**
     * Tron-style perspective grid: dark void sky, glowing horizon, floor racing toward camera.
     */
    const val GRID_FLOOR = """
uniform float2 uResolution;
uniform float uTime;
layout(color) uniform half4 uTint;

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / uResolution;
    float3 cyan = uTint.rgb;
    float3 amber = float3(1.0, 0.72, 0.30);
    float3 voidCol = float3(0.02, 0.025, 0.04);

    // Upper void with faint scan haze
    float horizon = 0.48;
    if (uv.y < horizon) {
        float sky = smoothstep(horizon, 0.0, uv.y);
        float haze = 0.04 * (1.0 - sky) * (0.6 + 0.4 * sin(uTime * 0.7 + uv.x * 4.0));
        float3 col = voidCol + cyan * haze * 0.35;
        // horizon glow strip
        float hg = exp(-abs(uv.y - horizon) * 55.0) * 0.55;
        col += cyan * hg + amber * hg * 0.15;
        return half4(col, 1.0);
    }

    // Perspective floor
    float depth = (uv.y - horizon) / (1.0 - horizon);
    float persp = 1.0 / max(depth, 0.03);
    float x = (uv.x - 0.5) * persp * 7.5;
    float z = persp * 5.0 - uTime * 0.55;
    float lineX = smoothstep(0.07, 0.0, abs(fract(x) - 0.5) - 0.44);
    float lineZ = smoothstep(0.07, 0.0, abs(fract(z) - 0.5) - 0.44);
    float g = clamp(lineX + lineZ, 0.0, 1.0);
    g *= smoothstep(0.0, 0.18, depth);
    // center runway brighter
    float runway = exp(-abs(uv.x - 0.5) * 3.2) * 0.35 * depth;
    float fog = depth * 0.22;
    float3 col = voidCol + cyan * (g * 0.55 + fog + runway);
    col += amber * g * 0.08 * (1.0 - abs(uv.x - 0.5) * 2.0);
    // horizon bleed
    col += cyan * exp(-(uv.y - horizon) * 12.0) * 0.25;
    return half4(col, 1.0);
}
"""

    /** De-rez dissolve into pixel blocks (uProgress 0..1). */
    const val DEREZ = """
uniform float2 uResolution;
uniform float uProgress;
layout(color) uniform half4 uTint;

float hash(float2 p) {
    return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
}

half4 main(float2 fragCoord) {
    float2 cell = floor(fragCoord / 14.0);
    float h = hash(cell);
    float visible = step(uProgress, h);
    float edge = smoothstep(0.0, 0.15, h - uProgress) * (1.0 - visible);
    float v = visible * 0.12 + edge * 0.8;
    return half4(uTint.rgb * v, v);
}
"""

    /** Light ribbon trace running horizontally. */
    const val RIBBON = """
uniform float2 uResolution;
uniform float uTime;
layout(color) uniform half4 uTint;

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / uResolution;
    float head = fract(uTime * 0.19);
    float d = uv.x - head;
    float trail = exp(-max(-d, 0.0) * 9.0) * step(d, 0.0);
    float band = smoothstep(0.5, 0.0, abs(uv.y - 0.5) * 2.0);
    float v = trail * band;
    return half4(uTint.rgb * v, v);
}
"""

    /**
     * Light-cycles racing toward the horizon (into the Grid), not side-to-side.
     * Perspective: near camera at bottom, vanish at horizon ~0.48.
     * Trails stay fully on the floor (no edge clipping).
     */
    const val LIGHT_CYCLES = """
uniform float2 uResolution;
uniform float uTime;
layout(color) uniform half4 uTint;

// headT: 0 = near camera (bottom), 1 = at horizon
// lane: -1..1 left/right of center runway, converges with depth
float cycle(float2 uv, float headT, float lane, float widthNear) {
    float horizon = 0.48;
    float floorTop = horizon + 0.02;
    float floorBot = 0.985;
    if (uv.y < floorTop || uv.y > floorBot) return 0.0;

    // depth 1 near camera, 0 at horizon
    float depth = (uv.y - horizon) / (1.0 - horizon);
    depth = clamp(depth, 0.02, 1.0);

    // head depth travels from near (1) toward horizon (0.08)
    float headDepth = mix(1.0, 0.08, clamp(headT, 0.0, 1.0));
    // perspective x of lane at this depth
    float laneX = 0.5 + lane * 0.42 * depth;
    float headX = 0.5 + lane * 0.42 * headDepth;
    float headY = horizon + headDepth * (1.0 - horizon);

    // only draw behind the head (toward camera = higher depth / lower screen)
    float behind = step(headDepth, depth + 0.002);
    float along = exp(-abs(depth - headDepth) * 14.0);
    float trail = exp(-max(depth - headDepth, 0.0) * 9.0) * behind;
    float lateral = exp(-abs(uv.x - mix(headX, laneX, 0.35)) * (18.0 / max(depth, 0.08)));
    float w = widthNear * mix(0.35, 1.0, depth);
    float band = smoothstep(w, 0.0, abs(uv.x - mix(laneX, headX, smoothstep(headDepth, headDepth + 0.2, depth))));
    float tip = exp(-length(float2((uv.x - headX) * 1.4, (uv.y - headY) * 2.2)) * 55.0);
    return (trail * band * lateral * 1.1 + tip * 1.6) * along;
}

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / uResolution;
    float3 cyan = uTint.rgb;
    float3 amber = float3(1.0, 0.72, 0.30);

    // three cycles on different lanes, phased
    float t1 = fract(uTime * 0.18 + 0.00);
    float t2 = fract(uTime * 0.14 + 0.33);
    float t3 = fract(uTime * 0.21 + 0.67);

    float c1 = cycle(uv, t1, -0.55, 0.045);
    float c2 = cycle(uv, t2,  0.15, 0.035);
    float a1 = cycle(uv, t3,  0.62, 0.032);

    float3 col = cyan * (c1 * 0.95 + c2 * 0.7) + amber * a1 * 0.9;
    float a = clamp(max(max(c1, c2), a1), 0.0, 1.0) * 0.9;
    return half4(col, a);
}
"""

    /** Radial core glow with a slow breathing pulse (~0.27 Hz). */
    const val CORE_GLOW = """
uniform float2 uResolution;
uniform float uTime;
layout(color) uniform half4 uTint;

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / uResolution;
    float2 d = uv - 0.5;
    d.x *= uResolution.x / uResolution.y;
    float r = length(d);
    float pulse = 0.86 + 0.14 * sin(uTime * 1.7);
    float core = smoothstep(0.16 * pulse, 0.0, r);
    float halo = smoothstep(0.55, 0.0, r) * 0.25;
    float v = core + halo;
    return half4(uTint.rgb * v, v);
}
"""

    /**
     * Core ignition cinematic (uProgress 0..1): expanding shock rings, radial beams,
     * collapsing hex sparks, cyan→amber heat. Full-screen payoff after topology lock.
     */
    const val CORE_IGNITION = """
uniform float2 uResolution;
uniform float uTime;
uniform float uProgress;
layout(color) uniform half4 uTint;

float hash(float2 p) {
    return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
}

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / uResolution;
    float2 p = uv - 0.5;
    p.x *= uResolution.x / max(uResolution.y, 1.0);
    float r = length(p);
    float ang = atan(p.y, p.x);
    float pr = clamp(uProgress, 0.0, 1.0);
    float3 cyan = uTint.rgb;
    float3 amber = float3(1.0, 0.72, 0.30);
    float3 col = float3(0.0);
    float alpha = 0.0;

    // Charge phase 0..0.35: core builds
    float charge = smoothstep(0.0, 0.35, pr);
    float coreR = 0.04 + 0.10 * charge + 0.03 * sin(uTime * 8.0) * charge;
    float core = smoothstep(coreR, 0.0, r);
    col += mix(cyan, amber, charge * 0.55) * core * (0.6 + 0.4 * charge);
    alpha = max(alpha, core * 0.9);

    // Shock rings expand 0.25..0.85
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float ringT = clamp((pr - 0.22 - fi * 0.08) / 0.55, 0.0, 1.0);
        float ringR = 0.06 + ringT * 0.85;
        float ring = exp(-pow((r - ringR) * 28.0, 2.0)) * (1.0 - ringT);
        float3 rc = mix(cyan, amber, fi * 0.35);
        col += rc * ring * 0.85;
        alpha = max(alpha, ring * 0.75);
    }

    // Radial spokes / energy rays
    float spokes = 0.0;
    for (int k = 0; k < 8; k++) {
        float ka = float(k) * 0.785398163 + uTime * 0.35 + pr * 1.2;
        float dAng = abs(atan(sin(ang - ka), cos(ang - ka)));
        spokes += exp(-dAng * 18.0) * exp(-r * 1.8);
    }
    float spokeA = spokes * smoothstep(0.15, 0.5, pr) * (1.0 - smoothstep(0.75, 1.0, pr));
    col += cyan * spokeA * 0.55 + amber * spokeA * 0.25;
    alpha = max(alpha, spokeA * 0.5);

    // Hex spark field
    float2 cell = floor((uv + float2(uTime * 0.02, -uTime * 0.01)) * 28.0);
    float h = hash(cell);
    float sparkLife = smoothstep(0.2, 0.55, pr) * (1.0 - smoothstep(0.7, 0.98, pr));
    float spark = step(0.88, h) * sparkLife * (0.5 + 0.5 * sin(uTime * 12.0 + h * 40.0));
    col += mix(cyan, amber, h) * spark * 0.7;
    alpha = max(alpha, spark * 0.4);

    // Final white flash near end
    float flash = smoothstep(0.82, 0.92, pr) * (1.0 - smoothstep(0.92, 1.0, pr));
    col += float3(0.9, 0.98, 1.0) * flash;
    alpha = max(alpha, flash * 0.85);

    // Soft vignette hold
    float vig = smoothstep(1.1, 0.35, r);
    col *= 0.35 + 0.65 * vig;
    alpha = clamp(alpha * (0.4 + 0.6 * pr), 0.0, 1.0);
    return half4(col, alpha);
}
"""
}
