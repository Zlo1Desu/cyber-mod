#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform float Time;
uniform float Intensity;
uniform float Shockwave;

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

const float TWO_PI = 6.28318530718;

void main() {
    float intensity = clamp(Intensity, 0.0, 1.0);
    float shockwave = clamp(Shockwave, 0.0, 1.0);

    vec3 baseOrig = texture(DiffuseSampler, texCoord).rgb;
    if (intensity <= 0.001 && (shockwave <= 0.0 || shockwave >= 1.0)) {
        fragColor = vec4(baseOrig, 1.0);
        return;
    }

    vec2 centered = texCoord - vec2(0.5);
    float distFromCenter = length(centered) * 1.41421356;
    vec2 radialDir = (distFromCenter > 0.0001) ? normalize(centered) : vec2(0.0);

    // Aspect-corrected circular distance (0 at crosshair, 1 at screen corners)
    float aspectX = max(InSize.x, 1.0) / max(InSize.y, 1.0);
    vec2 aspectVec = vec2(aspectX, 1.0);
    float circularDist = length(centered * aspectVec) / (0.5 * length(aspectVec));

    // 1. Refraction Shockwave Ring on Sandevistan activation
    vec2 uv = texCoord;
    float waveChroma = 0.0;
    float ringRim = 0.0;
    if (shockwave > 0.0 && shockwave < 1.0) {
        float ringRadius = shockwave * 0.95;
        float ringWidth = 0.075 + 0.045 * shockwave;
        float ringDelta = circularDist - ringRadius;
        float ringBell = exp(-(ringDelta * ringDelta) / (ringWidth * ringWidth));
        float waveEnv = (1.0 - shockwave) * smoothstep(0.0, 0.06, shockwave);

        // Radial lens distortion ripples the 3D world outward
        float lensShift = -sin((ringDelta / ringWidth) * 3.14159265) * ringBell * waveEnv * 0.036;
        uv = clamp(texCoord + radialDir * lensShift, vec2(0.001), vec2(0.999));

        waveChroma = ringBell * waveEnv * 0.018;
        ringRim = ringBell * waveEnv * 0.24;
    }

    // 2. Radial Chromatic Aberration (razor-sharp crosshair center, iconic 2077 edge fringes)
    float neuralPulse = 1.0 + 0.06 * sin(Time * TWO_PI);
    float radialCurve = pow(clamp(distFromCenter, 0.0001, 1.25), 1.8);
    float chromaOffset = (radialCurve * 0.0145 * intensity * neuralPulse) + waveChroma;

    // 3. Subtle 5-tap radial edge motion blur (periphery only, crystal-clear center)
    float edgeBlurMask = smoothstep(0.42, 1.08, distFromCenter) * intensity;
    vec2 blurStep = -radialDir * (0.0135 * edgeBlurMask);

    vec3 accum = vec3(0.0);
    float totalWeight = 0.0;
    for (int i = 0; i < 5; i++) {
        float t = float(i) * 0.25;
        float w = 1.0 - t * 0.45;
        vec2 tapUv = uv + blurStep * t;

        vec2 uvR = clamp(tapUv + radialDir * chromaOffset, vec2(0.001), vec2(0.999));
        vec2 uvG = clamp(tapUv, vec2(0.001), vec2(0.999));
        vec2 uvB = clamp(tapUv - radialDir * (chromaOffset * 1.12), vec2(0.001), vec2(0.999));

        vec3 sampleRgb = vec3(
            texture(DiffuseSampler, uvR).r,
            texture(DiffuseSampler, uvG).g,
            texture(DiffuseSampler, uvB).b
        );
        accum += sampleRgb * w;
        totalWeight += w;
    }
    vec3 color = accum / max(totalWeight, 0.0001);

    // 4. Authentic Cyberpunk 2077 Sandevistan Color Grading
    float luma = dot(color, vec3(0.299, 0.587, 0.114));
    vec3 desat = mix(color, vec3(luma), 0.22 * intensity);

    // Cool desaturated emerald-teal shadows & vivid cyan-emerald highlights
    vec3 shadowTeal = vec3(0.06, 0.30, 0.32);
    vec3 highlightCyan = vec3(0.32, 1.00, 0.84);
    float shadowWeight = 1.0 - smoothstep(0.05, 0.62, luma);
    float highlightWeight = smoothstep(0.55, 1.0, luma);

    vec3 graded = mix(
        desat,
        desat * vec3(0.78, 1.07, 1.05) + shadowTeal * (0.28 * shadowWeight),
        0.75 * intensity
    );
    graded += highlightCyan * (highlightWeight * 0.10 * intensity);

    // Subtle micro-shimmer prism accent along shockwave crest
    vec3 waveTint = mix(vec3(0.0, 1.0, 0.62), vec3(0.0, 0.90, 1.0), 0.5 + 0.5 * sin(Time * TWO_PI));
    graded += waveTint * ringRim;

    // 5. Sleek Dark-Teal Optical Vignette
    float vig = smoothstep(0.45, 1.05, distFromCenter);
    vec3 vigTarget = graded * vec3(0.42, 0.64, 0.66) + vec3(0.01, 0.07, 0.08);
    graded = mix(graded, vigTarget, vig * 0.52 * intensity);

    float effectBlend = clamp(max(intensity, ringRim * 3.5 + waveChroma * 40.0), 0.0, 1.0);
    vec3 finalColor = mix(baseOrig, graded, effectBlend);

    fragColor = vec4(clamp(finalColor, 0.0, 1.0), 1.0);
}
